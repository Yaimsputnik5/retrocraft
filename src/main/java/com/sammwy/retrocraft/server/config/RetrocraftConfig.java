package com.sammwy.retrocraft.server.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;

public final class RetrocraftConfig {
    public java.util.Map<String, String> cores = new java.util.HashMap<>(java.util.Map.of("retrocraft:nes", "fceumm", "retrocraft:snes", "snes9x"));
    public int checkpointIntervalSeconds = 2;
    public int transferTimeoutSeconds = 30;
    public int ownerTimeoutSeconds = 8;
    public int audioRadius = 16;
    public int streamFps = 20;
    public int keyframeIntervalSeconds = 3;
    public int autosaveIntervalSeconds = 30;
    public int maxActiveInstances = 8;
    public int watcherTimeoutSeconds = 15;
    public int maxRomSizeMiB = 16;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("retrocraft"); }
    public String coreId(String cartridge) {
        String configured = cores.get(cartridge);
        // File paths from the old prototype cannot become client download instructions.
        if (configured == null || configured.contains("/") || configured.contains("\\")) configured = com.sammwy.retrocraft.cores.KnownCores.defaultForCartridge(cartridge);
        return com.sammwy.retrocraft.cores.KnownCores.get(configured).id();
    }

    public static RetrocraftConfig load() throws IOException {
        Files.createDirectories(root().resolve("roms"));
        Files.createDirectories(root().resolve("cores"));
        Path file = root().resolve("config.json");
        RetrocraftConfig config;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonElement json = JsonParser.parseReader(reader);
                config = GSON.fromJson(json, RetrocraftConfig.class);
                // Honor the initial prototype's setting when upgrading an existing configuration.
                if (config != null && json.isJsonObject() && !json.getAsJsonObject().has("cores") && json.getAsJsonObject().has("nesCore"))
                    config.cores.put("retrocraft:nes", json.getAsJsonObject().get("nesCore").getAsString());
            }
            catch (JsonParseException ex) { throw new IOException("Invalid config/retrocraft/config.json", ex); }
        } else {
            config = new RetrocraftConfig();
            try (Writer writer = Files.newBufferedWriter(file)) { GSON.toJson(config, writer); }
        }
        if (config == null || config.cores == null || config.cores.values().stream().anyMatch(v -> v == null || v.isBlank()) || config.audioRadius < 1 || config.audioRadius > 128
                || config.checkpointIntervalSeconds < 1 || config.checkpointIntervalSeconds > 30
                || config.transferTimeoutSeconds < 10 || config.transferTimeoutSeconds > 120
                || config.ownerTimeoutSeconds < 3 || config.ownerTimeoutSeconds > 60
                || config.streamFps < 1 || config.streamFps > 20
                || config.keyframeIntervalSeconds < 1 || config.keyframeIntervalSeconds > 60
                || config.autosaveIntervalSeconds < 5 || config.autosaveIntervalSeconds > 3600
                || config.maxActiveInstances < 1 || config.maxActiveInstances > 64
                || config.watcherTimeoutSeconds < 5 || config.watcherTimeoutSeconds > 120
                || config.maxRomSizeMiB < 1 || config.maxRomSizeMiB > 64)
            throw new IOException("Retrocraft config values out of range; see README.md");
        return config;
    }
}
