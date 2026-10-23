package com.sammwy.retrocraft.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.sammwy.retrocraft.roms.SaveStates;

/** Personal preferences; never used to authorize or change server settings. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int volume = 100;
    public int maxVideoFps = 60;
    public boolean renderWorldScreens = true;
    public boolean renderHandheldScreens = true;
    public boolean showPlayerHud = true;
    public boolean cameraFocus = true;

    public static ClientConfig load(Path file) throws IOException {
        if (!Files.exists(file)) return new ClientConfig();
        try {
            if (Files.size(file) > 16_384) throw new IOException("Client config exceeds limit");
            ClientConfig config = GSON.fromJson(Files.readString(file), ClientConfig.class);
            if (config == null) throw new IOException("Invalid client config");
            config.validate();
            return config;
        } catch (JsonParseException | IllegalArgumentException ex) {
            throw new IOException("Invalid client config", ex);
        }
    }

    public ClientConfig changed(Map<String, String> changes) {
        ClientConfig copy = GSON.fromJson(GSON.toJson(this), ClientConfig.class);
        changes.forEach(
                (key, value) -> {
                    switch (key) {
                        case "volume" -> copy.volume = Integer.parseInt(value);
                        case "maxVideoFps" -> copy.maxVideoFps = Integer.parseInt(value);
                        case "renderWorldScreens" -> copy.renderWorldScreens = bool(value);
                        case "renderHandheldScreens" -> copy.renderHandheldScreens = bool(value);
                        case "showPlayerHud" -> copy.showPlayerHud = bool(value);
                        case "cameraFocus" -> copy.cameraFocus = bool(value);
                        default ->
                                throw new IllegalArgumentException(
                                        "Unknown client setting: " + key);
                    }
                });
        copy.validate();
        return copy;
    }

    public void save(Path file) throws IOException {
        validate();
        SaveStates.writeAtomic(file, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
    }

    private void validate() {
        if (volume < 0 || volume > 100 || maxVideoFps < 1 || maxVideoFps > 144)
            throw new IllegalArgumentException("Client config values out of range");
    }

    private static boolean bool(String value) {
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException("Expected true or false");
        return Boolean.parseBoolean(value);
    }
}
