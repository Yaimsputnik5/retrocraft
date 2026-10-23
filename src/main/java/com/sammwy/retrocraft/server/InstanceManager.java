package com.sammwy.retrocraft.server;

import com.google.gson.Gson;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.item.*;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.screen.*;
import com.sammwy.retrocraft.cores.stream.*;
import com.sammwy.retrocraft.cores.*;
import com.sammwy.retrocraft.cores.ownership.Ownership;
import com.sammwy.retrocraft.cores.transfer.*;
import com.sammwy.retrocraft.server.net.HybridPackets;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import com.sammwy.retrocraft.roms.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** Server-owned emulation. Video, audio and input are separate subscriptions. */
public final class InstanceManager implements AutoCloseable {
    private final MinecraftServer server;
    private volatile RetrocraftConfig config;
    private RomIndex roms;
    private boolean scanning;
    private final InstanceStore store;
    private final GameSaves saves;
    private Path workerJar;
    private CoreManager cores;
    private final Map<String, CoreManager.Prepared> preparedCores = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clientGenerations = new HashMap<>();
    private final Set<UUID> readyClients = new HashSet<>();
    private final Set<UUID> verifiedClients = new HashSet<>(), biosReadyClients = new HashSet<>();
    private final Map<UUID, ArrayDeque<BlobTransfer.Sender>> outbound = new HashMap<>();
    private final Set<UUID> biosManifestSent = new HashSet<>();
    private final Map<UUID, Set<String>> biosPending = new HashMap<>();
    private final Map<UUID, ArrayDeque<BiosOutgoing>> biosOutgoing = new HashMap<>();
    private static final class BiosOutgoing {
        final String core, name, sha;
        final byte[] bytes;
        int offset;
        BiosOutgoing(String core, String name, String sha, byte[] bytes) {
            this.core = core; this.name = name; this.sha = sha; this.bytes = bytes;
        }
    }
    private Set<String> requiredCores = Set.of();
    private final Set<String> preparingCores = new HashSet<>();
    private List<RomCatalog.Entry> publishedCatalog = List.of();
    private final RomLibrary romLibrary, biosLibrary;
    private final Map<UUID, Upload> romUploads = new HashMap<>();
    private boolean pendingScan, pendingOnlyNew;
    private int coreGeneration;
    private final ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private static final class PendingStart { volatile UUID created; }
    private final Map<Object, PendingStart> pendingStarts = new IdentityHashMap<>();
    private final Map<UUID, UUID> videoWatching = new HashMap<>(), inputSessions = new HashMap<>();
    private final Set<UUID> failedDiscovery = new HashSet<>();
    private int ticks, catalogGeneration;
    private volatile boolean stopping;

    private static final class VideoWatcher {
        long lastSeen = System.nanoTime(), lastSent, lastAcknowledged, lastResync;
        boolean needsKeyframe = true;
        /** Attached by proximity to an in-world screen (television / arcade cabinet) instead of by opening the play screen. */
        boolean ambient;
    }
    private static final class AudioWatcher {
        float gain;
        long lastSent, lastAcknowledged;
        AudioWatcher(float gain) { this.gain = gain; }
    }
    private static final class InputSource {
        final UUID player;
        volatile int buttons;
        volatile long lastSeen = System.nanoTime();
        InputSource(UUID player) { this.player = player; }
    }
    private static final class Media {
        private FrameCodec.Frame frame;
        private ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private boolean updated;
        synchronized void offer(WorkerProcess.Output output, int rate) {
            if (output.frame() != null) frame = output.frame();
            updated = true;
            int limit = Math.min(WorkerProtocol.MAX_AUDIO, rate); // at most 250ms stereo PCM
            if (audio.size() + output.audio().length > limit) audio.reset();
            if (output.audio().length <= limit) audio.writeBytes(output.audio());
        }
        synchronized WorkerProcess.Output drain() {
            if (!updated) return null;
            var result = new WorkerProcess.Output(frame, audio.toByteArray());
            frame = null; audio.reset(); updated = false;
            return result;
        }
        synchronized void clear() { frame = null; audio = new ByteArrayOutputStream(); updated = false; }
    }
    private static final class Session {
        final InstanceStore.Metadata data;
        volatile RomIndex.Game game;
        Boolean clientFit;
        boolean checkingFit;
        ConsoleHost host;
        final Map<UUID, VideoWatcher> videoWatchers = new HashMap<>();
        final Map<UUID, AudioWatcher> audioWatchers = new HashMap<>();
        final Media media = new Media();
        FrameCodec.Encoder encoder = new FrameCodec.Encoder();
        boolean encodingVideo;
        int videoGeneration;
        /** Controller slots; only the first one is usable on handhelds. Slot 0 also holds the emulation lease. */
        final InputSource[] pads = new InputSource[WorkerProtocol.PORTS];
        volatile boolean wantsVideo, wantsAudio, observed, finished, destroy;
        long invalidSince;
        boolean poweredOff;
        final AtomicBoolean cleanupStarted = new AtomicBoolean();
        volatile FrameCodec.Frame lastFrame;
        volatile boolean thumbDue;
        volatile FrameCodec.Frame lastThumbSource;
        volatile byte[] lastThumb;
        volatile long lastUploadAt;
        volatile WorkerProcess process;
        volatile Throwable failure;
        volatile int sampleRate;
        final Ownership ownership = new Ownership();
        volatile Map<BlobTransfer.Kind, BlobTransfer.Receiver> incoming = new ConcurrentHashMap<>();
        final ArrayDeque<UploadChunk> uploadQueue = new ArrayDeque<>();
        boolean decodingUpload;
        volatile long lastIncomingChunk;
        final Set<String> pendingCheckpointWrites = new HashSet<>();
        final AtomicInteger checkpointWrites = new AtomicInteger();
        FrameCodec.Decoder relayDecoder = new FrameCodec.Decoder();
        volatile boolean handoffStop;
        boolean offerSent, romRequested, romReady;
        long handoffStarted, romBytesSent;
        UUID failedOffload;
        long lastClientVideo, lastClientAudio, lastCheckpointAt;
        long checkpointBytes;
        long demandEpoch = -1;
        boolean relayAudio, relayVideo;
        float ownerGain = -1;
        Thread thread;
        boolean forceKeyframe = true;
        long nextStream, nextKeyframe;
        long audioSequence;
        Session(InstanceStore.Metadata data, RomIndex.Game game, ConsoleHost host) { this.data = data; this.game = game; this.host = host; }
    }
    private record UploadChunk(UUID player, BlobTransfer.Chunk chunk) {}

    public InstanceManager(MinecraftServer server, RetrocraftConfig config) throws IOException {
        this(server, config, server.getSavePath(WorldSavePath.ROOT));
    }

    public InstanceManager(MinecraftServer server, RetrocraftConfig config, Path worldRoot) throws IOException {
        this.server = server; this.config = config;
        store = new InstanceStore(worldRoot.resolve("retrocraft/instances"));
        saves = new GameSaves(RetrocraftConfig.savesRoot());
        romLibrary = new RomLibrary(RetrocraftConfig.root().resolve("roms"));
        biosLibrary = new RomLibrary(RetrocraftConfig.root().resolve("bios"));
        scanning = true;
        Thread.ofVirtual().name("retrocraft-server-init").start(() -> {
            try {
                CacheMigration.prepare(RetrocraftConfig.root(), RetrocraftConfig.cacheRoot(), false);
                Path jar = WorkerRuntime.extract(RetrocraftConfig.runtimeCache());
                RomIndex catalog = loadLibrary(false, false);
                callbacks.add(() -> {
                    scanning = false;
                    workerJar = jar; cores = new CoreManager(RetrocraftConfig.coreCache(), jar);
                    roms = catalog; libraryUpdated();
                    Retrocraft.LOG.info("Retrocraft ready: {} consoles, {} ROMs", ConsoleRegistry.consoles().size(), roms.catalog().size());
                    if (pendingScan) { boolean onlyNew = pendingOnlyNew; pendingScan = false; rescan(null, onlyNew); }
                });
            } catch (Exception ex) {
                Retrocraft.LOG.error("Retrocraft server initialization failed", ex);
                callbacks.add(() -> scanning = false);
            }
        });
    }

    private RomIndex loadLibrary(boolean force, boolean onlyNew) throws IOException {
        var previous = onlyNew && roms != null ? roms.games().stream()
                .map(g -> new RomScanner.Game(g.cartridge().id().toString(), g.id(), g.name(), g.filename(), g.source())).toList() : null;
        return RomIndex.load(RetrocraftConfig.root().resolve("roms"), RetrocraftConfig.serverCache(), config.maxRomSizeMiB * 1024L * 1024, force, onlyNew, previous,
                warning -> Retrocraft.LOG.warn("ROM scan: {}", warning));
    }
    public RetrocraftConfig config() { return config; }

    /** Applies a config object as-is (used by /retrocraft config set after a successful save). */
    public void applyConfig() {
        if (roms != null) libraryUpdated(); // core mappings may have changed
        biosManifestSent.clear();
        biosReadyClients.clear(); readyClients.clear();
        for (var player : server.getPlayerManager().getPlayerList()) sendCoreSettings(player);
    }

    /** /retrocraft reload: re-reads server.json from disk and applies it. */
    public boolean reloadConfig(net.minecraft.server.command.ServerCommandSource source) {
        try {
            config = RetrocraftConfig.load();
            applyConfig();
            return true;
        } catch (IOException ex) { Retrocraft.LOG.error("Cannot reload Retrocraft config", ex); return false; }
    }

    public boolean reloadLibrary(net.minecraft.server.command.ServerCommandSource source) { return reloadLibrary(source, false); }
    public boolean reloadLibrary(net.minecraft.server.command.ServerCommandSource source, boolean onlyNew) {
        if (scanning || stopping) return false;
        startScan(source, onlyNew);
        return true;
    }

    /** A library mutation from the operator GUI: rescan now, coalescing bursts and retrying after a busy scan. */
    private String scanActor;
    public void rescan() { rescan(null); }
    public void rescan(String actor) { rescan(actor, false); }
    public void rescan(String actor, boolean onlyNew) {
        if (stopping) return;
        if (actor != null) scanActor = actor;
        if (scanning) { pendingScan = true; pendingOnlyNew = onlyNew; return; }
        startScan(null, onlyNew);
    }

