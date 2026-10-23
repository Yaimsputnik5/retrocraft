package com.sammwy.retrocraft.client;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.google.gson.Gson;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.roms.RomArchives;
import com.sammwy.retrocraft.roms.RomLibrary;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.net.SettingsPackets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * What the settings screen talks to. On a remote server every call is a packet; in single player or
 * from the title screen the same widgets edit the local config/roms directly.
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

    interface ApplyCallback {
        void result(boolean ok, String message, List<SettingsPackets.GuiOpen.Entry> entries);
    }

    interface ListCallback {
        void result(
                boolean ok,
                List<SettingsPackets.RomList.Entry> entries,
                boolean truncated,
                String error);
    }

    interface EventCallback {
        void result(boolean ok, String message);
    }

    interface UploadCallback {
        void progress(float fraction);

        default void done(boolean ok, String message) {}
    }

    /** Packet-backed backend; the server re-checks operator permission on every request. */
    final class Remote implements SettingsBackend {
        private List<SettingsPackets.GuiOpen.Entry> schema;
        private ApplyCallback pendingApply;
        private long applyDeadline;
        private volatile boolean closed;

        Remote(SettingsPackets.GuiOpen grant) {
            schema = grant.entries();
        }

        @Override
        public Text scope() {
            return Text.translatable("settings.retrocraft.scope.server");
        }

        @Override
        public List<SettingsPackets.GuiOpen.Entry> schema() {
            return schema;
        }

        @Override
        public void apply(
                List<SettingsPackets.ConfigApply.Change> changes, ApplyCallback callback) {
            if (pendingApply != null) {
                callback.result(false, "A settings update is already pending", schema);
                return;
            }
            pendingApply = callback;
            applyDeadline = System.nanoTime() + 20_000_000_000L;
            ClientPlayNetworking.send(new SettingsPackets.ConfigApply(changes));
        }

        boolean applying() {
            return pendingApply != null;
        }

        void tick() {
            if (pendingApply != null && System.nanoTime() > applyDeadline) {
                var callback = pendingApply;
                pendingApply = null;
                callback.result(false, "Settings update timed out", schema);
            }
        }

        /** Routed from the packet receiver through the screen. */
        void saved(SettingsPackets.ConfigSaved packet) {
            var callback = pendingApply;
            pendingApply = null;
            schema = packet.entries();
            if (callback != null) callback.result(packet.ok(), packet.message(), packet.entries());
        }

        @Override
        public void list(String path, ListCallback callback) {
            int id =
                    SettingsClient.submit(
                            new SettingsClient.Response() {
                                @Override
                                public void list(SettingsPackets.RomList packet) {
                                    callback.result(
                                            true, packet.entries(), packet.truncated(), null);
                                }

                                @Override
                                public void event(SettingsPackets.RomEvent packet) {
                                    callback.result(false, List.of(), false, packet.message());
                                }
                            });
            ClientPlayNetworking.send(SettingsPackets.RomAction.of(id, SettingsPackets.LIST, path));
        }

        @Override
        public void action(int action, String path, String target, EventCallback callback) {
            int id =
                    SettingsClient.submit(
                            new SettingsClient.Response() {
                                @Override
                                public void event(SettingsPackets.RomEvent packet) {
                                    callback.result(packet.ok(), packet.message());
                                }
                            });
            ClientPlayNetworking.send(
                    new SettingsPackets.RomAction(
                            id, action, SettingsPackets.AREA_ROMS, path, target));
        }

        @Override
        public void upload(Path file, String dir, UploadCallback callback) {
            readUpload(file, SettingsPackets.AREA_ROMS, dir, callback);
        }

        @Override
        public void biosList(String coreId, ListCallback callback) {
            int id =
                    SettingsClient.submit(
                            new SettingsClient.Response() {
                                @Override
                                public void list(SettingsPackets.RomList packet) {
                                    callback.result(
                                            true, packet.entries(), packet.truncated(), null);
                                }

                                @Override
                                public void event(SettingsPackets.RomEvent packet) {
                                    callback.result(false, List.of(), false, packet.message());
                                }
                            });
            ClientPlayNetworking.send(
                    new SettingsPackets.RomAction(
                            id, SettingsPackets.LIST, SettingsPackets.AREA_BIOS, coreId, ""));
        }

        @Override
        public void biosDelete(String coreId, String name, EventCallback callback) {
            int id =
                    SettingsClient.submit(
                            new SettingsClient.Response() {
                                @Override
                                public void event(SettingsPackets.RomEvent packet) {
                                    callback.result(packet.ok(), packet.message());
                                }
                            });
            ClientPlayNetworking.send(
                    new SettingsPackets.RomAction(
                            id,
                            SettingsPackets.DELETE,
                            SettingsPackets.AREA_BIOS,
                            coreId + "/" + name,
                            ""));
        }

        @Override
        public void coreUpload(Path file, String coreId, UploadCallback callback) {
            readUpload(file, SettingsPackets.AREA_BIOS, coreId, callback);
        }

        private void readUpload(Path file, int area, String directory, UploadCallback callback) {
            if (closed) {
                callback.done(false, "Settings screen closed");
                return;
            }
            Thread.ofVirtual()
                    .name("retrocraft-upload-read")
                    .start(
                            () -> {
                                try {
                                    long size = Files.size(file);
                                    if (size < 1 || size > BlobTransfer.MAX_ROM)
                                        throw new IOException("File outside the upload size limit");
                                    byte[] bytes;
                                    try (var in = Files.newInputStream(file)) {
                                        bytes = RomArchives.bounded(in, BlobTransfer.MAX_ROM);
                                    }
                                    String hash = BlobTransfer.hash(bytes);
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () -> {
                                                        if (closed) {
                                                            callback.done(
                                                                    false,
                                                                    "Settings screen closed");
                                                            return;
                                                        }
                                                        SettingsClient.upload(
                                                                area,
                                                                directory,
                                                                file.getFileName().toString(),
                                                                bytes,
                                                                hash,
                                                                new SettingsClient.Callback() {
                                                                    public void progress(
                                                                            float fraction) {
                                                                        callback.progress(fraction);
                                                                    }

                                                                    public void done(
                                                                            boolean ok,
                                                                            String message) {
                                                                        callback.done(ok, message);
                                                                    }
                                                                });
                                                    });
                                } catch (IOException | RuntimeException ex) {
                                    String message =
                                            ex.getMessage() == null
                                                    ? "Could not read the file"
                                                    : ex.getMessage();
                                    MinecraftClient.getInstance()
                                            .execute(() -> callback.done(false, message));
                                }
                            });
        }

        @Override
        public void close() {
            closed = true;
            SettingsClient.abortUpload();
        }
    }

    /**
     * Single-player / title-screen backend: edits config/retrocraft on this machine, live-applied
     * when hosting.
     */
    final class Local implements SettingsBackend {
        private final RomLibrary library, biosLibrary;
        private RetrocraftConfig config;
        private List<SettingsPackets.GuiOpen.Entry> schema;

        Local() throws IOException {
            config = RetrocraftConfig.load();
            library = new RomLibrary(RetrocraftConfig.root().resolve("roms"));
            biosLibrary = new RomLibrary(RetrocraftConfig.root().resolve("bios"));
            schema = SettingsPackets.GuiOpen.schema(config);
        }

        @Override
        public Text scope() {
            return Text.translatable("settings.retrocraft.scope.local");
        }

        @Override
        public List<SettingsPackets.GuiOpen.Entry> schema() {
            return schema;
        }

        @Override
        public void apply(
                List<SettingsPackets.ConfigApply.Change> changes, ApplyCallback callback) {
            String error = null;
            var gson = new Gson();
            RetrocraftConfig probe = gson.fromJson(gson.toJson(config), RetrocraftConfig.class);
            for (var change : changes) {
                try {
                    ConfigKey.set(probe, change.key(), change.value());
                } catch (IllegalArgumentException ex) {
                    error = change.key() + ": " + ex.getMessage();
                    break;
                }
            }
            if (error == null) {
                try {
                    probe.save();
                    config = probe;
                    applyLive(changes);
                } catch (IOException | RuntimeException ex) {
                    error =
                            ex.getMessage() == null
                                    ? "Could not save the config file"
                                    : ex.getMessage();
                }
            }
            schema = SettingsPackets.GuiOpen.schema(config);
            callback.result(error == null, error == null ? "" : error, schema);
        }

        private static void applyLive(List<SettingsPackets.ConfigApply.Change> changes) {
            var server = MinecraftClient.getInstance().getServer();
            var instances = Retrocraft.instances();
            if (server == null || instances == null) return;
            server.execute(
                    () -> {
                        for (var change : changes) {
                            try {
                                ConfigKey.set(instances.config(), change.key(), change.value());
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        instances.applyConfig();
                    });
        }

        @Override
        public void list(String path, ListCallback callback) {
            listAsync(library, path, callback);
        }

        private static void listAsync(RomLibrary source, String path, ListCallback callback) {
            Thread.ofVirtual()
                    .name("retrocraft-local-list")
                    .start(
                            () -> {
                                try {
                                    var listing = source.list(path);
                                    var entries =
                                            listing.entries().stream()
                                                    .map(
                                                            e ->
                                                                    new SettingsPackets.RomList
                                                                            .Entry(
                                                                            e.name(),
                                                                            e.directory(),
                                                                            e.size()))
                                                    .toList();
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () ->
                                                            callback.result(
                                                                    true,
                                                                    entries,
                                                                    listing.truncated(),
                                                                    null));
                                } catch (IOException | RuntimeException ex) {
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () ->
                                                            callback.result(
                                                                    false,
                                                                    List.of(),
                                                                    false,
                                                                    ex.getMessage()));
                                }
                            });
        }

        @Override
        public void action(int action, String path, String target, EventCallback callback) {
            Thread.ofVirtual()
                    .name("retrocraft-local-action")
                    .start(
                            () -> {
                                try {
                                    switch (action) {
                                        case SettingsPackets.DELETE -> library.delete(path);
                                        case SettingsPackets.RENAME -> library.rename(path, target);
                                        case SettingsPackets.MOVE -> library.move(path, target);
                                        case SettingsPackets.MKDIR -> library.mkdir(path);
                                        case SettingsPackets.RESCAN,
                                                SettingsPackets.RESCAN_NEW -> {}
                                        default -> throw new IOException("Unknown action");
                                    }
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () -> {
                                                        rescanLive();
                                                        callback.result(true, "");
                                                    });
                                } catch (IOException | RuntimeException ex) {
                                    String message =
                                            ex.getMessage() == null
                                                    ? "Operation failed"
                                                    : ex.getMessage();
                                    MinecraftClient.getInstance()
                                            .execute(() -> callback.result(false, message));
                                }
                            });
        }

        @Override
        public void upload(Path file, String dir, UploadCallback callback) {
            callback.progress(-1);
            Thread.ofVirtual()
                    .name("retrocraft-rom-copy")
                    .start(
                            () -> {
                                try {
                                    Path target =
                                            library.destination(dir, file.getFileName().toString());
                                    Files.copy(file, target);
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () -> {
                                                        rescanLive();
                                                        callback.done(
                                                                true,
                                                                file.getFileName().toString());
                                                    });
                                } catch (IOException | RuntimeException ex) {
                                    String message =
                                            ex.getMessage() == null
                                                    ? "Could not copy the file"
                                                    : ex.getMessage();
                                    MinecraftClient.getInstance()
                                            .execute(() -> callback.done(false, message));
                                }
                            });
        }

        @Override
        public void biosList(String coreId, ListCallback callback) {
            listAsync(biosLibrary, coreId, callback);
        }

        @Override
        public void biosDelete(String coreId, String name, EventCallback callback) {
            Thread.ofVirtual()
                    .name("retrocraft-local-bios-delete")
                    .start(
                            () -> {
                                try {
                                    biosLibrary.delete(coreId + "/" + name);
                                    MinecraftClient.getInstance()
                                            .execute(() -> callback.result(true, ""));
                                } catch (IOException | RuntimeException ex) {
                                    String message =
                                            ex.getMessage() == null
                                                    ? "Operation failed"
                                                    : ex.getMessage();
                                    MinecraftClient.getInstance()
                                            .execute(() -> callback.result(false, message));
                                }
                            });
        }

        @Override
        public void coreUpload(Path file, String coreId, UploadCallback callback) {
            callback.progress(-1);
            Thread.ofVirtual()
                    .name("retrocraft-bios-copy")
                    .start(
                            () -> {
                                try {
                                    byte[] bytes;
                                    try (var in = Files.newInputStream(file)) {
                                        bytes = RomArchives.bounded(in, BlobTransfer.MAX_ROM);
                                    }
                                    int landed =
                                            biosLibrary.install(
                                                    coreId,
                                                    file.getFileName().toString(),
                                                    bytes,
                                                    true);
                                    MinecraftClient.getInstance()
                                            .execute(
                                                    () ->
                                                            callback.done(
                                                                    true,
                                                                    landed > 1
                                                                            ? landed + " files"
                                                                            : file.getFileName()
                                                                                    .toString()));
                                } catch (IOException | RuntimeException ex) {
                                    String message =
                                            ex.getMessage() == null
                                                    ? "Could not copy the file"
                                                    : ex.getMessage();
                                    MinecraftClient.getInstance()
                                            .execute(() -> callback.done(false, message));
                                }
                            });
        }

        /**
         * The integrated server rescans live; an offline edit drops the catalog cache so the next
         * boot rescans.
         */
        private static void rescanLive() {
            var server = MinecraftClient.getInstance().getServer();
            var instances = Retrocraft.instances();
            if (server != null && instances != null) server.execute(instances::rescan);
            else {
                try {
                    Files.deleteIfExists(RetrocraftConfig.serverCache().resolve("catalog.json"));
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        public void close() {}
    }
}
