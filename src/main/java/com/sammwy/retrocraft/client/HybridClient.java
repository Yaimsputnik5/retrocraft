package com.sammwy.retrocraft.client;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.*;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.stream.ThumbnailCodec;
import com.sammwy.retrocraft.cores.transfer.*;
import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.net.HybridPackets;
import com.sammwy.retrocraft.server.net.Packets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

/** Client networking/UI coordinator; native execution, core cache and save formats are shared. */
public final class HybridClient {
    private enum Phase {
        RECEIVING,
        PREPARED,
        ACTIVE,
        RETURNING,
        CLOSED
    }

    private static final class Local {
        final HybridPackets.Lease offer;
        final Map<BlobTransfer.Kind, BlobTransfer.Receiver> incoming = new ConcurrentHashMap<>();
        final ArrayDeque<BlobTransfer.Chunk> pending = new ArrayDeque<>();
        boolean receiving;
        final ArrayDeque<BlobTransfer.Sender> states = new ArrayDeque<>(),
                media = new ArrayDeque<>();
        final FrameCodec.Encoder encoder = new FrameCodec.Encoder();
        boolean encodingVideo;
        final AudioPlayer audio = new AudioPlayer();
        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        volatile ClientEmulator emulator;
        volatile Phase phase = Phase.RECEIVING;
        volatile FrameCodec.Frame frame;

        /**
         * Per-port RetroPad masks; this client owns its roster slot, the rest come from Pad
         * broadcasts.
         */
        final AtomicIntegerArray ports = new AtomicIntegerArray(4);

        volatile int rate;
        Downloads.Transfer romDownload;
        volatile float volume = 1;
        Path rom, snapshot;
        boolean starting, forceKey = true;
        boolean relayAudio = true, relayVideo;
        volatile float gain = 1;
        long audioSequence, checkpointSequence, lastHeartbeat, nextVideoKey, nextStream;

        Local(HybridPackets.Lease offer) {
            this.offer = offer;
            relayVideo = offer.docked();
        }

        synchronized void output(WorkerProcess.Output output, int sampleRate) {
            if (output.frame() != null) frame = output.frame();
            rate = sampleRate;
            audio.offer(sampleRate, output.audio(), volume * gain);
            if (pcm.size() + output.audio().length > Math.min(262_144, sampleRate)) pcm.reset();
            pcm.writeBytes(output.audio());
        }

        synchronized byte[] takeAudio() {
            byte[] bytes = pcm.toByteArray();
            pcm.reset();
            return bytes;
        }

        void close() {
            phase = Phase.CLOSED;
            if (romDownload != null) romDownload.cancel();
            if (emulator != null) emulator.close();
            audio.close();
            states.clear();
            media.clear();
            incoming.clear();
            synchronized (this) {
                pending.clear();
            }
            frame = null;
        }
    }

    private final AudioStreams remoteAudio;
    private final Map<String, CoreManager.Prepared> prepared = new HashMap<>();
    private final Map<UUID, Local> locals = new HashMap<>();
    private final ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
    private UUID connection = UUID.randomUUID();
    private int settingsGeneration,
            checkpointSeconds = 2,
            streamFps = 20,
            thumbnailSeconds = 5,
            emulationMode;
    private boolean uploadThumbnails = true;
    private long nextThumbAt;
    private Path workerJar;

    HybridClient(AudioStreams remoteAudio) {
        this.remoteAudio = remoteAudio;
    }

    boolean owns(UUID id) {
        Local local = locals.get(id);
        return local != null && local.phase == Phase.ACTIVE;
    }

    List<UUID> active() {
        List<UUID> ids = new ArrayList<>();
        for (var e : locals.entrySet()) if (e.getValue().phase == Phase.ACTIVE) ids.add(e.getKey());
        return ids;
    }

    FrameCodec.Frame frame(UUID id) {
        Local local = locals.get(id);
        return local != null && local.phase == Phase.ACTIVE ? local.frame : null;
    }

