package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.GameSaves;

class GameSavesTest {
    @TempDir Path root;

    @Test
    void deliberatePowerOffKeepsBatteryButNotSnapshot() throws Exception {
        GameSaves saves = new GameSaves(root.resolve("saves"));
        UUID owner = UUID.randomUUID();
        String game = "0123456789abcdef";
        Path state = root.resolve("saves").resolve(owner.toString()).resolve(game + ".state");
        Files.createDirectories(state.getParent());
        Files.write(state, new byte[] {1});
        Path battery = Files.write(root.resolve("battery.sav"), new byte[] {2, 3});
        saves.persistBattery(owner, false, game, battery);
        assertFalse(Files.exists(state));
        assertArrayEquals(new byte[] {2, 3}, saves.restoreBattery(owner, false, game));
    }
}
