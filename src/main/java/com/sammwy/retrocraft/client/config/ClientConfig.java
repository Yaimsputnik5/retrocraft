package com.sammwy.retrocraft.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.sammwy.retrocraft.roms.SaveStates;

/** Personal preferences; never used to authorize or change server settings. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public Map<String, Map<String, Integer>> controls = new LinkedHashMap<>();
    public String ndsLayout = "vertical", threedsLayout = "vertical";
    public Map<String, String> handheldLayouts = new LinkedHashMap<>();
    public int volume = 100;
    public int maxVideoFps = 60;
    public boolean renderWorldScreens = true;
    public boolean renderHandheldScreens = true;
    public boolean showPlayerHud = true;
    public boolean cameraFocus = true;

    public static ClientConfig load(Path file) throws IOException {
        if (!Files.exists(file)) return new ClientConfig();
        try {
            if (Files.size(file) > 131_072) throw new IOException("Client config exceeds limit");
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
                        case "ndsLayout" -> {
                            copy.ndsLayout = value;
                            copy.handheldLayouts.remove("retrocraft:nds_handheld");
                        }
                        case "threedsLayout" -> {
                            copy.threedsLayout = value;
                            copy.handheldLayouts.remove("retrocraft:3ds_handheld");
                        }
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

    public Map<String, Integer> controls(String console) {
        return Map.copyOf(controls.getOrDefault(console, Map.of()));
    }

    public DualScreenLayout hudLayout(String key) {
        String legacy =
                switch (key) {
                    case "retrocraft:nds_handheld" -> ndsLayout;
                    case "retrocraft:3ds_handheld" -> threedsLayout;
                    default -> "vertical";
                };
        return DualScreenLayout.parse(handheldLayouts.getOrDefault(key, legacy));
    }

    public ClientConfig withHudLayout(String key, DualScreenLayout layout) {
        ClientConfig copy = GSON.fromJson(GSON.toJson(this), ClientConfig.class);
        copy.handheldLayouts.put(key, layout.id());
        copy.validate();
        return copy;
    }

    public ClientConfig withControls(Map<String, Map<String, Integer>> bindings) {
        ClientConfig copy = GSON.fromJson(GSON.toJson(this), ClientConfig.class);
        copy.controls = new LinkedHashMap<>();
        bindings.forEach(
                (console, values) -> {
                    if (!values.isEmpty()) copy.controls.put(console, new LinkedHashMap<>(values));
                });
        copy.validate();
        return copy;
    }

    public void save(Path file) throws IOException {
        validate();
        SaveStates.writeAtomic(file, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
    }

    private void validate() {
        DualScreenLayout.parse(ndsLayout);
        DualScreenLayout.parse(threedsLayout);
        if (handheldLayouts == null || handheldLayouts.size() > 128)
            throw new IllegalArgumentException("Invalid handheld layouts");
        handheldLayouts.forEach(
                (key, value) -> {
                    if (key == null || !key.matches("[a-z0-9_.:/-]{1,128}"))
                        throw new IllegalArgumentException("Invalid handheld layout key");
                    DualScreenLayout.parse(value);
                });
        if (controls == null || controls.size() > 128)
            throw new IllegalArgumentException("Invalid controller bindings");
        controls.forEach(
                (console, values) -> {
                    if (console == null
                            || !console.matches("[a-z0-9_]{1,64}")
                            || values == null
                            || values.size() > 24)
                        throw new IllegalArgumentException("Invalid controller profile");
                    var actions =
                            ControllerProfile.of(console).actions().stream()
                                    .map(ControllerProfile.Action::id)
                                    .toList();
                    values.forEach(
                            (action, key) -> {
                                if (!actions.contains(action)
                                        || key == null
                                        || !(key == -1
                                                || key >= -9 && key <= -2
                                                || key >= 32 && key <= 348)
                                        || key == 256
                                        || key == 297)
                                    throw new IllegalArgumentException(
                                            "Invalid controller binding");
                            });
                });
        if (volume < 0 || volume > 100 || maxVideoFps < 1 || maxVideoFps > 144)
            throw new IllegalArgumentException("Client config values out of range");
    }

    private static boolean bool(String value) {
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException("Expected true or false");
        return Boolean.parseBoolean(value);
    }
}