    /** Sample local buttons at render cadence so local play does not wait for a 20 Hz game tick. */
    void pollInput(MinecraftClient client) {
        for (Local local : locals.values()) {
            if (local.phase != Phase.ACTIVE) continue;
            int mine =
                    client.player == null
                            ? -1
                            : PlayerHud.slotOf(local.offer.instance(), client.player.getUuid());
            boolean visible =
                    client.currentScreen instanceof ConsoleScreen screen
                            && screen.instance.equals(local.offer.instance());
            int buttons = visible ? InputControls.buttons(client) : 0;
            for (int port = 0; port < local.ports.length(); port++)
                local.ports.set(
                        port,
                        port == mine ? buttons : PlayerHud.mask(local.offer.instance(), port));
        }
    }

    int thumbnailSeconds() {
        return thumbnailSeconds;
    }

    void settings(HybridPackets.Settings packet) {
        if (!packet.pool().equals(KnownCores.POOL)
                || packet.checkpointSeconds() < 1
                || packet.checkpointSeconds() > 30
                || packet.streamFps() < 1
                || packet.streamFps() > 144
                || packet.thumbnailSeconds() < 1
                || packet.thumbnailSeconds() > 600) return;
        thumbnailSeconds = packet.thumbnailSeconds();
        uploadThumbnails = packet.thumbnailUpload();
        emulationMode = packet.emulationMode();
        checkpointSeconds = packet.checkpointSeconds();
        for (Local local : locals.values())
            if (local.emulator != null) local.emulator.checkpointInterval(checkpointSeconds);
        streamFps = Math.min(20, packet.streamFps());
        if (packet.generation() == settingsGeneration) {
            // Re-acknowledge soft updates: the server may have invalidated readiness without
            // changing cores.
            if (workerJar != null)
                ClientPlayNetworking.send(
                        new HybridPackets.Status(
                                settingsGeneration,
                                HybridPackets.READY,
                                prepared.values().stream()
                                        .map(CoreManager.Prepared::identity)
                                        .toList()));
            return;
        }
        settingsGeneration = packet.generation();
        int generation = settingsGeneration;
        UUID token = connection;
        Downloads downloads = DownloadHud.DOWNLOADS;
        Map<String, CoreManager.Prepared> previous = Map.copyOf(prepared);
        long started = System.nanoTime();
        ClientPlayNetworking.send(
                new HybridPackets.Status(generation, HybridPackets.DOWNLOADING, List.of()));
        Thread.ofVirtual()
                .name("retrocraft-client-cores")
                .start(
                        () -> {
                            try {
                                CacheMigration.prepare(
                                        RetrocraftConfig.root(),
                                        RetrocraftConfig.cacheRoot(),
                                        true);
                                Path jar = WorkerRuntime.extract(RetrocraftConfig.runtimeCache());
                                CoreManager manager =
                                        new CoreManager(RetrocraftConfig.coreCache(), jar);
                                Map<String, CoreManager.Prepared> ready = new ConcurrentHashMap<>();
                                try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
                                    for (CoreIdentity requested : packet.cores())
                                        tasks.submit(
                                                () -> {
                                                    String id = requested.id();
                                                    var cached = previous.get(id);
                                                    try {
                                                        ready.put(
                                                                id,
                                                                cached != null
                                                                                && Files
                                                                                        .isRegularFile(
                                                                                                cached
                                                                                                        .path())
                                                                        ? cached
                                                                        : manager.prepare(
                                                                                id,
                                                                                DownloadHud
                                                                                        .DOWNLOADS));
                                                    } catch (Exception ex) {
                                                        Retrocraft.LOG.warn(
                                                                "Client could not prepare core {}",
                                                                id,
                                                                ex);
                                                    }
                                                });
                                }
                                for (CoreIdentity requested : packet.cores()) {
                                    var core = ready.get(requested.id());
                                    if (core == null || !requested.compatible(core.identity()))
                                        throw new IOException(
                                                "Core build differs from server: "
                                                        + requested.id());
                                }
                                callbacks.add(
                                        () -> {
                                            if (!connection.equals(token)
                                                    || generation != settingsGeneration) return;
                                            prepared.clear();
                                            prepared.putAll(ready);
                                            workerJar = jar;
                                            Retrocraft.LOG.info(
                                                    "Client cores ready in {} ms",
                                                    (System.nanoTime() - started) / 1_000_000);
                                            ClientPlayNetworking.send(
                                                    new HybridPackets.Status(
                                                            generation,
                                                            HybridPackets.READY,
                                                            ready.values().stream()
                                                                    .map(
                                                                            CoreManager.Prepared
                                                                                    ::identity)
                                                                    .toList()));
                                        });
                            } catch (Exception ex) {
                                Retrocraft.LOG.error(
                                        "Client core preparation failed; server emulation remains available",
                                        ex);
                                callbacks.add(
                                        () -> {
                                            if (connection.equals(token)
                                                    && generation == settingsGeneration)
                                                ClientPlayNetworking.send(
                                                        new HybridPackets.Status(
                                                                generation,
                                                                HybridPackets.FAILED,
                                                                List.of()));
                                        });
                            }
                        });
    }

    void lease(HybridPackets.Lease packet) {
        Local local = locals.get(packet.instance());
        if (packet.action() == HybridPackets.Action.OFFER) {
            if (local == null && locals.size() >= 2) {
                signal(packet, HybridPackets.Action.FAILED, 0);
                return;
            }
            if (!prepared.containsKey(packet.core())
                    || workerJar == null
                    || !packet.gameId().matches("[0-9a-f]{64}")
                    || !RomCache.safeFilename(packet.romName())) {
                signal(packet, HybridPackets.Action.FAILED, 0);
                return;
            }
            if (local != null) {
                if (local.offer.epoch() >= packet.epoch()) return;
                local.close();
            }
            // The server grants at most one input lease per player.
            for (Local other : List.copyOf(locals.values()))
                if (!other.offer.instance().equals(packet.instance()))
                    release(other.offer.instance());
            Local offered = new Local(packet);
            locals.put(packet.instance(), offered);
            UUID token = connection;
            Thread.ofVirtual()
                    .name("retrocraft-rom-cache-check")
                    .start(
                            () -> {
                                try {
                                    Path cached =
                                            RomCache.find(
                                                    RetrocraftConfig.clientCache().resolve("roms"),
                                                    packet.gameId(),
                                                    packet.romName());
                                    callbacks.add(
                                            () -> {
                                                if (!current(offered, token)
                                                        || offered.phase != Phase.RECEIVING) return;
                                                offered.rom = cached;
                                                if (cached == null)
                                                    offered.romDownload =
                                                            DownloadHud.DOWNLOADS.begin(
                                                                    "ROM", packet.romName(), -1);
                                                signal(
                                                        packet,
                                                        cached == null
                                                                ? HybridPackets.Action.ROM_REQUEST
                                                                : HybridPackets.Action.ROM_READY,
                                                        0);
                                                startIfReady(offered, token);
                                            });
                                } catch (Exception ex) {
                                    callbacks.add(() -> fail(offered, token, ex));
                                }
                            });
            return;
        }
        if (local == null || local.offer.epoch() != packet.epoch()) return;
        switch (packet.action()) {
            case COMMIT -> {
                if (local.phase != Phase.PREPARED || local.emulator == null) return;
                local.phase = Phase.ACTIVE;
                remoteAudio.stop(packet.instance());
                local.emulator.commit();
            }
            case RETURN_REQUEST -> release(packet.instance());
            case REVOKE -> {
                local.close();
                locals.remove(packet.instance());
            }
            case CHECKPOINT_ACK -> {
                if (local.emulator != null) local.emulator.acknowledgeCheckpoint();
            }
            case REQUEST_KEYFRAME -> local.forceKey = true;
            default -> {}
        }
    }

    void blob(HybridPackets.Blob packet) {
        var chunk = packet.chunk();
        Local local = locals.get(chunk.instance());
        if (local == null
                || local.offer.epoch() != chunk.epoch()
                || local.phase != Phase.RECEIVING
                || chunk.kind() != BlobTransfer.Kind.ROM
                        && chunk.kind() != BlobTransfer.Kind.SNAPSHOT) return;
        UUID token = connection;
        synchronized (local) {
            if (local.pending.size() >= 128) {
                fail(local, token, new IOException("Client asset queue overflow"));
                return;
            }
            local.pending.addLast(chunk);
            if (local.receiving) return;
            local.receiving = true;
        }
        Thread.ofVirtual()
                .name("retrocraft-client-asset-decode")
                .start(
                        () -> {
                            while (true) {
                                BlobTransfer.Chunk next;
                                synchronized (local) {
                                    next = local.pending.pollFirst();
                                    if (next == null) {
                                        local.receiving = false;
                                        return;
                                    }
                                }
                                if (local.phase == Phase.CLOSED) return;
                                try {
                                    var receiver =
                                            local.incoming.computeIfAbsent(
                                                    next.kind(),
                                                    ignored -> new BlobTransfer.Receiver());
                                    byte[] bytes = receiver.accept(next, System.nanoTime());
                                    if (next.kind() == BlobTransfer.Kind.ROM
                                            && local.romDownload != null)
                                        local.romDownload.update(
                                                (long) next.offset() + next.bytes().length,
                                                next.total());
                                    if (bytes == null) continue;
                                    if (next.kind() == BlobTransfer.Kind.ROM
                                            && !BlobTransfer.hash(bytes)
                                                    .equals(local.offer.gameId()))
                                        throw new IOException(
                                                "ROM hash differs from ownership offer");
                                    writeAsset(local, token, next, bytes);
                                } catch (IOException ex) {
                                    synchronized (local) {
                                        local.pending.clear();
                                        local.receiving = false;
                                    }
                                    callbacks.add(() -> fail(local, token, ex));
                                    return;
                                }
                            }
                        });
    }

    private void writeAsset(Local local, UUID token, BlobTransfer.Chunk chunk, byte[] bytes) {
        Thread.ofVirtual()
                .name("retrocraft-client-assets")
                .start(
                        () -> {
                            try {
                                Path result;
                                if (chunk.kind() == BlobTransfer.Kind.ROM)
                                    result =
                                            RomCache.acceptNamed(
                                                    RetrocraftConfig.clientCache().resolve("roms"),
                                                    local.offer.gameId(),
                                                    local.offer.romName(),
                                                    bytes);
                                else {
                                    byte[] state = SnapshotWire.unpack(bytes);
                                    SaveStates.validate(state);
                                    result =
                                            RetrocraftConfig.clientCache()
                                                    .resolve("sessions")
                                                    .resolve(token.toString())
                                                    .resolve(chunk.instance().toString())
                                                    .resolve(Long.toString(chunk.epoch()))
                                                    .resolve("snapshot.bin");
                                    SaveStates.writeAtomic(result, state);
                                }
                                callbacks.add(
                                        () -> {
                                            if (!current(local, token)) return;
                                            if (chunk.kind() == BlobTransfer.Kind.ROM) {
                                                local.rom = result;
                                                if (local.romDownload != null) {
                                                    local.romDownload.complete();
                                                    local.romDownload = null;
                                                }
                                                signal(
                                                        local.offer,
                                                        HybridPackets.Action.ROM_READY,
                                                        0);
                                            } else local.snapshot = result;
                                            startIfReady(local, token);
                                        });
                            } catch (Exception ex) {
                                callbacks.add(() -> fail(local, token, ex));
                            }
                        });
    }

    void demand(HybridPackets.Demand packet) {
        Local local = locals.get(packet.instance());
        if (local == null
                || local.offer.epoch() != packet.epoch()
                || !Float.isFinite(packet.gain())
                || packet.gain() < 0
                || packet.gain() > 1) return;
        if (packet.video() && !local.relayVideo) local.forceKey = true;
        local.relayAudio = packet.audio();
        local.relayVideo = packet.video();
        local.gain = packet.gain();
    }

    private void startIfReady(Local local, UUID token) {
        if (local.starting
                || local.rom == null
                || local.snapshot == null
                || local.phase != Phase.RECEIVING) return;
        local.starting = true;
        CoreManager.Prepared core = prepared.get(local.offer.core());
        local.emulator =
                new ClientEmulator(
                        workerJar,
                        core.path(),
                        local.rom,
                        local.snapshot,
                        RetrocraftConfig.systemDir(local.offer.core()),
                        checkpointSeconds,
                        local.ports::get,
                        new ClientEmulator.Listener() {
                            public void prepared() {
                                callbacks.add(
                                        () -> {
                                            if (current(local, token)
                                                    && local.phase == Phase.RECEIVING) {
                                                local.phase = Phase.PREPARED;
                                                signal(
                                                        local.offer,
                                                        HybridPackets.Action.PREPARED,
                                                        0);
                                            }
                                        });
                            }

                            public void output(WorkerProcess.Output output, int rate) {
                                local.output(output, rate);
                            }

                            public void checkpoint(byte[] bytes, boolean last) {
                                try {
                                    byte[] packed = SnapshotWire.pack(bytes);
                                    var sender =
                                            new BlobTransfer.Sender(
                                                    local.offer.instance(),
                                                    local.offer.epoch(),
                                                    last
                                                            ? BlobTransfer.Kind.FINAL_SNAPSHOT
                                                            : BlobTransfer.Kind.SNAPSHOT,
                                                    ++local.checkpointSequence,
                                                    packed);
                                    callbacks.add(
                                            () -> {
                                                if (!current(local, token)) return;
                                                local.states.add(sender);
                                            });
                                } catch (IOException ex) {
                                    failed(ex);
                                }
                            }

                            public void failed(Throwable failure) {
                                callbacks.add(() -> fail(local, token, failure));
                            }
                        });
    }

    private boolean current(Local local, UUID token) {
        return connection.equals(token)
                && locals.get(local.offer.instance()) == local
                && local.phase != Phase.CLOSED;
    }

    private void fail(Local local, UUID token, Throwable failure) {
        if (!current(local, token)) return;
        Retrocraft.LOG.error("Client emulation failed; returning to server checkpoint", failure);
        if (local.romDownload != null) {
            local.romDownload.fail();
            local.romDownload = null;
        }
        signal(local.offer, HybridPackets.Action.FAILED, 0);
        local.close();
        locals.remove(local.offer.instance());
    }

    void release(UUID instance) {
        Local local = locals.get(instance);
        if (local == null || local.phase == Phase.RETURNING || local.phase == Phase.CLOSED) return;
        if (local.phase != Phase.ACTIVE) {
            signal(local.offer, HybridPackets.Action.FAILED, 0);
            local.close();
            locals.remove(instance);
            return;
        }
        local.phase = Phase.RETURNING;
        for (int p = 0; p < local.ports.length(); p++) local.ports.set(p, 0);
        local.media.clear();
        local.audio.close();
        signal(local.offer, HybridPackets.Action.RETURN_BEGIN, 0);
        local.emulator.returnToServer();
    }

    private static void signal(
            HybridPackets.Lease offer, HybridPackets.Action action, long sequence) {
        if (ClientPlayNetworking.canSend(HybridPackets.Lease.ID))
            ClientPlayNetworking.send(
                    HybridPackets.Lease.signal(offer.instance(), offer.epoch(), action, sequence));
    }

    void tick(MinecraftClient client) {
        if (!ClientPlayNetworking.canSend(HybridPackets.Lease.ID)) {
            if (!locals.isEmpty()) disconnect();
            return;
        }
        Runnable callback;
        for (int i = 0; i < 256 && (callback = callbacks.poll()) != null; i++) callback.run();
        long now = System.nanoTime();
        if (uploadThumbnails && now >= nextThumbAt) {
            nextThumbAt = now + TimeUnit.SECONDS.toNanos(thumbnailSeconds);
            for (Local local : locals.values())
                if (local.phase == Phase.ACTIVE && !local.offer.docked() && local.frame != null)
                    uploadThumbnail(local);
        }
        for (Local local : List.copyOf(locals.values())) {
            if (local.phase == Phase.ACTIVE
                    || local.phase == Phase.PREPARED
                    || local.phase == Phase.RECEIVING) {
                boolean visible =
                        client.currentScreen instanceof ConsoleScreen screen
                                && screen.instance.equals(local.offer.instance());
                if (!visible) {
                    release(local.offer.instance());
                    continue;
                }
            }
            int mine =
                    client.player != null
                            ? PlayerHud.slotOf(local.offer.instance(), client.player.getUuid())
                            : -1;
            for (int port = 0; port < local.ports.length(); port++)
                local.ports.set(
                        port,
                        port == mine
                                ? (local.phase == Phase.ACTIVE ? InputControls.buttons(client) : 0)
                                : PlayerHud.mask(local.offer.instance(), port));
            local.volume =
                    ClientPreferences.get().volume
                            / 100f
                            * client.options.getSoundVolume(SoundCategory.MASTER)
                            * client.options.getSoundVolume(SoundCategory.RECORDS);
            if (local.phase == Phase.ACTIVE) {
                if (now - local.lastHeartbeat > 500_000_000L) {
                    signal(local.offer, HybridPackets.Action.HEARTBEAT, 0);
                    local.lastHeartbeat = now;
                }
                {
                    byte[] pcm = local.takeAudio();
                    if (pcm.length > 0) {
                        if (local.relayAudio
                                && local.media.size() < 2
                                && local.media.stream()
                                        .noneMatch(
                                                sender ->
                                                        sender.kind() == BlobTransfer.Kind.AUDIO)) {
                            try {
                                local.media.add(
                                        new BlobTransfer.Sender(
                                                local.offer.instance(),
                                                local.offer.epoch(),
                                                BlobTransfer.Kind.AUDIO,
                                                ++local.audioSequence,
                                                MediaWire.audio(local.rate, pcm)));
                            } catch (IOException ex) {
                                fail(local, connection, ex);
                            }
                        }
                    }
                    if (now >= local.nextStream
                            && local.relayVideo
                            && local.frame != null
                            && !local.encodingVideo
                            && local.media.size() < 2
                            && local.media.stream()
                                    .noneMatch(
                                            sender -> sender.kind() == BlobTransfer.Kind.VIDEO)) {
                        local.nextStream = now + 1_000_000_000L / streamFps;
                        local.encodingVideo = true;
                        boolean key = local.forceKey || now >= local.nextVideoKey;
                        local.forceKey = false;
                        FrameCodec.Frame picture = local.frame;
                        UUID token = connection;
                        Thread.ofVirtual()
                                .name("retrocraft-client-video-encode")
                                .start(
                                        () -> {
                                            try {
                                                var frame = local.encoder.encode(picture, key);
                                                var sender =
                                                        new BlobTransfer.Sender(
                                                                local.offer.instance(),
                                                                local.offer.epoch(),
                                                                BlobTransfer.Kind.VIDEO,
                                                                frame.sequence(),
                                                                MediaWire.video(frame));
                                                callbacks.add(
                                                        () -> {
                                                            local.encodingVideo = false;
                                                            if (!current(local, token)
                                                                    || local.phase != Phase.ACTIVE)
                                                                return;
                                                            if (local.media.size() >= 2
                                                                    || local.media.stream()
                                                                            .anyMatch(
                                                                                    item ->
                                                                                            item
                                                                                                            .kind()
                                                                                                    == BlobTransfer
                                                                                                            .Kind
                                                                                                            .VIDEO)) {
                                                                local.forceKey = true;
                                                                return;
                                                            }
                                                            if (key)
                                                                local.nextVideoKey =
                                                                        System.nanoTime()
                                                                                + 3_000_000_000L;
                                                            local.media.add(sender);
                                                        });
                                            } catch (IOException ex) {
                                                callbacks.add(
                                                        () -> {
                                                            local.encodingVideo = false;
                                                            fail(local, token, ex);
                                                        });
                                            }
                                        });
                    }
                }
            }
            // Interleave checkpoint and media chunks. A large save must not starve audio.
            for (int i = 0; i < 4; i++) {
                ArrayDeque<BlobTransfer.Sender> queue =
                        !local.states.isEmpty() && (i % 2 == 0 || local.media.isEmpty())
                                ? local.states
                                : local.media;
                if (queue.isEmpty()) continue;
                var sender = queue.getFirst();
                ClientPlayNetworking.send(new HybridPackets.Blob(sender.next()));
                if (sender.done()) queue.removeFirst();
            }
        }
    }

    /** Pushes the client's own handheld preview so nearby players see it too. */
    private void uploadThumbnail(Local local) {
        if (!ClientPlayNetworking.canSend(Packets.ThumbnailUpload.ID)) return;
        FrameCodec.Frame frame = local.frame;
        UUID instance = local.offer.instance();
        Thread.ofVirtual()
                .name("retrocraft-thumb-upload")
                .start(
                        () -> {
                            try {
                                byte[] png = ThumbnailCodec.encode(frame);
                                callbacks.add(
                                        () -> {
                                            if (local.phase == Phase.ACTIVE
                                                    && ClientPlayNetworking.canSend(
                                                            Packets.ThumbnailUpload.ID))
                                                ClientPlayNetworking.send(
                                                        new Packets.ThumbnailUpload(instance, png));
                                        });
                            } catch (IOException ignored) {
                            }
                        });
    }

    void disconnect() {
        connection = UUID.randomUUID();
        prepared.clear();
        settingsGeneration = 0;
        for (Local local : locals.values()) local.close();
        locals.clear();
        callbacks.clear();
        workerJar = null;
    }
}
