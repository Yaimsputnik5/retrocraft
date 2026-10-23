package com.sammwy.retrocraft.server.config;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.sammwy.retrocraft.cores.KnownCores;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class RetrocraftConfig {
    public enum EmulationMode {
        @SerializedName("server") SERVER("server"),
        @SerializedName("client") CLIENT("client"),
        @SerializedName("hybrid") HYBRID("hybrid");
        public final String id;
        EmulationMode(String id) { this.id = id; }
        public static EmulationMode parse(String raw) {
            for (var mode : values()) if (mode.id.equalsIgnoreCase(raw)) return mode;
            throw new IllegalArgumentException("Expected server, client or hybrid");
        }
    }
    public enum SaveMode {
        @SerializedName("player") PLAYER("player"),
        @SerializedName("global") GLOBAL("global"),
        @SerializedName("none") NONE("none");
        public final String id;
        SaveMode(String id) { this.id = id; }
        public static SaveMode parse(String raw) {
            for (var mode : values()) if (mode.id.equalsIgnoreCase(raw)) return mode;
            throw new IllegalArgumentException("Expected player, global or none");
        }
    }

    public Map<String, String> cores = new HashMap<>(KnownCores.defaultMappings());
    /** Cores the server will prepare and offer; the library can still download any known core on demand. */
    public List<String> enabledCores = new ArrayList<>(KnownCores.defaults());
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

    public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("retrocraft"); }
    public static Path cacheRoot() { return FabricLoader.getInstance().getGameDir().resolve("cache/retrocraft"); }
    public static Path clientCache() { return cacheRoot().resolve("client"); }
    public static Path serverCache() { return cacheRoot().resolve("server"); }
    public static Path savesRoot() { return root().resolve("saves"); }
    public Path file() { return root().resolve("server.json"); }

    public boolean coreEnabled(String id) { return enabledCores != null && enabledCores.contains(id); }
    public static Path biosDir(String coreId) { return root().resolve("bios").resolve(coreId); }

    public String coreId(String cartridge) {
        String configured = cores.get(cartridge);
        // File paths from the old prototype cannot become client download instructions.
        if (configured == null || configured.contains("/") || configured.contains("\\")) configured = KnownCores.defaultForCartridge(cartridge);
        return KnownCores.get(configured).id();
    }

    public void save() throws IOException {
        Files.createDirectories(root());
        Path temporary = file().resolveSibling(file().getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) { GSON.toJson(this, writer); }
        Files.move(temporary, file(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static RetrocraftConfig load() throws IOException {
        Files.createDirectories(root().resolve("roms"));

        Path file = root().resolve("server.json"), legacy = root().resolve("config.json");
        boolean migrated = !Files.exists(file) && Files.exists(legacy);
        RetrocraftConfig config;
        if (Files.exists(file) || migrated) {
            try (Reader reader = Files.newBufferedReader(migrated ? legacy : file)) {
                JsonElement json = JsonParser.parseReader(reader);
                config = GSON.fromJson(json, RetrocraftConfig.class);
                // Honor the initial prototype's setting when upgrading an existing configuration.
                if (config != null && json.isJsonObject() && !json.getAsJsonObject().has("cores") && json.getAsJsonObject().has("nesCore"))
                    config.cores.put("retrocraft:nes", json.getAsJsonObject().get("nesCore").getAsString());
            }
            catch (JsonParseException ex) { throw new IOException("Invalid " + (migrated ? legacy : file), ex); }
        } else config = new RetrocraftConfig();
        // Configs written before the core library existed get the default enabled set.
        if (config != null && config.enabledCores == null) config.enabledCores = new ArrayList<>(KnownCores.defaults());
        if (config == null || config.cores == null || config.cores.values().stream().anyMatch(v -> v == null || v.isBlank()) || config.audioRadius < 1 || config.audioRadius > 256
                || config.checkpointIntervalSeconds < 1 || config.checkpointIntervalSeconds > 30
                || config.transferTimeoutSeconds < 10 || config.transferTimeoutSeconds > 120
                || config.ownerTimeoutSeconds < 3 || config.ownerTimeoutSeconds > 60
                || config.streamFps < 1 || config.streamFps > 144
                || config.keyframeIntervalSeconds < 1 || config.keyframeIntervalSeconds > 600
                || config.autosaveIntervalSeconds < 5 || config.autosaveIntervalSeconds > 3600
                || config.maxActiveInstances < 1 || config.maxActiveInstances > 64
                || config.watcherTimeoutSeconds < 5 || config.watcherTimeoutSeconds > 120
                || config.maxRomSizeMiB < 1 || config.maxRomSizeMiB > 64
                || config.thumbnailIntervalSeconds < 1 || config.thumbnailIntervalSeconds > 600
                || config.emulationMode == null || config.saveMode == null)
            throw new IOException("Retrocraft config values out of range");
        if (migrated || !Files.exists(file)) config.save();
        return config;
    }
}
