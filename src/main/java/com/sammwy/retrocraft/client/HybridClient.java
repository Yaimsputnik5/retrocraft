package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.*;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.transfer.*;
import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.net.HybridPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Client networking/UI coordinator; native execution, core cache and save formats are shared. */
public final class HybridClient {
    private enum Phase { RECEIVING, PREPARED, ACTIVE, RETURNING, CLOSED }
    private static final class Local {
        final HybridPackets.Lease offer;
        final Map<BlobTransfer.Kind, BlobTransfer.Receiver> incoming = new EnumMap<>(BlobTransfer.Kind.class);
        final ArrayDeque<BlobTransfer.Sender> states = new ArrayDeque<>(), media = new ArrayDeque<>();
        final FrameCodec.Encoder encoder = new FrameCodec.Encoder();
        final AudioPlayer audio = new AudioPlayer();
        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        volatile ClientEmulator emulator;
        volatile Phase phase = Phase.RECEIVING;
        volatile FrameCodec.Frame frame;
        volatile int input, rate;
        volatile float volume = 1;
        Path rom, snapshot;
        boolean starting, forceKey = true;
        boolean relayAudio = true, relayVideo;
        float gain = 1;
        long audioSequence, checkpointSequence, lastHeartbeat, nextVideoKey, nextStream;
        Local(HybridPackets.Lease offer) { this.offer = offer; relayVideo = offer.docked(); }
        synchronized void output(WorkerProcess.Output output, int sampleRate) {
            frame = output.frame(); rate = sampleRate;
            if (pcm.size() + output.audio().length > Math.min(262_144, sampleRate)) pcm.reset();
            pcm.writeBytes(output.audio());
        }
        synchronized byte[] takeAudio() { byte[] bytes = pcm.toByteArray(); pcm.reset(); return bytes; }
        void close() { phase = Phase.CLOSED; if (emulator != null) emulator.close(); audio.close(); states.clear(); media.clear(); incoming.clear(); frame = null; }
    }
    private final AudioStreams remoteAudio;
    private final Map<String, CoreManager.Prepared> prepared = new HashMap<>();
    private final Map<UUID, Local> locals = new HashMap<>();
    private final ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
    private UUID connection = UUID.randomUUID();
    private int settingsGeneration, checkpointSeconds = 2, streamFps = 20;
    private Path workerJar;

    HybridClient(AudioStreams remoteAudio) { this.remoteAudio = remoteAudio; }
    boolean owns(UUID id) { Local local = locals.get(id); return local != null && local.phase == Phase.ACTIVE; }
    FrameCodec.Frame frame(UUID id) { Local local = locals.get(id); return local != null && local.phase == Phase.ACTIVE ? local.frame : null; }

    void settings(HybridPackets.Settings packet) {
        if (!packet.pool().equals(KnownCores.POOL) || packet.checkpointSeconds() < 1 || packet.checkpointSeconds() > 30 || packet.streamFps() < 1 || packet.streamFps() > 20) return;
        settingsGeneration = packet.generation(); checkpointSeconds = packet.checkpointSeconds(); streamFps = packet.streamFps();
        int generation = settingsGeneration; UUID token = connection;
        Map<String, CoreManager.Prepared> previous = Map.copyOf(prepared);
        long started = System.nanoTime();
        ClientPlayNetworking.send(new HybridPackets.Status(generation, HybridPackets.DOWNLOADING, List.of()));
        Thread.ofVirtual().name("retrocraft-client-cores").start(() -> {
            try {
                CacheMigration.prepare(RetrocraftConfig.root(), RetrocraftConfig.cacheRoot(), true);
                Path jar = WorkerRuntime.extract(RetrocraftConfig.clientCache().resolve("runtime"));
                CoreManager manager = new CoreManager(RetrocraftConfig.clientCache().resolve("cores"), jar);
                Map<String, CoreManager.Prepared> ready = new HashMap<>();
                for (CoreIdentity requested : packet.cores()) {
                    KnownCores.get(requested.id()); // no remote URL, path or arbitrary native code identifier
                    var cached = previous.get(requested.id());
                    var core = cached != null && requested.compatible(cached.identity()) && Files.isRegularFile(cached.path())
                            && InstanceStore.sha256(cached.path()).equals(cached.identity().sha256()) ? cached : manager.prepare(requested.id());
                    if (!requested.compatible(core.identity())) throw new IOException("Core build differs from server: " + requested.id());
                    if (ready.put(requested.id(), core) != null) throw new IOException("Duplicate core in settings");
                }
                callbacks.add(() -> {
                    if (!connection.equals(token) || generation != settingsGeneration) return;
                    prepared.clear(); prepared.putAll(ready); workerJar = jar;
                    Retrocraft.LOG.info("Client cores ready in {} ms", (System.nanoTime() - started) / 1_000_000);
                    ClientPlayNetworking.send(new HybridPackets.Status(generation, HybridPackets.READY, ready.values().stream().map(CoreManager.Prepared::identity).toList()));
                });
            } catch (Exception ex) {
                Retrocraft.LOG.error("Client core preparation failed; server emulation remains available", ex);
                callbacks.add(() -> { if (connection.equals(token) && generation == settingsGeneration) ClientPlayNetworking.send(new HybridPackets.Status(generation, HybridPackets.FAILED, List.of())); });
            }
        });
    }

