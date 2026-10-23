package com.sammwy.retrocraft.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.roms.BiosCache;
import com.sammwy.retrocraft.roms.RomLibrary;
import com.sammwy.retrocraft.roms.SaveStates;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.net.SettingsPackets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client half of the admin channel: routes settings packets to the open screen and pumps upload
 * chunks.
 */
public final class SettingsClient {
    private static SettingsScreen screen;
    private static int nextRequest;
    private static int nextUpload;
    private static final Map<Integer, Pending> pending = new HashMap<>();
    private static Upload upload;

    /**
     * A pending {@link com.sammwy.retrocraft.server.net.SettingsPackets.RomAction}: the server
     * answers with exactly one shape.
     */
    interface Response {
        default void list(SettingsPackets.RomList packet) {}

        default void event(SettingsPackets.RomEvent packet) {}
    }

    private static final class Pending {
        final Response response;
        final long deadline = System.nanoTime() + 20_000_000_000L;

        Pending(Response response) {
            this.response = response;
        }
    }

    static void screen(SettingsScreen value) {
        screen = value;
    }

    static SettingsScreen screen() {
        return screen;
    }

    static int submit(Response response) {
        int id = ++nextRequest;
        pending.put(id, new Pending(response));
        return id;
    }

    static void complete(int request) {
        pending.remove(request);
    }

    public static boolean supported() {
        return ClientPlayNetworking.canSend(SettingsPackets.GuiRequest.ID);
    }

    public static void request() {
        ClientPlayNetworking.send(new SettingsPackets.GuiRequest());
    }

    public static void guiOpen(MinecraftClient client, SettingsPackets.GuiOpen packet) {
        if (screen != null) screen.granted(packet);
        else client.setScreen(SettingsScreen.remote(null, packet));
    }

    public static void configSaved(SettingsPackets.ConfigSaved packet) {
        if (screen != null) screen.configSaved(packet);
    }

    public static void romList(SettingsPackets.RomList packet) {
        var response = pending.remove(packet.request());
        if (response != null) response.response.list(packet);
    }

    public static void romEvent(SettingsPackets.RomEvent packet) {
        if (packet.action() == SettingsPackets.UPLOAD_ACCEPT
                || packet.action() == SettingsPackets.UPLOAD_DONE
                || packet.action() == SettingsPackets.UPLOAD_FAIL) {
            uploadEvent(packet);
            return;
        }
        var response = pending.remove(packet.request());
        if (response != null) response.response.event(packet);
    }

    /** Stale action responses are reported as a timeout so the UI never hangs on a spinner. */
    public static void tick() {
        long now = System.nanoTime();
        var expired = new ArrayList<Integer>();
        for (var entry : pending.entrySet())
            if (now >= entry.getValue().deadline) expired.add(entry.getKey());
        for (int id : expired) {
            var request = pending.remove(id);
            if (request != null)
                request.response.event(SettingsPackets.RomEvent.fail(id, "Timed out"));
        }
        if (upload != null && now - upload.lastActivity > 60_000_000_000L) {
            var timedOut = upload;
            abortUpload();
            timedOut.callback.done(false, "Upload timed out");
        }
        if (upload != null && upload.accepted) {
            for (int i = 0; i < 8 && upload.offset < upload.bytes.length; i++) {
                int end = Math.min(upload.bytes.length, upload.offset + BlobTransfer.CHUNK);
                ClientPlayNetworking.send(
                        new SettingsPackets.RomUpload(
                                upload.id,
                                SettingsPackets.CHUNK,
                                upload.area,
                                "",
                                "",
                                0,
                                upload.offset,
                                "",
                                Arrays.copyOfRange(upload.bytes, upload.offset, end)));
                upload.offset = end;
                upload.lastActivity = now;
                upload.callback.progress((float) upload.offset / upload.bytes.length);
            }
        }
    }

    private static final class Upload {
        final int id;
        final int area;
        final byte[] bytes;
        final Callback callback;
        int offset;
        boolean accepted;
        long lastActivity = System.nanoTime();

        Upload(int id, int area, byte[] bytes, Callback callback) {
            this.id = id;
            this.area = area;
            this.bytes = bytes;
            this.callback = callback;
        }
    }

    public interface Callback {
        void progress(float fraction);

        void done(boolean ok, String message);
    }

    public static void upload(String dir, String name, byte[] bytes, Callback callback) {
        upload(SettingsPackets.AREA_ROMS, dir, name, bytes, callback);
    }

