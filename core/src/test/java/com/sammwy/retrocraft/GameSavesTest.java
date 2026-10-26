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
    void nativeMemoryStickFilesSurvivePowerOffAndRespectPlayerAndGameScopes() throws Exception {
        var saves = new GameSaves(root);
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        String game = "ab".repeat(32);
        Path directory = saves.nativeDirectory(owner, false, game);
        Path progress = directory.resolve("PSP/SAVEDATA/game/save.bin");
        Files.createDirectories(progress.getParent());
        Files.write(progress, new byte[] {1, 2, 3});
        saves.persistBattery(owner, false, game, root.resolve("missing-battery.sav"));
        saves.cleanOrphans(java.time.Duration.ZERO);
        assertArrayEquals(
                new byte[] {1, 2, 3},
                Files.readAllBytes(
                        new GameSaves(root)
                                .nativeDirectory(owner, false, game)
                                .resolve("PSP/SAVEDATA/game/save.bin")));
        assertNotEquals(directory, saves.nativeDirectory(other, false, game));
        assertNotEquals(directory, saves.nativeDirectory(owner, false, "cd".repeat(32)));
        assertEquals(
                saves.nativeDirectory(owner, true, game), saves.nativeDirectory(other, true, game));
        assertThrows(
                IllegalArgumentException.class,
                () -> saves.nativeDirectory(owner, false, "../outside"));
    }

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