    void lease(HybridPackets.Lease packet) {
        Local local = locals.get(packet.instance());
        if (packet.action() == HybridPackets.Action.OFFER) {
            if (local == null && locals.size() >= 2) { signal(packet, HybridPackets.Action.FAILED, 0); return; }
            if (!prepared.containsKey(packet.core()) || workerJar == null || !packet.gameId().matches("[0-9a-f]{64}") || !RomCache.safeFilename(packet.romName())) { signal(packet, HybridPackets.Action.FAILED, 0); return; }
            if (local != null) { if (local.offer.epoch() >= packet.epoch()) return; local.close(); }
            // The server grants at most one input lease per player.
            for (Local other : List.copyOf(locals.values())) if (!other.offer.instance().equals(packet.instance())) release(other.offer.instance());
            Local offered = new Local(packet); locals.put(packet.instance(), offered);
            UUID token = connection;
            Thread.ofVirtual().name("retrocraft-rom-cache-check").start(() -> {
                try {
                    Path cached = RomCache.find(RetrocraftConfig.clientCache().resolve("roms"), packet.gameId(), packet.romName());
                    callbacks.add(() -> {
                        if (!current(offered, token) || offered.phase != Phase.RECEIVING) return;
                        offered.rom = cached;
                        signal(packet, cached == null ? HybridPackets.Action.ROM_REQUEST : HybridPackets.Action.ROM_READY, 0);
                        startIfReady(offered, token);
                    });
                } catch (Exception ex) { callbacks.add(() -> fail(offered, token, ex)); }
            });
            return;
        }
        if (local == null || local.offer.epoch() != packet.epoch()) return;
        switch (packet.action()) {
            case COMMIT -> {
                if (local.phase != Phase.PREPARED || local.emulator == null) return;
                local.phase = Phase.ACTIVE; remoteAudio.stop(packet.instance()); local.emulator.commit();
            }
            case RETURN_REQUEST -> release(packet.instance());
            case REVOKE -> { local.close(); locals.remove(packet.instance()); }
            case CHECKPOINT_ACK -> { if (local.emulator != null) local.emulator.acknowledgeCheckpoint(); }
            case REQUEST_KEYFRAME -> local.forceKey = true;
            default -> { }
        }
    }