    public static void upload(int area, String dir, String name, byte[] bytes, Callback callback) {
        abortUpload();
        upload = new Upload(++nextUpload, area, bytes, callback);
        ClientPlayNetworking.send(
                new SettingsPackets.RomUpload(
                        upload.id,
                        SettingsPackets.BEGIN,
                        area,
                        dir,
                        name,
                        bytes.length,
                        0,
                        BlobTransfer.hash(bytes),
                        new byte[0]));
    }

    public static void abortUpload() {
        if (upload == null) return;
        if (ClientPlayNetworking.canSend(SettingsPackets.RomUpload.ID))
            ClientPlayNetworking.send(
                    new SettingsPackets.RomUpload(
                            upload.id,
                            SettingsPackets.ABORT,
                            upload.area,
                            "",
                            "",
                            0,
                            0,
                            "",
                            new byte[0]));
        upload = null;
    }

    private static void uploadEvent(SettingsPackets.RomEvent packet) {
        if (upload == null || packet.request() != upload.id) return;
        upload.lastActivity = System.nanoTime();
        switch (packet.action()) {
            case SettingsPackets.UPLOAD_ACCEPT -> upload.accepted = true;
            case SettingsPackets.UPLOAD_DONE -> {
                var finished = upload;
                upload = null;
                finished.callback.done(true, packet.message());
            }
            case SettingsPackets.UPLOAD_FAIL -> {
                var failed = upload;
                upload = null;
                if (!"cancelled".equals(packet.message()))
                    failed.callback.done(false, packet.message());
            }
            default -> {}
        }
    }

    private static final class BiosFile {
        final long total;
        final String sha256;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();

        BiosFile(long total, String sha256) {
            this.total = total;
            this.sha256 = sha256;
        }
    }

    private static final Map<String, BiosFile> biosIncoming = new HashMap<>();
    private static final Set<String> biosRequested = new HashSet<>();
    private static final ArrayDeque<SettingsPackets.BiosRequest> biosMissing = new ArrayDeque<>();
    private static volatile long biosEpoch;
    private static int biosGeneration, biosChecks;
    private static boolean biosManifestFinished, biosManifestFailed, biosReadySent;

    public static void biosManifest(SettingsPackets.BiosManifest packet) {
        if (packet.first()) {
            biosEpoch++;
            biosChecks = 0;
            biosGeneration = packet.generation();
            biosManifestFinished = false;
            biosManifestFailed = false;
            biosReadySent = false;
            biosIncoming.clear();
            biosMissing.clear();
            biosRequested.clear();
        } else if (packet.generation() != biosGeneration) return;
        biosChecks++;
        if (packet.last()) biosManifestFinished = true;
        long epoch = biosEpoch;
        Thread.ofVirtual()
                .name("retrocraft-bios-cache-check")
                .start(
                        () -> {
                            List<SettingsPackets.BiosRequest> requests = new ArrayList<>();
                            for (var entry : packet.entries()) {
                                if (!KnownCores.ids().contains(entry.coreId())
                                        || !RomLibrary.safeName(entry.name())
                                        || entry.size() < 1
                                        || entry.size() > 8L * 1024 * 1024
                                        || !entry.sha256().matches("[0-9a-f]{64}")) continue;
                                Path cached =
                                        RetrocraftConfig.biosDir(entry.coreId())
                                                .resolve(
                                                        entry.coreId().equals("pcsx2")
                                                                ? "bios"
                                                                : "")
                                                .resolve(entry.name());
                                if (BiosCache.matches(cached, entry.size(), entry.sha256()))
                                    continue;
                                requests.add(
                                        new SettingsPackets.BiosRequest(
                                                entry.coreId(), entry.name(), entry.sha256()));
                            }
                            MinecraftClient.getInstance()
                                    .execute(
                                            () -> {
                                                if (epoch != biosEpoch) return;
                                                biosChecks--;
                                                for (var request : requests) {
                                                    String key =
                                                            request.coreId()
                                                                    + "/"
                                                                    + request.name()
                                                                    + "/"
                                                                    + request.sha256();
                                                    if (!biosRequested.contains(key)
                                                            && biosMissing.stream()
                                                                    .noneMatch(
                                                                            p -> p.equals(request)))
                                                        biosMissing.add(request);
                                                }
                                                pumpBios();
                                                finishBios();
                                            });
                        });
    }

    private static void pumpBios() {
        while (biosRequested.size() < 2
                && !biosMissing.isEmpty()
                && ClientPlayNetworking.canSend(SettingsPackets.BiosRequest.ID)) {
            var request = biosMissing.removeFirst();
            biosRequested.add(request.coreId() + "/" + request.name() + "/" + request.sha256());
            ClientPlayNetworking.send(request);
        }
    }