    private void broadcastScan(boolean started) {
        var packet = new SettingsPackets.ScanEvent(scanActor == null ? "" : scanActor, started);
        for (var player : server.getPlayerManager().getPlayerList())
            if (ServerPlayNetworking.canSend(player, SettingsPackets.ScanEvent.ID)) send(player, packet);
    }

    private void startScan(net.minecraft.server.command.ServerCommandSource source, boolean onlyNew) {
        scanning = true;
        broadcastScan(true);
        if (source != null) source.sendFeedback(() -> Text.translatable("message.retrocraft.scan_started"), false);
        Thread.ofVirtual().name("retrocraft-rom-scan").start(() -> {
            try {
                RomIndex result = loadLibrary(true, onlyNew);
                callbacks.add(() -> {
                    scanning = false; roms = result; libraryUpdated(); broadcastScan(false);
                    if (source != null) source.sendFeedback(() -> Text.translatable("message.retrocraft.reloaded", roms.games().size()), false);
                    else Retrocraft.LOG.info("ROM catalog updated: {} ROMs", roms.games().size());
                    if (pendingScan) { pendingScan = false; rescan(null, pendingOnlyNew); }
                });
            } catch (Exception ex) {
                Retrocraft.LOG.error("Cannot scan ROM catalog", ex);
                callbacks.add(() -> {
                    scanning = false; broadcastScan(false);
                    if (source != null) source.sendError(Text.translatable("message.retrocraft.scan_failed"));
                    if (pendingScan) { pendingScan = false; rescan(null, pendingOnlyNew); }
                });
            }
        });
    }
    private void libraryUpdated() {
        for (Session session : sessions.values()) {
            RomIndex.Game replacement = roms.find(session.game.cartridge(), session.data.gameId());
            if (replacement != null) {
                if (session.game != replacement) { session.clientFit = null; session.checkingFit = false; }
                session.game = replacement;
                if (session.thread == null) session.failure = null;
            }
        }
        Set<String> needed = roms.games().stream().map(game -> config.coreId(game.cartridge().id().toString()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean coresChanged = coreGeneration == 0 || !requiredCores.equals(needed);
        boolean catalogChanged = !publishedCatalog.equals(roms.catalog());
        requiredCores = needed; publishedCatalog = roms.catalog();
        if (coresChanged) { coreGeneration++; readyClients.clear(); verifiedClients.clear(); biosReadyClients.clear(); biosManifestSent.clear(); }
        for (String id : requiredCores) if (!preparedCores.containsKey(id) && preparingCores.add(id)) {
            Thread.ofVirtual().name("retrocraft-core-" + id).start(() -> {
                try {
                    var prepared = cores.prepare(id);
                    callbacks.add(() -> { preparingCores.remove(id); preparedCores.put(id, prepared); Retrocraft.LOG.info("Core {} ready: {} {}", id, prepared.identity().name(), prepared.identity().version()); if (requiredCores.contains(id)) for (var player : server.getPlayerManager().getPlayerList()) sendCoreSettings(player); });
                } catch (Exception ex) { Retrocraft.LOG.error("Cannot prepare known core {}", id, ex); callbacks.add(() -> preparingCores.remove(id)); }
            });
        }
        if (catalogChanged) catalogGeneration++;
        failedDiscovery.clear();
        if (catalogChanged || coresChanged) for (var player : server.getPlayerManager().getPlayerList()) sendCatalog(player, coresChanged);
    }
    public void sendCatalog(ServerPlayerEntity player) { sendCatalog(player, true); }
    private void sendCatalog(ServerPlayerEntity player, boolean settings) {
        if (roms == null) return;
        if (settings) sendCoreSettings(player);
        if (!ServerPlayNetworking.canSend(player, Packets.Catalog.ID)) return;
        List<RomCatalog.Entry> catalog = roms.catalog();
        if (catalog.isEmpty()) send(player, new Packets.Catalog(catalogGeneration, true, true, List.of()));
        for (int i = 0; i < catalog.size(); i += 128)
            send(player, new Packets.Catalog(catalogGeneration, i == 0, i + 128 >= catalog.size(), catalog.subList(i, Math.min(i + 128, catalog.size()))));
    }

    private static boolean operator(ServerPlayerEntity player) { return player.hasPermissionLevel(2); }

    /** /retrocraft gui or a client's RequestOpenSettingsGUI: operators get the schema, anyone else a denial. */
    public void openSettings(ServerPlayerEntity player) {
        send(player, operator(player) ? SettingsPackets.GuiOpen.of(config) : SettingsPackets.GuiOpen.denied("operator"));
    }

    /** Applies a batch of key/value edits. Validated on a probe copy first so a bad value cannot half-apply. */
    public void applySettings(ServerPlayerEntity player, SettingsPackets.ConfigApply packet) {
        if (!operator(player)) return;
        String error = null;
        var gson = new Gson();
        RetrocraftConfig probe = gson.fromJson(gson.toJson(config), RetrocraftConfig.class);
        for (var change : packet.changes()) {
            try { ConfigKey.set(probe, change.key(), change.value()); }
            catch (IllegalArgumentException ex) { error = change.key() + ": " + ex.getMessage(); break; }
        }
        if (error == null) {
            for (var change : packet.changes()) ConfigKey.set(config, change.key(), change.value());
            try { config.save(); applyConfig(); }
            catch (IOException ex) { error = "Could not save the config file"; Retrocraft.LOG.error("Cannot save Retrocraft config", ex); }
        }
        send(player, new SettingsPackets.ConfigSaved(error == null, error == null ? "" : error, SettingsPackets.GuiOpen.schema(config)));
        if (error == null) Retrocraft.LOG.info("{} updated Retrocraft settings via GUI", player.getGameProfile().getName());
    }

    /** RomManagementPacket: directory listings and library mutations, one request id per response. */
    public void romAction(ServerPlayerEntity player, SettingsPackets.RomAction packet) {
        if (!operator(player)) return;
        boolean bios = packet.area() == SettingsPackets.AREA_BIOS;
        try {
            switch (packet.action()) {
                case SettingsPackets.LIST -> sendRomList(player, packet.request(), packet.path(), bios);
                case SettingsPackets.DELETE -> { library(bios).delete(packet.path()); romChanged(player, packet.request(), !bios); }
                case SettingsPackets.RENAME -> { library(bios).rename(packet.path(), packet.target()); romChanged(player, packet.request(), !bios); }
                case SettingsPackets.MOVE -> { library(bios).move(packet.path(), packet.target()); romChanged(player, packet.request(), !bios); }
                case SettingsPackets.MKDIR -> { library(bios).mkdir(packet.path()); romChanged(player, packet.request(), !bios); }
                case SettingsPackets.RESCAN -> { rescan(player.getGameProfile().getName()); send(player, new SettingsPackets.RomEvent(packet.request(), SettingsPackets.ACTION, true, "")); }
                case SettingsPackets.RESCAN_NEW -> { rescan(player.getGameProfile().getName(), true); send(player, new SettingsPackets.RomEvent(packet.request(), SettingsPackets.ACTION, true, "")); }
                default -> send(player, SettingsPackets.RomEvent.fail(packet.request(), "Unknown action"));
            }
        } catch (IOException | RuntimeException ex) {
            send(player, SettingsPackets.RomEvent.fail(packet.request(), ex.getMessage() == null ? "Operation failed" : ex.getMessage()));
        }
    }
    private RomLibrary library(boolean bios) { return bios ? biosLibrary : romLibrary; }
    private void romChanged(ServerPlayerEntity player, int request, boolean rescan) {
        send(player, new SettingsPackets.RomEvent(request, SettingsPackets.ACTION, true, ""));
        if (rescan) rescan(player.getGameProfile().getName());
        else biosUpdated();
    }
    private void biosUpdated() {
        biosManifestSent.clear(); biosReadyClients.clear(); readyClients.clear();
        for (var player : server.getPlayerManager().getPlayerList()) sendCoreSettings(player);
    }
    private void sendRomList(ServerPlayerEntity player, int request, String path, boolean bios) throws IOException {
        var listing = library(bios).list(path);
        send(player, new SettingsPackets.RomList(request, path, listing.truncated(),
                listing.entries().stream().map(e -> new SettingsPackets.RomList.Entry(e.name(), e.directory(), e.size())).toList()));
    }

    /** All known cores preload in the background; nothing here is user-configurable anymore. */


    private static final class Upload {
        final int id; final int area; final String dir, name, sha256; final long total;
        final ByteArrayOutputStream data; long lastActivity;
        Upload(int id, int area, String dir, String name, long total, String sha256) {
            this.id = id; this.area = area; this.dir = dir; this.name = name; this.sha256 = sha256; this.total = total;
            data = new ByteArrayOutputStream((int) Math.min(total, 1 << 20));
            lastActivity = System.nanoTime();
        }
    }

    /** Chunked ROM upload; one active transfer per operator, staged in memory and verified before it lands. */
    public void romUpload(ServerPlayerEntity player, SettingsPackets.RomUpload packet) {
        if (!operator(player)) return;
        switch (packet.phase()) {
            case SettingsPackets.BEGIN -> beginUpload(player, packet);
            case SettingsPackets.CHUNK -> uploadChunk(player, packet);
            case SettingsPackets.ABORT -> { romUploads.remove(player.getUuid()); send(player, new SettingsPackets.RomEvent(packet.id(), SettingsPackets.UPLOAD_FAIL, true, "cancelled")); }
            default -> { }
        }
    }
    private void beginUpload(ServerPlayerEntity player, SettingsPackets.RomUpload packet) {
        try {
            if (romUploads.size() >= 8 && !romUploads.containsKey(player.getUuid())) throw new IOException("Too many concurrent uploads");
            if (packet.total() < 1 || packet.total() > Math.min((long) config.maxRomSizeMiB * 1024 * 1024, BlobTransfer.MAX_ROM)) throw new IOException("File outside the ROM size limit");
            if (!packet.sha256().matches("[0-9a-f]{64}")) throw new IOException("Invalid checksum");
            library(packet.area() == SettingsPackets.AREA_BIOS).destination(packet.dir(), packet.name()); // names, folder and collisions checked up front
            romUploads.put(player.getUuid(), new Upload(packet.id(), packet.area(), packet.dir(), packet.name(), packet.total(), packet.sha256()));
            send(player, new SettingsPackets.RomEvent(packet.id(), SettingsPackets.UPLOAD_ACCEPT, true, ""));
        } catch (IOException | RuntimeException ex) {
            send(player, new SettingsPackets.RomEvent(packet.id(), SettingsPackets.UPLOAD_FAIL, true, ex.getMessage() == null ? "Upload rejected" : ex.getMessage()));
        }
    }
    private void uploadChunk(ServerPlayerEntity player, SettingsPackets.RomUpload packet) {
        Upload upload = romUploads.get(player.getUuid());
        if (upload == null || upload.id != packet.id()) return; // stale chunk of an aborted transfer
        try {
            if (packet.offset() != upload.data.size() || packet.data().length < 1
                    || upload.data.size() + packet.data().length > upload.total) throw new IOException("Chunk outside transfer bounds");
            upload.data.writeBytes(packet.data()); upload.lastActivity = System.nanoTime();
            if (upload.data.size() < upload.total) return;
            romUploads.remove(player.getUuid());
            UUID playerId = player.getUuid();
            String actor = player.getGameProfile().getName();
            Thread.ofVirtual().name("retrocraft-library-install").start(() -> {
                try {
                    byte[] bytes = upload.data.toByteArray();
                    if (!BlobTransfer.hash(bytes).equals(upload.sha256)) throw new IOException("Upload checksum mismatch");
                    boolean bios = upload.area == SettingsPackets.AREA_BIOS;
                    RomLibrary target = library(bios);
                    int landed;
                    synchronized (target) { landed = target.install(upload.dir, upload.name, bytes, bios); }
                    Retrocraft.LOG.info("{} uploaded {} {}", actor, bios ? "BIOS" : "ROM", upload.name);
                    callbacks.add(() -> {
                        var current = server.getPlayerManager().getPlayer(playerId);
                        if (current != null) send(current, new SettingsPackets.RomEvent(packet.id(), SettingsPackets.UPLOAD_DONE, true, landed > 1 ? landed + " files" : upload.name));
                        if (bios) biosUpdated(); else rescan(actor);
                    });
                } catch (IOException | RuntimeException ex) {
                    callbacks.add(() -> {
                        var current = server.getPlayerManager().getPlayer(playerId);
                        if (current != null) send(current, new SettingsPackets.RomEvent(packet.id(), SettingsPackets.UPLOAD_FAIL, true,
                                ex.getMessage() == null ? "Upload failed" : ex.getMessage()));
                    });
                }
            });
        } catch (IOException | RuntimeException ex) {
            romUploads.remove(player.getUuid());
            send(player, new SettingsPackets.RomEvent(packet.id(), SettingsPackets.UPLOAD_FAIL, true, ex.getMessage() == null ? "Upload failed" : ex.getMessage()));
        }
    }

    public void powerOn(ConsoleHost host, ServerPlayerEntity player) {
        if (stopping || !host.canAccess(player) || host.instance() != null) return;
        if (roms == null) { error(player, "core_preparing"); return; }
        if (activeCount() + pendingStarts.size() >= config.maxActiveInstances) { error(player, "limit"); return; }
        ItemStack cartridge = host.cartridge();
        if (!cartridge.isOf(host.definition().cartridge())) { error(player, "insert_cartridge"); return; }
        RomIndex.Game game = roms.find(host.definition().cartridge(), CartridgeItem.gameId(cartridge));
        if (game == null) { error(player, "invalid_rom"); return; }
        if (host.definition().usesTelevision()) {
            if (!(host instanceof DockedConsoleBlockEntity console) || Televisions.nearestFree(console) == null) { error(player, "no_tv"); return; }
        }
        String coreId = config.coreId(game.cartridge().id().toString());
        CoreManager.Prepared prepared = preparedCores.get(coreId);
        if (prepared == null) { error(player, "core_preparing"); return; }
        Object key = host instanceof HandheldHost handheld ? handheld.stack() : host;
        if (pendingStarts.containsKey(key)) return;
        PendingStart pending = new PendingStart();
        pendingStarts.put(key, pending);
        UUID playerId = player.getUuid();
        String consoleId = host.definition().id().toString();
        Thread.ofVirtual().name("retrocraft-power-on").start(() -> {
            try {
                requireBios(coreId);
                var data = store.create(playerId, consoleId, game.id(), game.name(), prepared.path());
                pending.created = data.id();
                restoreSave(data);
                if (stopping) { destroyFiles(data.id()); return; }
                callbacks.add(() -> {
                    pendingStarts.remove(key);
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (stopping || current == null || !host.valid() || host.instance() != null
                            || !game.id().equals(CartridgeItem.gameId(host.cartridge()))
                            || activeCount() >= config.maxActiveInstances) {
                        Thread.ofVirtual().name("retrocraft-cancel-power-on").start(() -> destroyFiles(data.id()));
                        return;
                    }
                    TvBlockEntity tv = host instanceof DockedConsoleBlockEntity console && host.definition().usesTelevision()
                            ? Televisions.nearestFree(console) : null;
                    if (host.definition().usesTelevision() && tv == null) {
                        Thread.ofVirtual().name("retrocraft-cancel-power-on").start(() -> destroyFiles(data.id()));
                        error(current, "no_tv");
                        return;
                    }
                    host.instance(data.id());
                    if (tv != null) Televisions.bind((DockedConsoleBlockEntity) host, tv, data.id());
                    sessions.put(data.id(), new Session(data, game, host));
                    if (host.canAccess(current) && current.currentScreenHandler instanceof ConsoleScreenHandler screen && screen.host() == host)
                        ConsoleMenus.open(current, host);
                });
            } catch (IOException | IllegalArgumentException ex) {
                if (pending.created != null) destroyFiles(pending.created);
                Retrocraft.LOG.error("Cannot power on console", ex);
                callbacks.add(() -> {
                    pendingStarts.remove(key);
                    if (host.canAccess(player)) error(player, ex.getMessage() != null && ex.getMessage().startsWith("PS2 BIOS missing")
                            ? "bios_missing" : "storage_failed");
                });
            }
        });
    }

    /** A bound television was broken: its console goes dark too. */
    public void televisionRemoved(TvBlockEntity tv) {
        if (tv.console() == null || tv.getWorld() == null) return;
        if (tv.getWorld().getBlockEntity(tv.console()) instanceof DockedConsoleBlockEntity console && tv.getPos().equals(console.tv())) powerOff(console);
    }

    /** Restore a powered item/block after joining, chunk load, inventory transfer or server restart. */
    public void discover(ConsoleHost host) {
        UUID id = host.instance();
        if (stopping || roms == null || id == null || !host.valid() || failedDiscovery.contains(id)) return;
        Session existing = sessions.get(id);
        if (existing != null) { if (!existing.host.valid()) existing.host = host; return; }
        try {
            var data = store.load(id);
            RomIndex.Game game = roms.find(host.definition().cartridge(), CartridgeItem.gameId(host.cartridge()));
            if (game == null || !host.cartridge().isOf(host.definition().cartridge()) || !data.gameId().equals(game.id())
                    || !data.console().equals(host.definition().id().toString())) throw new IOException("Console/cartridge identity mismatch");
            sessions.put(id, new Session(data, game, host));
        } catch (IOException ex) { failedDiscovery.add(id); Retrocraft.LOG.error("Cannot discover powered console {}", id, ex); }
    }

    public void powerOff(ConsoleHost host) {
        UUID id = host.instance();
        if (id == null) return;
        host.instance(null);
        if (host instanceof DockedConsoleBlockEntity console) Televisions.unbind(console);
        failedDiscovery.remove(id);
        Session session = sessions.get(id);
        if (session != null) {
            if (session.ownership.owner() != null) revoke(session, false);
            session.poweredOff = true;
            session.destroy = true;
            session.observed = false;
            stopSubscribers(session);
            if (session.thread != null && session.thread.isAlive()) return;
            sessions.remove(id);
            Thread.ofVirtual().name("retrocraft-console-destroy").start(() -> destroySession(session));
            return;
        }
        Thread.ofVirtual().name("retrocraft-console-destroy").start(() -> destroyFiles(id));
    }

    public void watchVideo(ServerPlayerEntity player, ConsoleHost host) {
        if (!host.canAccess(player) || host.instance() == null) return;
        discover(host);
        Session session = sessions.get(host.instance());
        if (session == null || session.destroy) { error(player, "invalid_instance"); return; }
        if (session.failure != null) { error(player, failureKey(session.failure)); return; }
        detachVideo(player.getUuid());
        VideoWatcher existing = session.videoWatchers.get(player.getUuid());
        if (existing != null) existing.ambient = false; else session.videoWatchers.put(player.getUuid(), new VideoWatcher());
        videoWatching.put(player.getUuid(), session.data.id());
        session.forceKeyframe = true;
        joinInput(player, session.data.id());
        send(player, status(session, player, Packets.PLAY));
        notifyPlayers(session);
    }

    public boolean needsReset(ConsoleHost host) {
        if (host.instance() == null) return false;
        discover(host);
        Session session = sessions.get(host.instance());
        return session == null || session.failure != null || session.destroy;
    }

    private static int maxSlots(Session session) { return session.host.definition().type() == ConsoleType.DOCKED ? WorkerProtocol.PORTS : 1; }
    private static int slotOf(Session session, UUID player) {
        for (int slot = 0; slot < session.pads.length; slot++) if (session.pads[slot] != null && session.pads[slot].player.equals(player)) return slot;
        return -1;
    }

    /** Right-clicking a powered docked console grabs the next free controller slot; handhelds stay single-player. */
    public boolean joinInput(ServerPlayerEntity player, UUID id) {
        Session session = sessions.get(id);
        if (session == null || session.destroy || !session.host.canAccess(player)) return false;
        if (slotOf(session, player.getUuid()) >= 0) { inputSessions.put(player.getUuid(), id); return true; }
        int slot = -1;
        for (int i = 0; i < maxSlots(session); i++) if (session.pads[i] == null) { slot = i; break; }
        if (slot < 0) { error(player, "input_busy"); return false; }
        releaseInput(player.getUuid());
        session.pads[slot] = new InputSource(player.getUuid());
        inputSessions.put(player.getUuid(), id);
        session.failedOffload = null;
        notifyRoles(session);
        notifyPlayers(session);
        return true;
    }
    public boolean isInputSource(UUID player, UUID instance) {
        if (instance == null || !instance.equals(inputSessions.get(player))) return false;
        Session session = sessions.get(instance);
        return session != null && slotOf(session, player) >= 0;
    }
    public void releaseInput(UUID player) {
        Session session = sessions.get(inputSessions.remove(player));
        if (session == null) return;
        int slot = slotOf(session, player);
        if (slot < 0) return;
        session.pads[slot] = null;
        if (slot == 0) returnOwnership(session);
        broadcastPad(session, slot, 0);
        notifyRoles(session);
        notifyPlayers(session);
    }
    /** The picture is drawn on a block: the arcade cabinet's own CRT or the television bound to a docked console. */
    private static boolean inWorld(Session session) {
        var definition = session.host.definition();
        return definition.type() == ConsoleType.DOCKED && (!definition.usesTelevision() || session.host instanceof DockedConsoleBlockEntity console && console.tv() != null);
    }
    private void detachVideo(UUID player) {
        Session session = sessions.get(videoWatching.remove(player));
        if (session != null) {
            session.videoWatchers.remove(player);
            if (session.videoWatchers.isEmpty()) resetVideo(session);
        }
        releaseInput(player);
    }
    public void disconnect(UUID player) {
        for (Session session : sessions.values()) if (player.equals(session.ownership.owner())) revoke(session, false);
        readyClients.remove(player); verifiedClients.remove(player); biosReadyClients.remove(player);
        clientGenerations.remove(player); outbound.remove(player); romUploads.remove(player);
        biosManifestSent.remove(player); biosPending.remove(player); biosOutgoing.remove(player);
        detachVideo(player);
        for (Session session : sessions.values()) { session.audioWatchers.remove(player); session.videoWatchers.remove(player); }
    }

    public void request(ServerPlayerEntity player, Packets.Request packet) {
        if (stopping) return;
        UUID id = packet.instance();
        Session session = sessions.get(id);
        if (session == null || session.destroy) return;
        UUID playerId = player.getUuid();
        VideoWatcher video = session.videoWatchers.get(playerId);
        long now = System.nanoTime();
        switch (packet.action()) {
            case Packets.AUDIO_ACK -> {
                AudioWatcher watcher = session.audioWatchers.get(playerId);
                if (watcher != null) watcher.lastAcknowledged = Math.max(watcher.lastAcknowledged, Math.min(watcher.lastSent, packet.acknowledgedSequence()));
            }
            case Packets.VIDEO_ACK -> {
                if (video != null) {
                    video.lastSeen = now;
                    video.lastAcknowledged = Math.max(video.lastAcknowledged, Math.min(video.lastSent, packet.acknowledgedSequence()));
                }
            }
            case Packets.INPUT -> {
                if (video != null) {
                    video.lastSeen = now;
                    video.lastAcknowledged = Math.max(video.lastAcknowledged, Math.min(video.lastSent, packet.acknowledgedSequence()));
                }
                int slot = slotOf(session, playerId);
                InputSource input = slot >= 0 ? session.pads[slot] : null;
                if (input != null && session.host.canAccess(player)) {
                    int mask = packet.buttons() & 0xffff;
                    if (input.buttons != mask) { input.buttons = mask; broadcastPad(session, slot, mask); }
                    input.lastSeen = now;
                }
            }
            case Packets.CLOSE -> { if (video != null) detachVideo(playerId); }
            case Packets.RESYNC -> {
                if (video == null || now - video.lastResync < TimeUnit.SECONDS.toNanos(1)) return;
                video.lastResync = now; video.lastAcknowledged = video.lastSent; video.needsKeyframe = true; session.forceKeyframe = true;
            }
            case Packets.POWER_OFF -> {
                if (!session.host.canAccess(player)) return;
                ConsoleHost host = session.host; powerOff(host); ConsoleMenus.inventory(player, host);
            }
            case Packets.EJECT -> {
                if (!session.host.canAccess(player)) return;
                ConsoleHost host = session.host;
                powerOff(host);
                ItemStack cartridge = host.cartridge();
                host.cartridge(ItemStack.EMPTY);
                if (!player.getInventory().insertStack(cartridge)) player.dropItem(cartridge, false);
                ConsoleMenus.inventory(player, host);
            }
            case Packets.CLAIM_INPUT -> joinInput(player, id);
            default -> { }
        }
    }

    public void tick() {
        Runnable callback;
        for (int i = 0; i < 256 && (callback = callbacks.poll()) != null; i++) if (!stopping) callback.run();
        flushTransfers();
        flushBios();
        if (roms == null) return;
        ticks++;
        if (ticks % 20 == 1) discoverItems();
        if (ticks % (20 * config.thumbnailIntervalSeconds) == 1) markThumbnails();
        if (ticks % (20 * 3600) == 2) Thread.ofVirtual().name("retrocraft-saves-cleanup").start(this::cleanOrphanSaves);
        long now = System.nanoTime();
        for (var it = romUploads.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (now - entry.getValue().lastActivity <= TimeUnit.SECONDS.toNanos(60)) continue;
            it.remove();
            var player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) send(player, new SettingsPackets.RomEvent(entry.getValue().id, SettingsPackets.UPLOAD_FAIL, true, "Upload timed out"));
        }
        for (Session session : List.copyOf(sessions.values())) {
            finish(session);
            if (session.destroy) {
                if (session.thread == null) {
                    sessions.remove(session.data.id());
                    Thread.ofVirtual().name("retrocraft-console-destroy").start(() -> destroySession(session));
                }
                continue;
            }
            updateSubscriptions(session, now);
            sendDemand(session);
            session.wantsVideo = !session.videoWatchers.isEmpty();
            session.wantsAudio = !session.audioWatchers.isEmpty();
            session.observed = session.wantsVideo || session.wantsAudio;
            if (session.ownership.expired(now)) revoke(session, true);
            if (session.incoming.values().stream().anyMatch(receiver -> receiver.reserved() > 0)
                    && now - session.lastIncomingChunk > TimeUnit.SECONDS.toNanos(15)) revoke(session, true);
            if (session.ownership.mode() == Ownership.Mode.CLIENT && now - session.lastCheckpointAt
                    > TimeUnit.SECONDS.toNanos(BlobTransfer.timeoutSeconds(Math.max(config.ownerTimeoutSeconds, config.checkpointIntervalSeconds * 3L + 5), session.checkpointBytes, 2))) revoke(session, true);
            if (session.thread == null && session.observed && session.failure == null && session.ownership.serverMayRun() && serverMayEmulate()
                    && preparedCores.containsKey(config.coreId(session.game.cartridge().id().toString())) && activeCount() < config.maxActiveInstances) start(session);
            coordinateOwnership(session, now);
            if (session.thread == null && !session.host.valid() && !session.observed && session.ownership.serverMayRun()) {
                sessions.remove(session.data.id());
                Thread.ofVirtual().name("retrocraft-console-unload").start(() -> persistSave(session));
                continue;
            }
            stream(session, now);
        }
    }

    private void discoverItems() {
        for (var player : server.getPlayerManager().getPlayerList()) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() instanceof HandheldConsoleItem && HandheldConsoleItem.instance(stack) != null) discover(new HandheldHost(player, stack));
            }
        }
        for (var world : server.getWorlds()) for (var entity : world.iterateEntities()) {
            if (entity instanceof ItemEntity item && item.getStack().getItem() instanceof HandheldConsoleItem && HandheldConsoleItem.instance(item.getStack()) != null)
                discover(new HandheldHost(item));
        }
    }

    private void updateSubscriptions(Session session, long now) {
        boolean valid = session.host.valid() && session.data.id().equals(session.host.instance());
        if (valid) session.invalidSince = 0; else if (session.invalidSince == 0) session.invalidSince = now;
        // A handheld being dropped or picked up keeps playing while discovery swaps the host.
        boolean grace = valid || now - session.invalidSince < TimeUnit.SECONDS.toNanos(3);
        for (UUID id : List.copyOf(session.videoWatchers.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            VideoWatcher watcher = session.videoWatchers.get(id);
            if (!valid || player == null || (!watcher.ambient && !session.host.canAccess(player))
                    || now - watcher.lastSeen > TimeUnit.SECONDS.toNanos(config.watcherTimeoutSeconds)) {
                if (watcher.ambient) { session.videoWatchers.remove(id); continue; }
                if (player != null) send(player, stopped(session));
                detachVideo(id);
            }
        }
        Set<UUID> listeners = new HashSet<>();
        boolean inWorld = inWorld(session);
        if (grace && session.failure == null) for (var player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() != session.host.world() || !ServerPlayNetworking.canSend(player, Packets.Audio.ID)) continue;
            double distance = player.getPos().distanceTo(session.host.soundPosition());
            if (distance >= config.audioRadius) continue;
            listeners.add(player.getUuid());
            session.audioWatchers.computeIfAbsent(player.getUuid(), ignored -> new AudioWatcher(1)).gain = (float) (1 - distance / config.audioRadius);
            // Anyone near a television or arcade cabinet sees the picture on the block without opening the play screen.
            if (inWorld && !session.videoWatchers.containsKey(player.getUuid())) {
                VideoWatcher watcher = new VideoWatcher(); watcher.ambient = true;
                session.videoWatchers.put(player.getUuid(), watcher); session.forceKeyframe = true;
            }
        }
        for (var entry : List.copyOf(session.videoWatchers.entrySet())) if (entry.getValue().ambient && (!inWorld || !listeners.contains(entry.getKey()))) {
            session.videoWatchers.remove(entry.getKey());
            if (session.videoWatchers.isEmpty()) resetVideo(session);
        }
        for (UUID id : List.copyOf(session.audioWatchers.keySet())) if (!listeners.contains(id)) {
            session.audioWatchers.remove(id);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) send(player, new Packets.AudioStop(session.data.id()));
        }
        for (int slot = 0; slot < session.pads.length; slot++) {
            InputSource input = session.pads[slot];
            if (input == null) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(input.player);
            if (!valid || player == null || !session.host.canAccess(player)
                    || now - input.lastSeen > TimeUnit.SECONDS.toNanos(config.watcherTimeoutSeconds)) releaseInput(input.player);
        }
    }

    private void stream(Session session, long now) {
        if (now + 1_000_000L < session.nextStream) return;
        long period = 1_000_000_000L / config.streamFps;
        session.nextStream = Math.max(session.nextStream, now - period) + period;
        WorkerProcess.Output output = session.media.drain();
        if (output == null) return;
        if (output.frame() != null) {
            session.lastFrame = output.frame();
            if (session.thumbDue) { session.thumbDue = false; broadcastThumbnail(session); }
        }
        // Audio fan-out never depends on video acknowledgements or on an open HUD.
        if (output.audio().length > 0) session.audioSequence++;
        if (output.audio().length > 0) for (var listener : session.audioWatchers.entrySet()) {
            AudioWatcher watcher = listener.getValue();
            if (watcher.lastSent - watcher.lastAcknowledged >= 6) continue;
            var player = server.getPlayerManager().getPlayer(listener.getKey());
            if (player != null && !session.ownership.clientMayRun(player.getUuid(), session.ownership.epoch())) { send(player, new Packets.Audio(session.data.id(), session.audioSequence, session.sampleRate, watcher.gain, output.audio())); watcher.lastSent = session.audioSequence; }
        }
        if (output.frame() == null || session.videoWatchers.isEmpty() || session.encodingVideo) return;
        List<UUID> targets = new ArrayList<>();
        for (var entry : session.videoWatchers.entrySet()) {
            VideoWatcher watcher = entry.getValue();
            if (watcher.lastSent - watcher.lastAcknowledged >= 4) { watcher.needsKeyframe = true; continue; }
            if (server.getPlayerManager().getPlayer(entry.getKey()) != null) targets.add(entry.getKey());
            if (watcher.needsKeyframe) session.forceKeyframe = true;
        }
        if (targets.isEmpty()) return;
        boolean key = session.forceKeyframe || now >= session.nextKeyframe;
        if (key) { session.forceKeyframe = false; session.nextKeyframe = now + TimeUnit.SECONDS.toNanos(config.keyframeIntervalSeconds); }
        session.encodingVideo = true;
        int generation = session.videoGeneration;
        FrameCodec.Encoder encoder = session.encoder;
        FrameCodec.Frame picture = output.frame();
        Thread.ofVirtual().name("retrocraft-video-encode").start(() -> {
            try {
                var frame = encoder.encode(picture, key);
                callbacks.add(() -> {
                    if (generation != session.videoGeneration) return;
                    session.encodingVideo = false;
                    for (UUID id : targets) {
                        VideoWatcher watcher = session.videoWatchers.get(id);
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                        if (watcher == null || player == null || watcher.needsKeyframe && !key) continue;
                        send(player, new Packets.Video(session.data.id(), frame));
                        watcher.lastSent = frame.sequence(); watcher.needsKeyframe = false;
                    }
                });
            } catch (IOException ex) { callbacks.add(() -> {
                if (generation == session.videoGeneration) { session.encodingVideo = false; session.forceKeyframe = true; Retrocraft.LOG.error("Cannot encode console video", ex); }
            }); }
        });
    }

    private static void resetVideo(Session session) {
        session.encoder = new FrameCodec.Encoder();
        session.videoGeneration++;
        session.encodingVideo = false;
        session.forceKeyframe = true;
    }

    private boolean serverMayEmulate() { return config.emulationMode != RetrocraftConfig.EmulationMode.CLIENT; }
    private boolean clientMayEmulate() { return config.emulationMode != RetrocraftConfig.EmulationMode.SERVER; }
    /** ROMs over the client pool bound never leave the server — hybrid degrades to server-side, client mode just refuses. */
    private boolean clientFits(Session session) {
        if (session.clientFit != null) return session.clientFit;
        if (!session.checkingFit) {
            session.checkingFit = true;
            RomIndex.Game game = session.game;
            long limit = Math.min((long) config.maxRomSizeMiB * 1024 * 1024, BlobTransfer.MAX_ROM);
            Thread.ofVirtual().name("retrocraft-client-fit").start(() -> {
                boolean fits;
                try { fits = Files.size(game.materialize()) <= limit; }
                catch (IOException ex) { fits = false; }
                boolean result = fits;
                callbacks.add(() -> {
                    if (session.game == game) { session.clientFit = result; session.checkingFit = false; }
                });
            });
        }
        return false;
    }

    /** Everyone who could glance at the console: trackers of its carrier plus the holder itself. */
    private Set<ServerPlayerEntity> audience(HandheldHost host) {
        Entity carrier = host.carrier();
        if (carrier == null) return null;
        Set<ServerPlayerEntity> audience = new HashSet<>(PlayerLookup.tracking(carrier));
        if (carrier instanceof ServerPlayerEntity player) audience.add(player);
        audience.removeIf(player -> !ServerPlayNetworking.canSend(player, Packets.Thumbnail.ID));
        return audience.isEmpty() ? null : audience;
    }

    /** Every cycle: running handhelds capture a fresh frame on their next advance; paused ones resend the cached preview. */
    private void markThumbnails() {
        for (Session session : sessions.values()) {
            if (session.destroy || !(session.host instanceof HandheldHost host) || audience(host) == null) continue;
            if (session.lastThumb != null) resendThumbnail(session, host); // cached preview always refreshes the watchers' TTL
            if (session.thread != null) session.thumbDue = true;
            else if (session.lastThumb == null && session.lastFrame != null) broadcastThumbnail(session);
        }
    }

    private void resendThumbnail(Session session, HandheldHost host) {
        Set<ServerPlayerEntity> audience = audience(host);
        if (audience != null) for (var player : audience) send(player, new Packets.Thumbnail(session.data.id(), session.lastThumb));
    }

    private void broadcastThumbnail(Session session) {
        if (session.destroy || !(session.host instanceof HandheldHost host) || session.lastFrame == null) return;
        if (audience(host) == null) return;
        FrameCodec.Frame frame = session.lastFrame;
        if (session.lastThumbSource == frame && session.lastThumb != null) { resendThumbnail(session, host); return; }
        Thread.ofVirtual().name("retrocraft-thumbnail").start(() -> {
            try {
                byte[] png = ThumbnailCodec.encode(frame);
                callbacks.add(() -> {
                    session.lastThumbSource = frame; session.lastThumb = png;
                    if (!session.destroy && session.host instanceof HandheldHost current) resendThumbnail(session, current);
                });
            } catch (IOException ignored) { }
        });
    }

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /** Hybrid owners push their own in-hand preview; the server adopts it as the session thumbnail and fans it out. */
    public void thumbnailUpload(ServerPlayerEntity player, Packets.ThumbnailUpload packet) {
        if (!config.thumbnailUpload || stopping) return;
        Session session = sessions.get(packet.instance());
        if (session == null || session.destroy || !(session.host instanceof HandheldHost host)
                || !session.ownership.clientMayRun(player.getUuid(), session.ownership.epoch())) return;
        byte[] png = packet.png();
        if (png.length < PNG_SIGNATURE.length || !Arrays.equals(Arrays.copyOf(png, PNG_SIGNATURE.length), PNG_SIGNATURE)) return;
        long now = System.nanoTime();
        if (now - session.lastUploadAt < 1_000_000_000L) return;
        session.lastUploadAt = now;
        session.lastThumb = png; session.lastThumbSource = null;
        Set<ServerPlayerEntity> audience = audience(host);
        if (audience != null) for (var watcher : audience) if (!watcher.getUuid().equals(player.getUuid())) send(watcher, new Packets.Thumbnail(session.data.id(), png));
    }

    private long activeCount() { return sessions.values().stream().filter(s -> s.thread != null && !s.finished).count(); }
    private void start(Session session) {
        session.handoffStop = false;
        RomIndex.Game game = session.game;
        session.thread = Thread.ofVirtual().name("retrocraft-" + session.data.id()).unstarted(() -> {
            Path state = store.snapshot(session.data.id());
            try {
                String coreId = config.coreId(game.cartridge().id().toString());
                CoreManager.Prepared prepared = preparedCores.get(coreId);
                if (prepared == null) throw new IOException("Core not prepared");
                requireBios(coreId);
                Path core = prepared.path();
                Path rom = game.materialize();
                if (!RomSource.matches(rom, session.data.gameId(), game.maxBytes())
                        || !session.data.coreSha256().equals(InstanceStore.sha256(core))) throw new IOException("ROM/core changed since snapshot");
                try (WorkerProcess process = openServerProcess(session, core, rom, state)) {
                    session.process = process; session.sampleRate = process.sampleRate();
                    long period = Math.round(1_000_000_000d / process.fps());
                    long next = System.nanoTime();
                    while (!stopping && !session.destroy && session.observed && !session.handoffStop && session.ownership.serverMayRun() && serverMayEmulate()) {
                        int[] masks = new int[WorkerProtocol.PORTS];
                        long polled = System.nanoTime();
                        for (int port = 0; port < masks.length; port++) {
                            InputSource input = session.pads[port];
                            masks[port] = input != null && polled - input.lastSeen < TimeUnit.SECONDS.toNanos(2) ? input.buttons : 0;
                        }
                        session.media.offer(process.advance(masks, session.wantsVideo || session.thumbDue, session.wantsAudio), process.sampleRate());
                        long now = System.nanoTime();
                        next += period;
                        if (next < now - period * 4) next = now;
                        if (next > now) LockSupport.parkNanos(next - now);
                    }
                    if (!session.destroy) process.save();
                }
            } catch (Throwable ex) { session.failure = ex; Retrocraft.LOG.error("Console instance {} failed", session.data.id(), ex); }
            finally {
                if (session.destroy) destroySession(session);
                session.process = null; session.media.clear(); session.finished = true;
            }
        });
        session.thread.start();
    }

    private WorkerProcess openServerProcess(Session session, Path core, Path rom, Path state) throws IOException {
        Path log = store.directory(session.data.id()).resolve("worker.log");
        // The pool layout is POOL/platform/<coreId>/<binary> — the parent dir name is the trusted id.
        Path bios = RetrocraftConfig.systemDir(core.getParent().getFileName().toString());
        try { return new WorkerProcess(workerJar, core, rom, state, bios, log); }
        catch (IOException ex) {
            if (ex instanceof EOFException && workerLogSays(log, "Core rejected ROM")) throw new RomRejectedException(ex);
            if (!Files.exists(store.backup(session.data.id()))) throw ex;
            Retrocraft.LOG.warn("Latest checkpoint rejected for {}; restoring previous complete state", session.data.id());
            byte[] previous = Files.readAllBytes(store.backup(session.data.id()));
            SaveStates.validate(previous); SaveStates.writeAtomic(state, previous);
            return new WorkerProcess(workerJar, core, rom, state, bios, log);
        }
    }

    /** The worker died before the handshake and its log names the cause (e.g. MAME 2003-Plus rejecting a mismatched romset). */
    static final class RomRejectedException extends IOException { RomRejectedException(Throwable cause) { super("Core rejected ROM", cause); } }
    private static boolean workerLogSays(Path log, String needle) {
        try { return Files.exists(log) && Files.readString(log).contains(needle); } catch (IOException ex) { return false; }
    }

    private static void requireBios(String coreId) throws IOException {
        if (!coreId.equals("pcsx2")) return;
        Path bios = RetrocraftConfig.biosDir(coreId).resolve("bios");
        if (!Files.isDirectory(bios)) throw new IOException("PS2 BIOS missing: place an uncompressed .bin dump in " + bios);
        try (var files = Files.list(bios)) {
            if (files.noneMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin")))
                throw new IOException("PS2 BIOS missing: place an uncompressed .bin dump in " + bios);
        }
    }

    private void finish(Session session) {
        if (!session.finished) return;
        session.thread = null; session.finished = false; resetVideo(session);
        if (session.failure != null) {
            String key = failureKey(session.failure);
            for (UUID id : session.videoWatchers.keySet()) { var player = server.getPlayerManager().getPlayer(id); if (player != null) error(player, key); }
            stopSubscribers(session);
        }
    }
    private static String failureKey(Throwable failure) {
        if (failure instanceof RomRejectedException) return "rom_rejected";
        if (failure instanceof IOException && failure.getMessage() != null) {
            if (failure.getMessage().startsWith("PS2 BIOS missing")) return "bios_missing";
            if (failure.getMessage().startsWith("ROM/core changed since snapshot")) return "rom_changed";
        }
        return "failed";
    }
    private void stopSubscribers(Session session) {
        for (UUID id : List.copyOf(session.videoWatchers.keySet())) {
            var player = server.getPlayerManager().getPlayer(id);
            if (player != null) send(player, stopped(session));
            detachVideo(id);
        }
        session.videoWatchers.clear();
        for (InputSource pad : session.pads) if (pad != null) releaseInput(pad.player);
        for (UUID id : session.audioWatchers.keySet()) { var player = server.getPlayerManager().getPlayer(id); if (player != null) send(player, new Packets.AudioStop(session.data.id())); }
        session.audioWatchers.clear();
    }
    private void notifyRoles(Session session) {
        for (UUID id : session.videoWatchers.keySet()) { var player = server.getPlayerManager().getPlayer(id); if (player != null) send(player, status(session, player, Packets.ROLE)); }
    }
    /** Players with the play screen open; ambient television watchers get no HUD traffic. */
    private void forHudWatchers(Session session, java.util.function.Consumer<ServerPlayerEntity> action) {
        for (var entry : session.videoWatchers.entrySet()) {
            if (entry.getValue().ambient) continue;
            var player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) action.accept(player);
        }
    }
    private void notifyPlayers(Session session) {
        List<Packets.Players.Entry> entries = new ArrayList<>(session.pads.length);
        UUID owner = session.ownership.owner();
        boolean owned = session.ownership.mode() == Ownership.Mode.CLIENT;
        for (int slot = 0; slot < session.pads.length; slot++)
            if (session.pads[slot] != null) entries.add(new Packets.Players.Entry(slot, session.pads[slot].player, owned && session.pads[slot].player.equals(owner)));
        forHudWatchers(session, player -> send(player, new Packets.Players(session.data.id(), entries)));
    }
    private void broadcastPad(Session session, int slot, int mask) {
        forHudWatchers(session, player -> send(player, new Packets.Pad(session.data.id(), slot, mask)));
    }
    private Packets.Ui status(Session session, ServerPlayerEntity player, int mode) {
        long screen = 0;
        if (session.host instanceof DockedConsoleBlockEntity console) screen = (console.tv() != null ? console.tv() : console.getPos()).asLong();
        return new Packets.Ui(mode, session.data.id(), session.host.definition().id().getPath(), session.game.name(), isInputSource(player.getUuid(), session.data.id()), session.host.canAccess(player), inWorld(session), screen);
    }
    private static Packets.Ui stopped(Session session) { return new Packets.Ui(Packets.STOP, session.data.id(), "", "", false, false, false, 0); }
    private void destroyFiles(UUID id) { try { store.destroy(id); } catch (IOException ex) { Retrocraft.LOG.error("Cannot destroy console instance {}", id, ex); } }
    private void destroySession(Session session) {
        if (!session.cleanupStarted.compareAndSet(false, true)) return;
        while (session.checkpointWrites.get() > 0) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        persistSave(session);
        destroyFiles(session.data.id());
    }
    /** Copies the durable per-game save into a freshly created instance so powered consoles resume where they left off. */
    private void restoreSave(InstanceStore.Metadata data) throws IOException {
        if (config.saveMode == RetrocraftConfig.SaveMode.NONE) return;
        byte[] battery = saves.restoreBattery(data.owner(), config.saveMode == RetrocraftConfig.SaveMode.GLOBAL, data.gameId());
        if (battery != null) SaveStates.writeAtomic(store.directory(data.id()).resolve("battery.sav"), battery);
    }

    /** Savestates older than a week without being loaded are orphaned; battery saves are never touched. */
    private void cleanOrphanSaves() {
        int removed = saves.cleanOrphans(java.time.Duration.ofDays(7));
        if (removed > 0) Retrocraft.LOG.info("Removed {} orphaned savestate(s)", removed);
    }

    /**
     * The session is going away: publish its latest snapshot to the durable save directory.
     * A clean shutdown deletes the savestate (it is the powered console's RAM only); the battery save stays.
     */
    private void persistSave(Session session) {
        if (config.saveMode == RetrocraftConfig.SaveMode.NONE) return;
        try {
            boolean global = config.saveMode == RetrocraftConfig.SaveMode.GLOBAL;
            Path battery = store.directory(session.data.id()).resolve("battery.sav");
            if (session.poweredOff && Files.isRegularFile(battery))
                saves.persistBattery(session.data.owner(), global, session.data.gameId(), battery);
            else saves.persist(session.data.owner(), global, session.data.gameId(), store.snapshot(session.data.id()), !session.poweredOff);
        }
        catch (IOException ex) { Retrocraft.LOG.warn("Cannot persist save for {}", session.data.id()); }
    }

    private static void send(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload packet) { ServerPlayNetworking.send(player, packet); }
    private static void error(ServerPlayerEntity player, String key) { player.sendMessage(Text.translatable("message.retrocraft." + key), false); }

    private void sendCoreSettings(ServerPlayerEntity player) {
        if (roms == null || !preparedCores.keySet().containsAll(requiredCores) || !ServerPlayNetworking.canSend(player, HybridPackets.Settings.ID)) return;
        Integer previous = clientGenerations.put(player.getUuid(), coreGeneration);
        if (previous == null || previous != coreGeneration) {
            readyClients.remove(player.getUuid()); verifiedClients.remove(player.getUuid()); biosReadyClients.remove(player.getUuid());
        }
        send(player, new HybridPackets.Settings(coreGeneration, KnownCores.POOL, config.checkpointIntervalSeconds, config.streamFps,
                 config.thumbnailIntervalSeconds, config.thumbnailUpload, config.emulationMode.ordinal(),
                 requiredCores.stream().sorted().map(id -> preparedCores.get(id).identity()).toList()));
        sendBiosManifest(player);
    }

    private void sendBiosManifest(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, SettingsPackets.BiosManifest.ID) || !biosManifestSent.add(player.getUuid())) return;
        UUID playerId = player.getUuid();
        int generation = coreGeneration;
        Set<String> cores = Set.copyOf(requiredCores);
        Thread.ofVirtual().name("retrocraft-bios-manifest").start(() -> {
            List<SettingsPackets.BiosManifest.Entry> entries = new ArrayList<>();
            for (String id : cores) {
                if (!KnownCores.get(id).needsBios()) continue;
                String directory = id.equals("pcsx2") ? id + "/bios" : id;
                if (!Files.isDirectory(RetrocraftConfig.root().resolve("bios").resolve(directory))) continue;
                try {
                    for (var file : biosLibrary.list(directory).entries()) {
                        if (file.directory() || !RomLibrary.safeName(file.name()) || file.size() < 1 || file.size() > 8L * 1024 * 1024) continue;
                        Path path = biosLibrary.read(directory + "/" + file.name());
                        entries.add(new SettingsPackets.BiosManifest.Entry(id, file.name(), file.size(), InstanceStore.sha256(path)));
                    }
                } catch (IOException ex) { Retrocraft.LOG.warn("Cannot list BIOS for {}", id, ex); }
            }
            callbacks.add(() -> {
                ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                if (current == null || !biosManifestSent.contains(playerId) || !requiredCores.equals(cores) || coreGeneration != generation) return;
                for (int i = 0; i < entries.size(); i += 128)
                    send(current, new SettingsPackets.BiosManifest(generation, i == 0, i + 128 >= entries.size(),
                            entries.subList(i, Math.min(i + 128, entries.size()))));
                if (entries.isEmpty()) send(current, new SettingsPackets.BiosManifest(generation, true, true, List.of()));
            });
        });
    }

    public void biosReady(ServerPlayerEntity player, SettingsPackets.BiosReady packet) {
        if (!Objects.equals(clientGenerations.get(player.getUuid()), packet.generation())) return;
        biosReadyClients.add(player.getUuid());
        markClientReady(player);
    }

    private void markClientReady(ServerPlayerEntity player) {
        if (!verifiedClients.contains(player.getUuid()) || !biosReadyClients.contains(player.getUuid()) || !readyClients.add(player.getUuid())) return;
        Retrocraft.LOG.info("Client {} ready for hybrid emulation: {}", player.getGameProfile().getName(), requiredCores);
    }

    public void requestBios(ServerPlayerEntity player, SettingsPackets.BiosRequest request) {
        if (!clientGenerations.containsKey(player.getUuid()) || !requiredCores.contains(request.coreId())
                || !KnownCores.get(request.coreId()).needsBios() || !RomLibrary.safeName(request.name())
                || !request.sha256().matches("[0-9a-f]{64}")) return;
        UUID playerId = player.getUuid();
        Set<String> pending = biosPending.computeIfAbsent(playerId, ignored -> new HashSet<>());
        String key = request.coreId() + "/" + request.name() + "/" + request.sha256();
        if (pending.size() >= 2 || !pending.add(key)) return;
        Thread.ofVirtual().name("retrocraft-bios-read").start(() -> {
            try {
                Path path = biosLibrary.read(request.coreId() + (request.coreId().equals("pcsx2") ? "/bios/" : "/") + request.name());
                if (Files.size(path) < 1 || Files.size(path) > 8L * 1024 * 1024) throw new IOException("Invalid BIOS size");
                byte[] bytes = Files.readAllBytes(path);
                if (!BlobTransfer.hash(bytes).equals(request.sha256())) throw new IOException("BIOS changed after manifest");
                callbacks.add(() -> {
                    if (biosPending.getOrDefault(playerId, Set.of()).contains(key))
                        biosOutgoing.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).add(new BiosOutgoing(request.coreId(), request.name(), request.sha256(), bytes));
                });
            } catch (IOException ex) {
                Retrocraft.LOG.warn("Cannot send BIOS {} for {}", request.name(), request.coreId(), ex);
                callbacks.add(() -> {
                    biosPending.getOrDefault(playerId, Set.of()).remove(key);
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null && ServerPlayNetworking.canSend(current, SettingsPackets.BiosUnavailable.ID))
                        send(current, new SettingsPackets.BiosUnavailable(request.coreId(), request.name(), request.sha256()));
                });
            }
        });
    }

    private void flushBios() {
        for (var entry : List.copyOf(biosOutgoing.entrySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !ServerPlayNetworking.canSend(player, SettingsPackets.BiosChunk.ID)) continue;
            ArrayDeque<BiosOutgoing> queue = entry.getValue();
            for (int i = 0; i < 8 && !queue.isEmpty(); i++) {
                BiosOutgoing file = queue.peek();
                int end = Math.min(file.bytes.length, file.offset + BlobTransfer.CHUNK);
                send(player, new SettingsPackets.BiosChunk(file.core, file.name, file.offset, file.bytes.length, file.sha,
                        Arrays.copyOfRange(file.bytes, file.offset, end)));
                file.offset = end;
                if (end == file.bytes.length) {
                    queue.removeFirst();
                    biosPending.getOrDefault(entry.getKey(), Set.of()).remove(file.core + "/" + file.name + "/" + file.sha);
                }
            }
            if (queue.isEmpty()) biosOutgoing.remove(entry.getKey());
        }
    }

    public void coreStatus(ServerPlayerEntity player, HybridPackets.Status packet) {
        if (!Objects.equals(clientGenerations.get(player.getUuid()), packet.generation())) return;
        readyClients.remove(player.getUuid()); verifiedClients.remove(player.getUuid());
        if (packet.phase() != HybridPackets.READY || packet.cores().size() != requiredCores.size()) return;
        Map<String, CoreIdentity> reported = new HashMap<>();
        for (var identity : packet.cores()) if (reported.put(identity.id(), identity) != null) return;
        for (String id : requiredCores) if (!preparedCores.get(id).identity().compatible(reported.get(id) == null
                ? new CoreIdentity("", "", "", "", "") : reported.get(id))) return;
        verifiedClients.add(player.getUuid());
        markClientReady(player);
    }

    private boolean controlsHere(Session session, ServerPlayerEntity player) {
        InputSource first = session.pads[0];
        if (player == null || first == null || !first.player.equals(player.getUuid()) || !session.host.canAccess(player)) return false;
        return session.data.id().equals(videoWatching.get(player.getUuid()));
    }

    private void coordinateOwnership(Session session, long now) {
        if (session.destroy || session.failure != null) return;
        if (session.ownership.serverMayRun() && session.pads[0] != null && clientMayEmulate() && clientFits(session)) {
            var player = server.getPlayerManager().getPlayer(session.pads[0].player);
            if (controlsHere(session, player) && readyClients.contains(player.getUuid()) && !player.getUuid().equals(session.failedOffload)) {
                if (session.thread == null && !Files.exists(store.snapshot(session.data.id()))) {
                    if (activeCount() >= config.maxActiveInstances) return;
                    start(session); // initialize and serialize even before the first observed frame
                }
                session.ownership.offer(player.getUuid(), now, TimeUnit.SECONDS.toNanos(config.transferTimeoutSeconds));
                Retrocraft.LOG.info("Instance {} preparing ownership epoch {} for {}", session.data.id(), session.ownership.epoch(), player.getGameProfile().getName());
                session.handoffStop = true; session.offerSent = false; session.romRequested = false; session.romReady = false;
                session.handoffStarted = now; session.romBytesSent = 0; session.media.clear(); resetVideo(session);
                session.relayDecoder = new FrameCodec.Decoder(); session.lastClientAudio = 0; session.lastClientVideo = 0;
            }
        }
        if (session.ownership.mode() != Ownership.Mode.PREPARING_CLIENT || session.thread != null || session.offerSent) return;
        UUID playerId = session.ownership.owner(); long epoch = session.ownership.epoch();
        session.offerSent = true;
        Thread.ofVirtual().name("retrocraft-handoff-read").start(() -> {
            try {
                byte[] snapshot = Files.readAllBytes(store.snapshot(session.data.id()));
                SaveStates.validate(snapshot);
                byte[] packed = SnapshotWire.pack(snapshot);
                callbacks.add(() -> {
                    if (!session.ownership.matches(playerId, epoch) || session.ownership.mode() != Ownership.Mode.PREPARING_CLIENT) return;
                    var player = server.getPlayerManager().getPlayer(playerId);
                    if (!controlsHere(session, player)) { revoke(session, false); return; }
                    long timeout = BlobTransfer.timeoutSeconds(config.transferTimeoutSeconds, packed.length, 4);
                    if (!session.ownership.preparationTime(playerId, epoch, System.nanoTime(), TimeUnit.SECONDS.toNanos(timeout))) { revoke(session, true); return; }
                    session.checkpointBytes = packed.length;
                    send(player, new HybridPackets.Lease(session.data.id(), epoch, HybridPackets.Action.OFFER,
                            config.coreId(session.game.cartridge().id().toString()), session.data.gameId(), session.game.name(), session.game.filename(), session.host.definition().type() == ConsoleType.DOCKED, 0));
                    var queue = outbound.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
                    queue.add(new BlobTransfer.Sender(session.data.id(), epoch, BlobTransfer.Kind.SNAPSHOT, 0, packed));
                });
            } catch (Exception ex) { callbacks.add(() -> { if (session.ownership.matches(playerId, epoch)) { Retrocraft.LOG.error("Cannot prepare ownership transfer", ex); revoke(session, true); } }); }
        });
    }

    private void sendRom(Session session, UUID playerId, long epoch) {
        Thread.ofVirtual().name("retrocraft-rom-download").start(() -> {
            try {
                byte[] rom = Files.readAllBytes(session.game.materialize());
                if (!BlobTransfer.hash(rom).equals(session.data.gameId())) throw new IOException("ROM changed during handoff");
                callbacks.add(() -> {
                    if (!session.ownership.matches(playerId, epoch) || session.ownership.mode() != Ownership.Mode.PREPARING_CLIENT || session.destroy) return;
                    long budget = BlobTransfer.timeoutSeconds(config.transferTimeoutSeconds, (long) rom.length + session.checkpointBytes, 4);
                    if (!session.ownership.preparationTime(playerId, epoch, System.nanoTime(), TimeUnit.SECONDS.toNanos(budget))) { revoke(session, true); return; }
                    outbound.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).add(new BlobTransfer.Sender(session.data.id(), epoch, BlobTransfer.Kind.ROM, 0, rom));
                });
            } catch (Exception ex) { callbacks.add(() -> { if (session.ownership.matches(playerId, epoch)) { Retrocraft.LOG.error("Cannot supply requested ROM", ex); revoke(session, true); } }); }
        });
    }

    public void ownership(ServerPlayerEntity player, HybridPackets.Lease packet) {
        Session session = sessions.get(packet.instance());
        if (session == null || session.destroy || !session.ownership.matches(player.getUuid(), packet.epoch())) return;
        long now = System.nanoTime();
        switch (packet.action()) {
            case ROM_REQUEST -> {
                if (session.ownership.mode() != Ownership.Mode.PREPARING_CLIENT || !session.offerSent || session.romRequested || session.romReady || !controlsHere(session, player)) return;
                session.romRequested = true; sendRom(session, player.getUuid(), packet.epoch());
            }
            case ROM_READY -> {
                if (session.ownership.mode() == Ownership.Mode.PREPARING_CLIENT && session.offerSent) session.romReady = true;
            }
            case PREPARED -> {
                if (!readyClients.contains(player.getUuid()) || !controlsHere(session, player) || session.thread != null || !session.offerSent || !session.romReady) return;
                if (session.ownership.commit(player.getUuid(), packet.epoch(), now, TimeUnit.SECONDS.toNanos(config.ownerTimeoutSeconds))) {
                    session.lastCheckpointAt = now;
                    Retrocraft.LOG.info("Instance {} committed client ownership epoch {} in {} ms (ROM cache {}, {} ROM bytes sent)", session.data.id(), packet.epoch(), (now - session.handoffStarted) / 1_000_000, session.romRequested ? "miss" : "hit", session.romBytesSent);
                    session.media.clear(); resetVideo(session);
                    send(player, HybridPackets.Lease.signal(session.data.id(), packet.epoch(), HybridPackets.Action.COMMIT, 0));
                    sendDemand(session);
                    send(player, new Packets.AudioStop(session.data.id()));
                    notifyPlayers(session);
                }
            }
            case HEARTBEAT -> { if (controlsHere(session, player)) session.ownership.heartbeat(player.getUuid(), packet.epoch(), now, TimeUnit.SECONDS.toNanos(config.ownerTimeoutSeconds)); }
            case RETURN_BEGIN -> {
                if (session.ownership.beginReturn(player.getUuid(), packet.epoch(), now, returnTimeout(session))) releaseInput(player.getUuid());
            }
            case FAILED -> revoke(session, true);
            default -> { }
        }
    }

    private void sendDemand(Session session) {
        UUID owner = session.ownership.owner();
        if (owner == null || session.ownership.mode() != Ownership.Mode.CLIENT) return;
        boolean audio = session.audioWatchers.keySet().stream().anyMatch(id -> !id.equals(owner));
        boolean video = session.host.definition().type() == ConsoleType.DOCKED || session.videoWatchers.keySet().stream().anyMatch(id -> !id.equals(owner));
        AudioWatcher listener = session.audioWatchers.get(owner);
        float gain = listener == null ? 0 : listener.gain;
        if (session.demandEpoch == session.ownership.epoch() && audio == session.relayAudio && video == session.relayVideo && Math.abs(gain - session.ownerGain) < .02f) return;
        var player = server.getPlayerManager().getPlayer(owner);
        if (player == null) return;
        send(player, new HybridPackets.Demand(session.data.id(), session.ownership.epoch(), audio, video, gain));
        session.demandEpoch = session.ownership.epoch(); session.relayAudio = audio; session.relayVideo = video; session.ownerGain = gain;
    }

    private long returnTimeout(Session session) {
        return TimeUnit.SECONDS.toNanos(BlobTransfer.timeoutSeconds(config.transferTimeoutSeconds, session.checkpointBytes, 2));
    }
    private void returnOwnership(Session session) {
        UUID playerId = session.ownership.owner();
        if (playerId == null) return;
        if (session.ownership.mode() == Ownership.Mode.PREPARING_CLIENT) { revoke(session, false); return; }
        if (session.ownership.beginReturn(playerId, session.ownership.epoch(), System.nanoTime(), returnTimeout(session))) {
            var player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) revoke(session, false);
            else send(player, HybridPackets.Lease.signal(session.data.id(), session.ownership.epoch(), HybridPackets.Action.RETURN_REQUEST, 0));
        }
    }

    private void revoke(Session session, boolean failed) {
        UUID playerId = session.ownership.owner(); long epoch = session.ownership.epoch();
        if (playerId != null) {
            var player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) send(player, HybridPackets.Lease.signal(session.data.id(), epoch, HybridPackets.Action.REVOKE, 0));
            var queue = outbound.get(playerId);
            if (queue != null) queue.removeIf(sender -> sender.belongsTo(session.data.id(), epoch));
            if (failed) session.failedOffload = playerId;
        }
        session.ownership.revoke(); session.incoming = new ConcurrentHashMap<>(); synchronized (session) { session.uploadQueue.clear(); } session.media.clear(); resetVideo(session);
        notifyPlayers(session);
        Retrocraft.LOG.info("Instance {} returned to server at epoch {}{}", session.data.id(), session.ownership.epoch(), failed ? " (fallback checkpoint)" : "");
    }

    private void flushTransfers() {
        for (UUID playerId : List.copyOf(outbound.keySet())) {
            var player = server.getPlayerManager().getPlayer(playerId);
            var queue = outbound.get(playerId);
            if (player == null) { outbound.remove(playerId); continue; }
            for (int i = 0; i < 4 && !queue.isEmpty(); i++) {
                var sender = queue.getFirst(); var chunk = sender.next(); send(player, new HybridPackets.Blob(chunk));
                if (chunk.kind() == BlobTransfer.Kind.ROM) {
                    Session session = sessions.get(chunk.instance());
                    if (session != null && session.ownership.matches(playerId, chunk.epoch())) session.romBytesSent += chunk.bytes().length;
                }
                if (sender.done()) queue.removeFirst();
            }
            if (queue.isEmpty()) outbound.remove(playerId);
        }
    }

    public void upload(ServerPlayerEntity player, HybridPackets.Blob packet) {
        var chunk = packet.chunk(); Session session = sessions.get(chunk.instance());
        if (session == null || session.destroy || !session.ownership.matches(player.getUuid(), chunk.epoch())) return;
        boolean state = chunk.kind() == BlobTransfer.Kind.SNAPSHOT || chunk.kind() == BlobTransfer.Kind.FINAL_SNAPSHOT;
        if (chunk.kind() == BlobTransfer.Kind.ROM || (!state && !session.ownership.clientMayRun(player.getUuid(), chunk.epoch()))
                || state && !session.ownership.acceptsCheckpoint(player.getUuid(), chunk.epoch(), chunk.sequence())) return;
        if (state && chunk.total() > 0 && chunk.total() <= BlobTransfer.limit(chunk.kind()))
            session.checkpointBytes = Math.max(session.checkpointBytes, chunk.total());
        session.lastIncomingChunk = System.nanoTime();
        synchronized (session) {
            if (session.uploadQueue.size() >= 128) { revoke(session, true); return; }
            session.uploadQueue.addLast(new UploadChunk(player.getUuid(), chunk));
            if (session.decodingUpload) return;
            session.decodingUpload = true;
        }
        Thread.ofVirtual().name("retrocraft-owner-decode").start(() -> decodeUpload(session));
    }

    private void decodeUpload(Session session) {
        while (true) {
            UploadChunk upload;
            synchronized (session) {
                upload = session.uploadQueue.pollFirst();
                if (upload == null) { session.decodingUpload = false; return; }
            }
            var chunk = upload.chunk();
            if (stopping || session.destroy) { synchronized (session) { session.decodingUpload = false; session.uploadQueue.clear(); } return; }
            if (!session.ownership.matches(upload.player(), chunk.epoch())) continue;
            boolean state = chunk.kind() == BlobTransfer.Kind.SNAPSHOT || chunk.kind() == BlobTransfer.Kind.FINAL_SNAPSHOT;
            try {
                Map<BlobTransfer.Kind, BlobTransfer.Receiver> incoming = session.incoming;
                incoming.values().removeIf(receiver -> receiver.expired(System.nanoTime()));
                var receiver = incoming.computeIfAbsent(chunk.kind(), ignored -> new BlobTransfer.Receiver());
                if (receiver.reserved() == 0 && incoming.values().stream().mapToLong(BlobTransfer.Receiver::reserved).sum() + chunk.total() > BlobTransfer.MAX_SNAPSHOT + 2_000_000L)
                    throw new IOException("Owner upload budget exceeded");
                byte[] complete = receiver.accept(chunk, System.nanoTime());
                if (complete == null) continue;
                if (state) callbacks.add(() -> {
                    if (!session.ownership.matches(upload.player(), chunk.epoch())) return;
                    session.checkpointBytes = Math.max(session.checkpointBytes, chunk.total());
                    try { checkpoint(session, upload.player(), chunk, complete); }
                    catch (IOException ex) { Retrocraft.LOG.warn("Rejected owner checkpoint", ex); revoke(session, true); }
                });
                else if (chunk.kind() == BlobTransfer.Kind.VIDEO) {
                    FrameCodec.Decoder decoder = session.relayDecoder;
                    var frame = decoder.decode(MediaWire.video(complete));
                    callbacks.add(() -> {
                        if (!session.ownership.matches(upload.player(), chunk.epoch()) || decoder != session.relayDecoder || chunk.sequence() <= session.lastClientVideo) return;
                        var player = server.getPlayerManager().getPlayer(upload.player());
                        if (frame == null) { if (player != null) send(player, HybridPackets.Lease.signal(session.data.id(), chunk.epoch(), HybridPackets.Action.REQUEST_KEYFRAME, 0)); }
                        else { session.lastClientVideo = chunk.sequence(); session.media.offer(new WorkerProcess.Output(frame, new byte[0]), session.sampleRate); }
                    });
                } else if (chunk.kind() == BlobTransfer.Kind.AUDIO) {
                    var audio = MediaWire.audio(complete);
                    callbacks.add(() -> {
                        if (!session.ownership.matches(upload.player(), chunk.epoch()) || chunk.sequence() <= session.lastClientAudio) return;
                        session.sampleRate = audio.rate(); session.lastClientAudio = chunk.sequence();
                        session.media.offer(new WorkerProcess.Output(null, audio.pcm()), audio.rate());
                    });
                }
            } catch (IOException | RuntimeException ex) {
                callbacks.add(() -> {
                    if (session.ownership.matches(upload.player(), chunk.epoch())) { Retrocraft.LOG.warn("Rejected owner upload: {}", ex.toString()); revoke(session, true); }
                });
                synchronized (session) { session.decodingUpload = false; session.uploadQueue.clear(); }
                return;
            }
        }
    }

    private void checkpoint(Session session, UUID player, BlobTransfer.Chunk chunk, byte[] bytes) throws IOException {
        String writeId = chunk.epoch() + ":" + chunk.sequence();
        if (session.pendingCheckpointWrites.size() >= 2 || !session.pendingCheckpointWrites.add(writeId)) throw new IOException("Checkpoint already pending or write budget exhausted");
        Path staged = store.directory(session.data.id()).resolve("received-" + chunk.epoch() + "-" + chunk.sequence() + ".bin");
        session.checkpointWrites.incrementAndGet();
        Thread.ofVirtual().name("retrocraft-checkpoint-write").start(() -> {
            try {
                byte[] state = SnapshotWire.unpack(bytes); SaveStates.validate(state);
                SaveStates.writeAtomic(staged, state);
                if (stopping || session.destroy) { Files.deleteIfExists(staged); return; }
                callbacks.add(() -> {
                    session.pendingCheckpointWrites.remove(writeId);
                    try {
                        if (session.destroy || !session.ownership.acceptsCheckpoint(player, chunk.epoch(), chunk.sequence())) { Files.deleteIfExists(staged); return; }
                        store.promote(session.data.id(), staged);
                        session.ownership.checkpoint(player, chunk.epoch(), chunk.sequence());
                        session.lastCheckpointAt = System.nanoTime();
                        var target = server.getPlayerManager().getPlayer(player);
                        if (target != null) send(target, HybridPackets.Lease.signal(session.data.id(), chunk.epoch(), HybridPackets.Action.CHECKPOINT_ACK, chunk.sequence()));
                        if (chunk.kind() == BlobTransfer.Kind.FINAL_SNAPSHOT) {
                            // A newly opened HUD may already have claimed input during the return.
                            revoke(session, false); notifyRoles(session);
                        }
                    } catch (IOException ex) { Retrocraft.LOG.error("Cannot commit checkpoint", ex); revoke(session, true); }
                });
            } catch (IOException ex) { callbacks.add(() -> { session.pendingCheckpointWrites.remove(writeId); Retrocraft.LOG.error("Cannot persist owner checkpoint", ex); if (session.ownership.matches(player, chunk.epoch())) revoke(session, true); }); }
            finally { session.checkpointWrites.decrementAndGet(); }
        });
    }

    @Override public void close() {
        stopping = true;
        for (PendingStart pending : pendingStarts.values()) if (pending.created != null) {
            UUID id = pending.created;
            Thread.ofVirtual().name("retrocraft-cancel-power-on").start(() -> destroyFiles(id));
        }
        pendingStarts.clear();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        for (Session session : sessions.values()) {
            if (session.thread == null) continue;
            try { session.thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            if (session.thread.isAlive()) { Retrocraft.LOG.error("Console {} exceeded shutdown deadline", session.data.id()); if (session.process != null) session.process.abort(); session.thread.interrupt(); }
        }
        for (Session session : sessions.values()) if (!session.destroy && session.thread != null && !session.thread.isAlive()) persistSave(session);
        sessions.clear(); videoWatching.clear(); inputSessions.clear(); failedDiscovery.clear(); outbound.clear(); readyClients.clear(); callbacks.clear(); romUploads.clear();
    }
}
