package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.BiosCache;
import com.sammwy.retrocraft.roms.InstanceStore;

class BiosCacheTest {
    @TempDir Path root;

    @Test
    void matchingFirmwareIsNotDownloadedAgain() throws Exception {
        Path file = Files.write(root.resolve("firmware.bin"), new byte[] {1, 2, 3, 4});
        String hash = InstanceStore.sha256(file);
        assertTrue(BiosCache.matches(file, 4, hash));
        assertFalse(BiosCache.matches(file, 3, hash));
        Files.write(file, new byte[] {4, 3, 2, 1});
        assertFalse(BiosCache.matches(file, 4, hash));
        assertFalse(BiosCache.matches(root.resolve("missing.bin"), 4, hash));
        assertFalse(BiosCache.matches(file, 4, "invalid"));
    }
}