    private static void finishBios() {
        if (!biosManifestFinished
                || biosManifestFailed
                || biosReadySent
                || biosChecks != 0
                || !biosMissing.isEmpty()
                || !biosRequested.isEmpty()
                || !ClientPlayNetworking.canSend(SettingsPackets.BiosReady.ID)) return;
        biosReadySent = true;
        ClientPlayNetworking.send(new SettingsPackets.BiosReady(biosGeneration));
    }

    public static void biosUnavailable(SettingsPackets.BiosUnavailable packet) {
        if (!biosRequested.contains(packet.coreId() + "/" + packet.name() + "/" + packet.sha256()))
            return;
        biosManifestFailed = true;
        biosIncoming.remove(packet.coreId() + "/" + packet.name());
        biosRequested.remove(packet.coreId() + "/" + packet.name() + "/" + packet.sha256());
        pumpBios();
    }

    private static String scanActor = "";
    private static boolean scanning;

    public static boolean scanning() {
        return scanning;
    }

    public static String scanActor() {
        return scanActor;
    }

    public static void scanEvent(SettingsPackets.ScanEvent packet) {
        scanning = packet.scanning();
        if (!packet.actor().isBlank()) scanActor = packet.actor();
        if (!scanning && screen != null) screen.scanFinished();
    }

    public static void biosChunk(SettingsPackets.BiosChunk packet) {
        String key = packet.coreId() + "/" + packet.name();
        if (!biosRequested.contains(key + "/" + packet.sha256())) return;
        try {
            if (!RomLibrary.safeName(packet.name())
                    || packet.total() < 1
                    || packet.total() > 8L * 1024 * 1024
                    || packet.offset() < 0) return;
            BiosFile file =
                    biosIncoming.computeIfAbsent(
                            key, k -> new BiosFile(packet.total(), packet.sha256()));
            if (file.total != packet.total()
                    || !file.sha256.equals(packet.sha256())
                    || packet.offset() != file.data.size()
                    || file.data.size() + packet.data().length > file.total) {
                biosIncoming.remove(key);
                return;
            }
            file.data.writeBytes(packet.data());
            if (file.data.size() < file.total) return;
            biosIncoming.remove(key);
            byte[] bytes = file.data.toByteArray();
            long epoch = biosEpoch;
            Thread.ofVirtual()
                    .name("retrocraft-bios-write")
                    .start(
                            () -> {
                                try {
                                    if (!BlobTransfer.hash(bytes).equals(file.sha256)) {
                                        MinecraftClient.getInstance()
                                                .execute(
                                                        () -> {
                                                            if (epoch == biosEpoch)
                                                                biosManifestFailed = true;
                                                        });
                                        return;
                                    }
                                    if (epoch != biosEpoch) return;
                                    Path target =
                                            RetrocraftConfig.biosDir(packet.coreId())
                                                    .resolve(
                                                            packet.coreId().equals("pcsx2")
                                                                    ? "bios"
                                                                    : "")
                                                    .resolve(packet.name());
                                    SaveStates.writeAtomic(target, bytes);
                                    Retrocraft.LOG.info(
                                            "Received BIOS {} for core {} from server",
                                            packet.name(),
                                            packet.coreId());
                                } catch (IOException ex) {
                                    Retrocraft.LOG.warn(
                                            "Cannot cache BIOS {} for {}",
                                            packet.name(),
                                            packet.coreId(),
                                            ex);
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () -> {
                                                        if (epoch == biosEpoch)
                                                            biosManifestFailed = true;
                                                    });
                                } finally {
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () -> {
                                                        if (epoch != biosEpoch) return;
                                                        biosRequested.remove(
                                                                packet.coreId()
                                                                        + "/"
                                                                        + packet.name()
                                                                        + "/"
                                                                        + packet.sha256());
                                                        pumpBios();
                                                        finishBios();
                                                    });
                                }
                            });
        } catch (RuntimeException ex) {
            biosIncoming.remove(key);
        }
    }

    public static void disconnect() {
        biosEpoch++;
        pending.clear();
        upload = null;
        screen = null;
        biosIncoming.clear();
        biosRequested.clear();
        biosMissing.clear();
        biosChecks = 0;
        biosManifestFinished = false;
        biosManifestFailed = false;
        biosReadySent = false;
        scanning = false;
        scanActor = "";
    }

    private SettingsClient() {}
}
