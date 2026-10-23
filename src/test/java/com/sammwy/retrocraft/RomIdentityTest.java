package com.sammwy.retrocraft;

import com.sammwy.retrocraft.roms.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.RandomAccessFile;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RomIdentityTest {
    @TempDir Path root;

    @Test void largeDiscUsesTheIndexedPrefixHashInsteadOfTheFullFileHash() throws Exception {
        Path disc = root.resolve("disc.iso");
        try (RandomAccessFile file = new RandomAccessFile(disc.toFile(), "rw")) {
            file.write(42);
            file.setLength(129 * 1024);
        }
        long limit = 128 * 1024;
        String id = RomScanner.hash(disc, Files.size(disc), limit);
        Path materialized = new RomSource("disc.iso", null, -1).materialize(root, root.resolve("cache"), id, "disc.iso", limit);
        assertEquals(disc, materialized);
        assertNotEquals(id, InstanceStore.sha256(materialized));
        assertTrue(RomSource.matches(materialized, id, limit));
        Files.write(materialized, new byte[]{1}, StandardOpenOption.WRITE);
        assertFalse(RomSource.matches(materialized, id, limit));
    }
}
