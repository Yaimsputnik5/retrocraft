package com.sammwy.retrocraft.server.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.roms.BiosFiles;
import com.sammwy.retrocraft.roms.SaveStates;

import net.fabricmc.loader.api.FabricLoader;

public final class RetrocraftConfig {
    public enum EmulationMode {
        @SerializedName("server")
        SERVER("server"),
        @SerializedName("client")
        CLIENT("client"),
        @SerializedName("hybrid")
        HYBRID("hybrid");
        public final String id;

        EmulationMode(String id) {
            this.id = id;
        }

        public static EmulationMode parse(String raw) {
            for (var mode : values()) if (mode.id.equalsIgnoreCase(raw)) return mode;
            throw new IllegalArgumentException("Expected server, client or hybrid");
        }
    }

    public enum SaveMode {
        @SerializedName("player")
        PLAYER("player"),
        @SerializedName("global")
        GLOBAL("global"),
        @SerializedName("none")
        NONE("none");
        public final String id;

        SaveMode(String id) {
            this.id = id;
        }

        public static SaveMode parse(String raw) {
            for (var mode : values()) if (mode.id.equalsIgnoreCase(raw)) return mode;
            throw new IllegalArgumentException("Expected player, global or none");
        }
    }

    public Map<String, String> cores = new HashMap<>(KnownCores.defaultMappings());
    public int checkpointIntervalSeconds = 2;
    public int transferTimeoutSeconds = 30;
    public int ownerTimeoutSeconds = 8;
    public int audioRadius = 16;
    public int streamFps = 20;
    public int keyframeIntervalSeconds = 3;
    public int autosaveIntervalSeconds = 30;
    public int maxActiveInstances = 8;
    public int watcherTimeoutSeconds = 15;
    public int maxRomSizeMiB = 64;
    public int thumbnailIntervalSeconds = 5;
    public boolean thumbnailUpload = true;
    public EmulationMode emulationMode = EmulationMode.HYBRID;
    public SaveMode saveMode = SaveMode.PLAYER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("retrocraft");
    }

    public static Path cacheRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("cache/retrocraft");
    }

    public static Path clientCache() {
        return cacheRoot().resolve("client");
    }

    public static Path serverCache() {
        return cacheRoot().resolve("server");
    }

    /**
     * Shared core pool: one copy serves the integrated server and hybrid clients on this machine
     * (LAN included).
     */
    public static Path coreCache() {
        return cacheRoot().resolve("cores");
    }

    public static Path runtimeCache() {
        return cacheRoot().resolve("runtime");
    }

    public static Path savesRoot() {
        return root().resolve("saves");
    }

    public Path file() {
        return root().resolve("server.json");
    }

    public boolean coreEnabled(String id) {
        return true;
    }

    public static Path biosDir(String coreId) {
        return root().resolve("bios").resolve(BiosFiles.directory(coreId));
    }

    public static Path systemDir(String coreId) {
        return coreId.equals("pcsx2") ? root().resolve("bios") : biosDir(coreId);
    }

    public String coreId(String cartridge) {
        String configured = cores.get(cartridge);
        // File paths from the old prototype cannot become client download instructions.
        if (configured == null || configured.contains("/") || configured.contains("\\"))
            configured = KnownCores.defaultForCartridge(cartridge);
        return KnownCores.get(configured).id();
    }

    public void save() throws IOException {
        save(file());
    }

    public void save(Path target) throws IOException {
        SaveStates.writeAtomic(target, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
    }

    public static RetrocraftConfig load() throws IOException {
        return load(root());
    }

    public static RetrocraftConfig load(Path directory) throws IOException {
        Files.createDirectories(directory.resolve("roms"));

        Path file = directory.resolve("server.json"), legacy = directory.resolve("config.json");
        boolean migrated = !Files.exists(file) && Files.exists(legacy);
        RetrocraftConfig config;
        if (Files.exists(file) || migrated) {
            if (Files.size(migrated ? legacy : file) > 65_536)
                throw new IOException("Server config exceeds limit");
            try (Reader reader = Files.newBufferedReader(migrated ? legacy : file)) {
                JsonElement json = JsonParser.parseReader(reader);
                config = GSON.fromJson(json, RetrocraftConfig.class);
                // Honor the initial prototype's setting when upgrading an existing configuration.
                if (config != null
                        && config.cores != null
                        && json.isJsonObject()
                        && !json.getAsJsonObject().has("cores")
                        && json.getAsJsonObject().has("nesCore"))
                    config.cores.put(
                            "retrocraft:nes", json.getAsJsonObject().get("nesCore").getAsString());
            } catch (JsonParseException | IllegalStateException ex) {
                throw new IOException("Invalid " + (migrated ? legacy : file), ex);
            }
        } else config = new RetrocraftConfig();
        if (config == null
                || config.cores == null
                || config.cores.values().stream().anyMatch(v -> v == null || v.isBlank()))
            throw new IOException("Invalid core mappings");
        config.cores
                .entrySet()
                .removeIf(
                        entry -> !KnownCores.compatible(entry.getKey()).contains(entry.getValue()));
        if (config.audioRadius < 1
                || config.audioRadius > 256
                || config.checkpointIntervalSeconds < 1
                || config.checkpointIntervalSeconds > 30
                || config.transferTimeoutSeconds < 10
                || config.transferTimeoutSeconds > 120
                || config.ownerTimeoutSeconds < 3
                || config.ownerTimeoutSeconds > 60
                || config.streamFps < 1
                || config.streamFps > 144
                || config.keyframeIntervalSeconds < 1
                || config.keyframeIntervalSeconds > 600
                || config.autosaveIntervalSeconds < 5
                || config.autosaveIntervalSeconds > 3600
                || config.maxActiveInstances < 1
                || config.maxActiveInstances > 64
                || config.watcherTimeoutSeconds < 5
                || config.watcherTimeoutSeconds > 120
                || config.maxRomSizeMiB < 1
                || config.maxRomSizeMiB > 64
                || config.thumbnailIntervalSeconds < 1
                || config.thumbnailIntervalSeconds > 600
                || config.emulationMode == null
                || config.saveMode == null)
            throw new IOException("Retrocraft config values out of range");
        if (migrated || !Files.exists(file)) config.save(file);
        return config;
    }
}
