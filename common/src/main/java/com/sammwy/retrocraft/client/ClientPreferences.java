package com.sammwy.retrocraft.client;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.client.config.ClientConfig;
import com.sammwy.retrocraft.client.render.Thumbnails;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.net.SettingsPackets;

/** Client-thread owner of the persistent personal settings. */
public final class ClientPreferences {
    private static ClientConfig config = new ClientConfig();

    public static ClientConfig get() {
        return config;
    }

    static void load() {
        try {
            config = ClientConfig.load(RetrocraftConfig.root().resolve("client.json"));
        } catch (IOException ex) {
            Retrocraft.LOG.warn("Cannot load personal Retrocraft settings; using defaults", ex);
        }
    }

    public static void apply(Map<String, String> changes) throws IOException {
        ClientConfig next = config.changed(changes);
        next.save(RetrocraftConfig.root().resolve("client.json"));
        config = next;
        send();
        if (config.volume == 0 && RetrocraftClient.AUDIO != null) RetrocraftClient.AUDIO.close();
        if (RetrocraftClient.VIDEO != null) RetrocraftClient.VIDEO.preferencesChanged();
        if (!config.renderHandheldScreens) Thumbnails.close();
    }

    public static void applyControls(Map<String, Map<String, Integer>> bindings)
            throws IOException {
        ClientConfig next = config.withControls(bindings);
        next.save(RetrocraftConfig.root().resolve("client.json"));
        config = next;
    }

    public static void applyHudLayout(
            String key, com.sammwy.retrocraft.client.config.DualScreenLayout layout)
            throws IOException {
        ClientConfig next = config.withHudLayout(key, layout);
        next.save(RetrocraftConfig.root().resolve("client.json"));
        config = next;
    }

    static void send() {
        if (ClientNetwork.canSend(Packets.Preferences.ID))
            ClientNetwork.send(
                    new Packets.Preferences(
                            config.volume > 0,
                            config.renderWorldScreens,
                            config.renderHandheldScreens));
    }

    public static List<SettingsPackets.GuiOpen.Entry> schema() {
        return List.of(
                number("volume", config.volume, 0, 100),
                number("maxVideoFps", config.maxVideoFps, 1, 144),
                toggle("renderWorldScreens", config.renderWorldScreens),
                toggle("renderHandheldScreens", config.renderHandheldScreens),
                toggle("showPlayerHud", config.showPlayerHud),
                toggle("cameraFocus", config.cameraFocus));
    }

    private static SettingsPackets.GuiOpen.Entry number(String key, int value, int min, int max) {
        return new SettingsPackets.GuiOpen.Entry(
                key, Integer.toString(value), SettingsPackets.INT, -1, min, max, List.of());
    }

    private static SettingsPackets.GuiOpen.Entry toggle(String key, boolean value) {
        return new SettingsPackets.GuiOpen.Entry(
                key,
                Boolean.toString(value),
                SettingsPackets.BOOL,
                -1,
                0,
                0,
                List.of("true", "false"));
    }

    private ClientPreferences() {}
}
