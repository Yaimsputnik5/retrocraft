package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
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
    }
    private static final class Pending {
        final Response response; final long deadline = System.nanoTime() + 20_000_000_000L;
        Pending(Response response) { this.response = response; }
    }

    static void screen(SettingsScreen value) { screen = value; }
    static SettingsScreen screen() { return screen; }
    static int submit(Response response) {
        int id = ++nextRequest;
        pending.put(id, new Pending(response));
        return id;
    }

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
    public static void romEvent(SettingsPackets.RomEvent packet) {
        if (packet.action() == SettingsPackets.UPLOAD_ACCEPT || packet.action() == SettingsPackets.UPLOAD_DONE || packet.action() == SettingsPackets.UPLOAD_FAIL) {
            uploadEvent(packet);
            return;
        }
        var response = pending.remove(packet.request());
        if (response != null) response.response.event(packet);
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
                ClientPlayNetworking.send(new SettingsPackets.RomUpload(upload.id, SettingsPackets.CHUNK, "", "", 0, upload.offset, "", Arrays.copyOfRange(upload.bytes, upload.offset, end)));
                upload.offset = end;
                upload.callback.progress((float) upload.offset / upload.bytes.length);
            }
        }
    }

    private static final class Upload {
        final int id; final byte[] bytes; final Callback callback;
        int offset; boolean accepted;
        Upload(int id, byte[] bytes, Callback callback) { this.id = id; this.bytes = bytes; this.callback = callback; }
    }
    public interface Callback {
        void progress(float fraction);
        void done(boolean ok, String message);
    }

    public static void upload(String dir, String name, byte[] bytes, Callback callback) {
        abortUpload();
        upload = new Upload(++nextUpload, bytes, callback);
        ClientPlayNetworking.send(new SettingsPackets.RomUpload(upload.id, SettingsPackets.BEGIN, dir, name, bytes.length, 0, BlobTransfer.hash(bytes), new byte[0]));
    }
    public static void abortUpload() {
        if (upload == null) return;
        ClientPlayNetworking.send(new SettingsPackets.RomUpload(upload.id, SettingsPackets.ABORT, "", "", 0, 0, "", new byte[0]));
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

    public static void disconnect() {
        pending.clear(); upload = null; screen = null;
    }
    private SettingsClient() { }
}
