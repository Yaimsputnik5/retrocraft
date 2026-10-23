package com.sammwy.retrocraft;

import com.sammwy.retrocraft.client.config.ClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClientConfigTest {
    @TempDir Path root;

    @Test void personalPreferencesRoundTripWithoutTouchingServerSettings() throws Exception {
        Path file = root.resolve("client.json"), server = root.resolve("server.json");
        Files.writeString(server, "server settings");
        ClientConfig config = ClientConfig.load(file).changed(Map.of("volume", "25", "renderWorldScreens", "false", "maxVideoFps", "30"));
        config.save(file);
        ClientConfig restored = ClientConfig.load(file);
        assertEquals(25, restored.volume);
        assertEquals(30, restored.maxVideoFps);
        assertFalse(restored.renderWorldScreens);
        assertTrue(restored.showPlayerHud);
        assertEquals("server settings", Files.readString(server));
    }

    @Test void rejectedBatchLeavesCurrentPreferencesAndDiskUnchanged() throws Exception {
        Path file = root.resolve("client.json");
        ClientConfig config = new ClientConfig(); config.save(file);
        assertThrows(IllegalArgumentException.class, () -> config.changed(Map.of("volume", "20", "maxVideoFps", "0")));
        assertThrows(IllegalArgumentException.class, () -> config.changed(Map.of("showPlayerHud", "maybe")));
        assertThrows(IllegalArgumentException.class, () -> config.changed(Map.of("emulationMode", "client")));
        assertEquals(100, config.volume);
        assertEquals(100, ClientConfig.load(file).volume);
        Files.writeString(file, "{\"volume\":-1}");
        assertThrows(IOException.class, () -> ClientConfig.load(file));
    }
}
