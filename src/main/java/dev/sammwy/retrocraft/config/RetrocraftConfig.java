package dev.sammwy.retrocraft.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;

public final class RetrocraftConfig {
    public java.util.Map<String, String> cores = new java.util.HashMap<>(java.util.Map.of("retrocraft:nes", "cores/fceumm_libretro.so"));
    public int audioRadius = 16;
    public int streamFps = 20;
    public int keyframeIntervalSeconds = 3;
    public int autosaveIntervalSeconds = 30;
    public int maxActiveInstances = 8;
    public int watcherTimeoutSeconds = 15;
    public int maxRomSizeMiB = 16;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("retrocraft"); }
    public Path corePath(net.minecraft.util.Identifier cartridge) {
        String value = cores.get(cartridge.toString());
        if (value == null || value.isBlank()) throw new IllegalArgumentException("No core configured for " + cartridge);
        Path configured = Path.of(value);
        return (configured.isAbsolute() ? configured : root().resolve(configured)).normalize();
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