    void blob(HybridPackets.Blob packet) {
        var chunk = packet.chunk(); Local local = locals.get(chunk.instance());
        if (local == null || local.offer.epoch() != chunk.epoch() || local.phase != Phase.RECEIVING
                || chunk.kind() != BlobTransfer.Kind.ROM && chunk.kind() != BlobTransfer.Kind.SNAPSHOT) return;
        try {
            var receiver = local.incoming.computeIfAbsent(chunk.kind(), ignored -> new BlobTransfer.Receiver());
            byte[] bytes = receiver.accept(chunk, System.nanoTime());
            if (bytes == null) return;
            if (chunk.kind() == BlobTransfer.Kind.ROM && !BlobTransfer.hash(bytes).equals(local.offer.gameId())) throw new IOException("ROM hash differs from ownership offer");
            UUID token = connection;
            Thread.ofVirtual().name("retrocraft-client-assets").start(() -> {
                try {
                    Path result;
                    if (chunk.kind() == BlobTransfer.Kind.ROM) result = RomCache.acceptNamed(RetrocraftConfig.clientCache().resolve("roms"), local.offer.gameId(), local.offer.romName(), bytes);
                    else {
                        byte[] state = SnapshotWire.unpack(bytes); SaveStates.validate(state);
                        result = RetrocraftConfig.clientCache().resolve("sessions").resolve(token.toString()).resolve(chunk.instance().toString()).resolve(Long.toString(chunk.epoch())).resolve("snapshot.bin");
                        SaveStates.writeAtomic(result, state);
                    }
                    callbacks.add(() -> {
                        if (!current(local, token)) return;
                        if (chunk.kind() == BlobTransfer.Kind.ROM) { local.rom = result; signal(local.offer, HybridPackets.Action.ROM_READY, 0); } else local.snapshot = result;
                        startIfReady(local, token);
                    });
                } catch (Exception ex) { callbacks.add(() -> fail(local, token, ex)); }
            });
        } catch (IOException ex) { fail(local, connection, ex); }
    }

    void demand(HybridPackets.Demand packet) {
        Local local = locals.get(packet.instance());
        if (local == null || local.offer.epoch() != packet.epoch() || !Float.isFinite(packet.gain()) || packet.gain() < 0 || packet.gain() > 1) return;
        if (packet.video() && !local.relayVideo) local.forceKey = true;
        local.relayAudio = packet.audio(); local.relayVideo = packet.video(); local.gain = packet.gain();
    }

    private void startIfReady(Local local, UUID token) {
        if (local.starting || local.rom == null || local.snapshot == null || local.phase != Phase.RECEIVING) return;
        local.starting = true;
        CoreManager.Prepared core = prepared.get(local.offer.core());
        local.emulator = new ClientEmulator(workerJar, core.path(), local.rom, local.snapshot, checkpointSeconds, () -> local.input, new ClientEmulator.Listener() {
            public void prepared() { callbacks.add(() -> { if (current(local, token) && local.phase == Phase.RECEIVING) { local.phase = Phase.PREPARED; signal(local.offer, HybridPackets.Action.PREPARED, 0); } }); }
            public void output(WorkerProcess.Output output, int rate) { local.output(output, rate); }
            public void checkpoint(byte[] bytes, boolean last) {
                try {
                    byte[] packed = SnapshotWire.pack(bytes);
                    callbacks.add(() -> {
                        if (!current(local, token)) return;
                        local.states.add(new BlobTransfer.Sender(local.offer.instance(), local.offer.epoch(), last ? BlobTransfer.Kind.FINAL_SNAPSHOT : BlobTransfer.Kind.SNAPSHOT, ++local.checkpointSequence, packed));
                    });
                } catch (IOException ex) { failed(ex); }
            }
            public void failed(Throwable failure) { callbacks.add(() -> fail(local, token, failure)); }
        });
    }

    private boolean current(Local local, UUID token) { return connection.equals(token) && locals.get(local.offer.instance()) == local && local.phase != Phase.CLOSED; }
    private void fail(Local local, UUID token, Throwable failure) {
        if (!current(local, token)) return;
        Retrocraft.LOG.error("Client emulation failed; returning to server checkpoint", failure);
        signal(local.offer, HybridPackets.Action.FAILED, 0); local.close(); locals.remove(local.offer.instance());
    }
    void release(UUID instance) {
        Local local = locals.get(instance);
        if (local == null || local.phase == Phase.RETURNING || local.phase == Phase.CLOSED) return;
        if (local.phase != Phase.ACTIVE) { signal(local.offer, HybridPackets.Action.FAILED, 0); local.close(); locals.remove(instance); return; }
        local.phase = Phase.RETURNING; local.input = 0; local.media.clear(); local.audio.close();
        signal(local.offer, HybridPackets.Action.RETURN_BEGIN, 0); local.emulator.returnToServer();
    }
    private static void signal(HybridPackets.Lease offer, HybridPackets.Action action, long sequence) {
        if (ClientPlayNetworking.canSend(HybridPackets.Lease.ID)) ClientPlayNetworking.send(HybridPackets.Lease.signal(offer.instance(), offer.epoch(), action, sequence));
    }

