package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.roms.RomLibrary;
import com.sammwy.retrocraft.roms.SaveStates;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;

/** Client half of the admin channel: routes settings packets to the open screen and pumps upload chunks. */
public final class SettingsClient {
    private static SettingsScreen screen;
    private static int nextRequest;
    private static int nextUpload;
    private static final Map<Integer, Pending> pending = new HashMap<>();
    private static Upload upload;

    /** A pending {@link com.sammwy.retrocraft.server.net.SettingsPackets.RomAction}: the server answers with exactly one shape. */
    interface Response {
        default void list(SettingsPackets.RomList packet) { }
        default void event(SettingsPackets.RomEvent packet) { }
        default void core(SettingsPackets.CoreList packet) { }
    }
    private static final class Pending {
        final Response response; final long deadline = System.nanoTime() + 20_000_000_000L;
        boolean sticky;
        Pending(Response response) { this.response = response; }
    }

    static void screen(SettingsScreen value) { screen = value; }
    static SettingsScreen screen() { return screen; }
    static int submit(Response response) {
        int id = ++nextRequest;
        pending.put(id, new Pending(response));
        return id;
    }
    /** Mutations that yield several CoreList pushes before the final RomEvent. */
    static int submitSticky(Response response) {
        int id = submit(response);
        pending.get(id).sticky = true;
        return id;
    }
    static void complete(int request) { pending.remove(request); }

    public static boolean supported() { return ClientPlayNetworking.canSend(SettingsPackets.GuiRequest.ID); }
    public static void request() { ClientPlayNetworking.send(new SettingsPackets.GuiRequest()); }

    public static void guiOpen(MinecraftClient client, SettingsPackets.GuiOpen packet) {
        if (screen != null) screen.granted(packet);
        else client.setScreen(SettingsScreen.remote(null, packet));
    }
    public static void configSaved(SettingsPackets.ConfigSaved packet) { if (screen != null) screen.configSaved(packet); }
    public static void romList(SettingsPackets.RomList packet) {
        var response = pending.remove(packet.request());
        if (response != null) response.response.list(packet);
    }
    public static void coreList(SettingsPackets.CoreList packet) {
        var response = pending.get(packet.request());
        if (response != null) { if (!response.sticky) pending.remove(packet.request()); response.response.core(packet); }
    }
    public static void romEvent(SettingsPackets.RomEvent packet) {
        if (packet.action() == SettingsPackets.UPLOAD_ACCEPT || packet.action() == SettingsPackets.UPLOAD_DONE || packet.action() == SettingsPackets.UPLOAD_FAIL) {
            uploadEvent(packet);
            return;
        }
        var response = pending.get(packet.request());
        if (response != null) { if (!response.sticky) pending.remove(packet.request()); response.response.event(packet); }
    }

    /** Stale action responses are reported as a timeout so the UI never hangs on a spinner. */
    public static void tick() {
        long now = System.nanoTime();
        for (var it = pending.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (now < entry.getValue().deadline) continue;
            it.remove();
            entry.getValue().response.event(SettingsPackets.RomEvent.fail(entry.getKey(), "Timed out"));
        }
        if (upload != null && upload.accepted) {
            for (int i = 0; i < 8 && upload.offset < upload.bytes.length; i++) {
                int end = Math.min(upload.bytes.length, upload.offset + BlobTransfer.CHUNK);
                ClientPlayNetworking.send(new SettingsPackets.RomUpload(upload.id, SettingsPackets.CHUNK, upload.area, "", "", 0, upload.offset, "", Arrays.copyOfRange(upload.bytes, upload.offset, end)));
                upload.offset = end;
                upload.callback.progress((float) upload.offset / upload.bytes.length);
            }
        }
    }

    private static final class Upload {
        final int id; final int area; final byte[] bytes; final Callback callback;
        int offset; boolean accepted;
        Upload(int id, int area, byte[] bytes, Callback callback) { this.id = id; this.area = area; this.bytes = bytes; this.callback = callback; }
    }
    public interface Callback {
        void progress(float fraction);
        void done(boolean ok, String message);
    }

    public static void upload(String dir, String name, byte[] bytes, Callback callback) { upload(SettingsPackets.AREA_ROMS, dir, name, bytes, callback); }
    public static void upload(int area, String dir, String name, byte[] bytes, Callback callback) {
        abortUpload();
        upload = new Upload(++nextUpload, area, bytes, callback);
        ClientPlayNetworking.send(new SettingsPackets.RomUpload(upload.id, SettingsPackets.BEGIN, area, dir, name, bytes.length, 0, BlobTransfer.hash(bytes), new byte[0]));
    }
    public static void abortUpload() {
        if (upload == null) return;
        ClientPlayNetworking.send(new SettingsPackets.RomUpload(upload.id, SettingsPackets.ABORT, upload.area, "", "", 0, 0, "", new byte[0]));
        upload = null;
    }
    private static void uploadEvent(SettingsPackets.RomEvent packet) {
        if (upload == null || packet.request() != upload.id) return;
        switch (packet.action()) {
            case SettingsPackets.UPLOAD_ACCEPT -> upload.accepted = true;
            case SettingsPackets.UPLOAD_DONE -> { var finished = upload; upload = null; finished.callback.done(true, packet.message()); }
            case SettingsPackets.UPLOAD_FAIL -> { var failed = upload; upload = null; if (!"cancelled".equals(packet.message())) failed.callback.done(false, packet.message()); }
            default -> { }
        }
    }


    private static final class BiosFile {
        final long total; final String sha256;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        BiosFile(long total, String sha256) { this.total = total; this.sha256 = sha256; }
    }
    private static final Map<String, BiosFile> biosIncoming = new HashMap<>();

    public static void biosChunk(SettingsPackets.BiosChunk packet) {
        String key = packet.coreId() + "/" + packet.name();
        try {
            if (!RomLibrary.safeName(packet.name())
                    || packet.total() < 1 || packet.total() > 8L * 1024 * 1024 || packet.offset() < 0) return;
            BiosFile file = biosIncoming.computeIfAbsent(key, k -> new BiosFile(packet.total(), packet.sha256()));
            if (file.total != packet.total() || packet.offset() != file.data.size()
                    || file.data.size() + packet.data().length > file.total) { biosIncoming.remove(key); return; }
            file.data.writeBytes(packet.data());
            if (file.data.size() < file.total) return;
            biosIncoming.remove(key);
            byte[] bytes = file.data.toByteArray();
            if (!BlobTransfer.hash(bytes).equals(file.sha256)) return;
            Path target = RetrocraftConfig.biosDir(packet.coreId()).resolve(packet.name());
            Files.createDirectories(target.getParent());
            SaveStates.writeAtomic(target, bytes);
            Retrocraft.LOG.info("Received BIOS {} for core {} from server", packet.name(), packet.coreId());
        } catch (java.io.IOException | RuntimeException ex) { biosIncoming.remove(key); }
    }

    public static void disconnect() {
        pending.clear(); upload = null; screen = null; biosIncoming.clear();
    }
    private SettingsClient() { }
}
