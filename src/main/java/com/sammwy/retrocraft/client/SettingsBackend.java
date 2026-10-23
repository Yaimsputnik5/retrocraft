package com.sammwy.retrocraft.client;

import com.google.gson.Gson;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.CoreManager;
import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.cores.WorkerRuntime;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.roms.RomLibrary;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * What the settings screen talks to. On a remote server every call is a packet; in single
 * player or from the title screen the same widgets edit the local config/roms directly.
 */
interface SettingsBackend {
    Text scope();
    List<SettingsPackets.GuiOpen.Entry> schema();
    void apply(List<SettingsPackets.ConfigApply.Change> changes, ApplyCallback callback);
    void list(String path, ListCallback callback);
    void action(int action, String path, String target, EventCallback callback);
    void upload(Path file, String dir, UploadCallback callback);
    void biosList(String coreId, ListCallback callback);
    void biosDelete(String coreId, String name, EventCallback callback);
    void coreUpload(Path file, String coreId, UploadCallback callback);
    void close();

    interface ApplyCallback { void result(boolean ok, String message, List<SettingsPackets.GuiOpen.Entry> entries); }
    interface ListCallback { void result(boolean ok, List<SettingsPackets.RomList.Entry> entries, boolean truncated, String error); }
    interface EventCallback { void result(boolean ok, String message); }
    interface UploadCallback {
        void progress(float fraction);
        default void done(boolean ok, String message) { }
    }

    /** Packet-backed backend; the server re-checks operator permission on every request. */
    final class Remote implements SettingsBackend {
        private List<SettingsPackets.GuiOpen.Entry> schema;
        private ApplyCallback pendingApply;
        Remote(SettingsPackets.GuiOpen grant) { schema = grant.entries(); }

        @Override public Text scope() { return Text.translatable("settings.retrocraft.scope.server"); }
        @Override public List<SettingsPackets.GuiOpen.Entry> schema() { return schema; }

        @Override public void apply(List<SettingsPackets.ConfigApply.Change> changes, ApplyCallback callback) {
            pendingApply = callback;
            ClientPlayNetworking.send(new SettingsPackets.ConfigApply(changes));
        }
        /** Routed from the packet receiver through the screen. */
        void saved(SettingsPackets.ConfigSaved packet) {
            var callback = pendingApply; pendingApply = null;
            schema = packet.entries();
            if (callback != null) callback.result(packet.ok(), packet.message(), packet.entries());
        }

        @Override public void list(String path, ListCallback callback) {
            int id = SettingsClient.submit(new SettingsClient.Response() {
                @Override public void list(SettingsPackets.RomList packet) { callback.result(true, packet.entries(), packet.truncated(), null); }
                @Override public void event(SettingsPackets.RomEvent packet) { callback.result(false, List.of(), false, packet.message()); }
            });
            ClientPlayNetworking.send(SettingsPackets.RomAction.of(id, SettingsPackets.LIST, path));
        }

        @Override public void action(int action, String path, String target, EventCallback callback) {
            int id = SettingsClient.submit(new SettingsClient.Response() {
                @Override public void event(SettingsPackets.RomEvent packet) { callback.result(packet.ok(), packet.message()); }
            });
            ClientPlayNetworking.send(new SettingsPackets.RomAction(id, action, SettingsPackets.AREA_ROMS, path, target));
        }

        @Override public void upload(Path file, String dir, UploadCallback callback) {
            Thread.ofVirtual().name("retrocraft-rom-read").start(() -> {
                try {
                    long size = Files.size(file);
                    if (size < 1 || size > BlobTransfer.MAX_ROM) throw new IOException("File outside the ROM size limit");
                    byte[] bytes = Files.readAllBytes(file);
                    String name = file.getFileName().toString();
                    MinecraftClient.getInstance().execute(() -> SettingsClient.upload(dir, name, bytes, new SettingsClient.Callback() {
                        @Override public void progress(float fraction) { callback.progress(fraction); }
                        @Override public void done(boolean ok, String message) { callback.done(ok, message); }
                    }));
                } catch (IOException | RuntimeException ex) {
                    String message = ex.getMessage() == null ? "Could not read the file" : ex.getMessage();
                    MinecraftClient.getInstance().execute(() -> callback.done(false, message));
                }
            });
        }