    void tick(MinecraftClient client) {
        if (!ClientPlayNetworking.canSend(HybridPackets.Lease.ID)) { if (!locals.isEmpty()) disconnect(); return; }
        Runnable callback;
        for (int i = 0; i < 256 && (callback = callbacks.poll()) != null; i++) callback.run();
        long now = System.nanoTime();
        for (Local local : List.copyOf(locals.values())) {
            if (local.phase == Phase.ACTIVE || local.phase == Phase.PREPARED || local.phase == Phase.RECEIVING) {
                boolean visible = client.currentScreen instanceof ConsoleScreen screen && screen.instance.equals(local.offer.instance());
                if (!visible) { release(local.offer.instance()); continue; }
            }
            local.input = local.phase == Phase.ACTIVE ? InputControls.buttons(client) : 0;
            local.volume = client.options.getSoundVolume(SoundCategory.MASTER) * client.options.getSoundVolume(SoundCategory.RECORDS);
            if (local.phase == Phase.ACTIVE) {
                if (now - local.lastHeartbeat > 500_000_000L) { signal(local.offer, HybridPackets.Action.HEARTBEAT, 0); local.lastHeartbeat = now; }
                if (now >= local.nextStream) {
                    local.nextStream = now + 1_000_000_000L / streamFps;
                    byte[] pcm = local.takeAudio();
                    if (pcm.length > 0) {
                        if (local.gain > 0) local.audio.offer(local.rate, pcm, local.volume * local.gain);
                        if (local.relayAudio && local.media.size() < 2 && local.media.stream().noneMatch(sender -> sender.kind() == BlobTransfer.Kind.AUDIO)) {
                            try { local.media.add(new BlobTransfer.Sender(local.offer.instance(), local.offer.epoch(), BlobTransfer.Kind.AUDIO, ++local.audioSequence, MediaWire.audio(local.rate, pcm))); }
                            catch (IOException ex) { fail(local, connection, ex); }
                        }
                    }
                    if (local.relayVideo && local.frame != null && local.media.size() < 2 && local.media.stream().noneMatch(sender -> sender.kind() == BlobTransfer.Kind.VIDEO)) {
                        try {
                            boolean key = local.forceKey || now >= local.nextVideoKey;
                            var frame = local.encoder.encode(local.frame, key); local.forceKey = false;
                            if (key) local.nextVideoKey = now + 3_000_000_000L;
                            local.media.add(new BlobTransfer.Sender(local.offer.instance(), local.offer.epoch(), BlobTransfer.Kind.VIDEO, frame.sequence(), MediaWire.video(frame)));
                        } catch (IOException ex) { fail(local, connection, ex); }
                    }
                }
            }
            // Interleave checkpoint and media chunks. A large save must not starve audio.
            for (int i = 0; i < 4; i++) {
                ArrayDeque<BlobTransfer.Sender> queue = !local.states.isEmpty() && (i % 2 == 0 || local.media.isEmpty()) ? local.states : local.media;
                if (queue.isEmpty()) continue;
                var sender = queue.getFirst(); ClientPlayNetworking.send(new HybridPackets.Blob(sender.next()));
                if (sender.done()) queue.removeFirst();
            }
        }
    }

    void disconnect() {
        connection = UUID.randomUUID(); prepared.clear();
        for (Local local : locals.values()) local.close();
        locals.clear(); callbacks.clear(); workerJar = null;
    }
}