        @Override public void biosList(String coreId, ListCallback callback) {
            int id = SettingsClient.submit(new SettingsClient.Response() {
                @Override public void list(SettingsPackets.RomList packet) { callback.result(true, packet.entries(), packet.truncated(), null); }
                @Override public void event(SettingsPackets.RomEvent packet) { callback.result(false, List.of(), false, packet.message()); }
            });
            ClientPlayNetworking.send(new SettingsPackets.RomAction(id, SettingsPackets.LIST, SettingsPackets.AREA_BIOS, coreId, ""));
        }

        @Override public void biosDelete(String coreId, String name, EventCallback callback) {
            int id = SettingsClient.submit(new SettingsClient.Response() {
                @Override public void event(SettingsPackets.RomEvent packet) { callback.result(packet.ok(), packet.message()); }
            });
            ClientPlayNetworking.send(new SettingsPackets.RomAction(id, SettingsPackets.DELETE, SettingsPackets.AREA_BIOS, coreId + "/" + name, ""));
        }

        @Override public void coreUpload(Path file, String coreId, UploadCallback callback) {
            Thread.ofVirtual().name("retrocraft-bios-read").start(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    String name = file.getFileName().toString();
                    MinecraftClient.getInstance().execute(() -> SettingsClient.upload(SettingsPackets.AREA_BIOS, coreId, name, bytes, new SettingsClient.Callback() {
                        @Override public void progress(float fraction) { callback.progress(fraction); }
                        @Override public void done(boolean ok, String message) { callback.done(ok, message); }
                    }));
                } catch (IOException | RuntimeException ex) {
                    String message = ex.getMessage() == null ? "Could not read the file" : ex.getMessage();
                    MinecraftClient.getInstance().execute(() -> callback.done(false, message));
                }
            });
        }

        @Override public void close() { SettingsClient.abortUpload(); }
    }

    /** Single-player / title-screen backend: edits config/retrocraft on this machine, live-applied when hosting. */
    final class Local implements SettingsBackend {
        private final RomLibrary library, biosLibrary;
        private final RetrocraftConfig config;
        private List<SettingsPackets.GuiOpen.Entry> schema;

        Local() throws IOException {
            config = RetrocraftConfig.load();
            library = new RomLibrary(RetrocraftConfig.root().resolve("roms"));
            biosLibrary = new RomLibrary(RetrocraftConfig.root().resolve("bios"));
            schema = SettingsPackets.GuiOpen.schema(config);
        }

        @Override public Text scope() { return Text.translatable("settings.retrocraft.scope.local"); }
        @Override public List<SettingsPackets.GuiOpen.Entry> schema() { return schema; }

        @Override public void apply(List<SettingsPackets.ConfigApply.Change> changes, ApplyCallback callback) {
            String error = null;
            var gson = new Gson();
            RetrocraftConfig probe = gson.fromJson(gson.toJson(config), RetrocraftConfig.class);
            for (var change : changes) {
                try { ConfigKey.set(probe, change.key(), change.value()); }
                catch (IllegalArgumentException ex) { error = change.key() + ": " + ex.getMessage(); break; }
            }
            if (error == null) {
                try {
                    for (var change : changes) ConfigKey.set(config, change.key(), change.value());
                    config.save();
                    applyLive(changes);
                } catch (IOException | RuntimeException ex) { error = ex.getMessage() == null ? "Could not save the config file" : ex.getMessage(); }
            }
            schema = SettingsPackets.GuiOpen.schema(config);
            callback.result(error == null, error == null ? "" : error, schema);
        }
        private static void applyLive(List<SettingsPackets.ConfigApply.Change> changes) {
            var server = MinecraftClient.getInstance().getServer();
            var instances = Retrocraft.instances();
            if (server == null || instances == null) return;
            server.execute(() -> {
                for (var change : changes) {
                    try { ConfigKey.set(instances.config(), change.key(), change.value()); } catch (IllegalArgumentException ignored) { }
                }
                instances.applyConfig();
            });
        }

        @Override public void list(String path, ListCallback callback) {
            try {
                var listing = library.list(path);
                callback.result(true, listing.entries().stream().map(e -> new SettingsPackets.RomList.Entry(e.name(), e.directory(), e.size())).toList(), listing.truncated(), null);
            } catch (IOException | RuntimeException ex) { callback.result(false, List.of(), false, ex.getMessage()); }
        }

        @Override public void action(int action, String path, String target, EventCallback callback) {
            try {
                switch (action) {
                    case SettingsPackets.DELETE -> library.delete(path);
                    case SettingsPackets.RENAME -> library.rename(path, target);
                    case SettingsPackets.MOVE -> library.move(path, target);
                    case SettingsPackets.MKDIR -> library.mkdir(path);
                    case SettingsPackets.RESCAN, SettingsPackets.RESCAN_NEW -> { }
                    default -> throw new IOException("Unknown action");
                }
                rescanLive();
                callback.result(true, "");
            } catch (IOException | RuntimeException ex) { callback.result(false, ex.getMessage() == null ? "Operation failed" : ex.getMessage()); }
        }

        @Override public void upload(Path file, String dir, UploadCallback callback) {
            callback.progress(-1);
            Thread.ofVirtual().name("retrocraft-rom-copy").start(() -> {
                try {
                    Path target = library.destination(dir, file.getFileName().toString());
                    Files.copy(file, target);
                    MinecraftClient.getInstance().execute(() -> { rescanLive(); callback.done(true, file.getFileName().toString()); });
                } catch (IOException | RuntimeException ex) {
                    String message = ex.getMessage() == null ? "Could not copy the file" : ex.getMessage();
                    MinecraftClient.getInstance().execute(() -> callback.done(false, message));
                }
            });
        }

        @Override public void biosList(String coreId, ListCallback callback) {
            try {
                var listing = biosLibrary.list(coreId);
                callback.result(true, listing.entries().stream().map(e -> new SettingsPackets.RomList.Entry(e.name(), e.directory(), e.size())).toList(), listing.truncated(), null);
            } catch (IOException | RuntimeException ex) { callback.result(false, List.of(), false, ex.getMessage()); }
        }

        @Override public void biosDelete(String coreId, String name, EventCallback callback) {
            try {
                biosLibrary.delete(coreId + "/" + name);
                callback.result(true, "");
            } catch (IOException | RuntimeException ex) { callback.result(false, ex.getMessage() == null ? "Operation failed" : ex.getMessage()); }
        }

        @Override public void coreUpload(Path file, String coreId, UploadCallback callback) {
            callback.progress(-1);
            Thread.ofVirtual().name("retrocraft-bios-copy").start(() -> {
                try {
                    int landed = biosLibrary.install(coreId, file.getFileName().toString(), Files.readAllBytes(file), true);
                    MinecraftClient.getInstance().execute(() -> callback.done(true, landed > 1 ? landed + " files" : file.getFileName().toString()));
                } catch (IOException | RuntimeException ex) {
                    String message = ex.getMessage() == null ? "Could not copy the file" : ex.getMessage();
                    MinecraftClient.getInstance().execute(() -> callback.done(false, message));
                }
            });
        }

        /** The integrated server rescans live; an offline edit drops the catalog cache so the next boot rescans. */
        private static void rescanLive() {
            var server = MinecraftClient.getInstance().getServer();
            var instances = Retrocraft.instances();
            if (server != null && instances != null) server.execute(instances::rescan);
            else {
                try { Files.deleteIfExists(RetrocraftConfig.serverCache().resolve("catalog.json")); } catch (IOException ignored) { }
            }
        }

        @Override public void close() { }
    }
}
