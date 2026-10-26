package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.*;

class RomIdentityTest {
    @TempDir Path root;

    @Test
    void largeDiscUsesTheIndexedPrefixHashInsteadOfTheFullFileHash() throws Exception {
        Path disc = root.resolve("disc.iso");
        try (RandomAccessFile file = new RandomAccessFile(disc.toFile(), "rw")) {
            file.write(42);
            file.setLength(129 * 1024);
        }
        long limit = 128 * 1024;
        String id = RomScanner.hash(disc, Files.size(disc), limit);
        Path materialized =
                new RomSource("disc.iso", null, -1)
                        .materialize(root, root.resolve("cache"), id, "disc.iso", limit);
        assertEquals(disc, materialized);
        assertNotEquals(id, InstanceStore.sha256(materialized));
        assertTrue(RomSource.matches(materialized, id, limit));
        Files.write(materialized, new byte[] {1}, StandardOpenOption.WRITE);
        assertFalse(RomSource.matches(materialized, id, limit));
    }

    @Test
    void arcadeRomUsesItsLibraryDirectorySoParentSetsRemainVisible() throws Exception {
        Path library = Files.createDirectory(root.resolve("roms"));
        Path arcade = Files.createDirectory(library.resolve("Mame"));
        Path set = Files.write(arcade.resolve("mk2.zip"), new byte[] {1, 2, 3});
        Files.write(arcade.resolve("parent.zip"), new byte[] {4, 5});
        String id = InstanceStore.sha256(set);
        Path cache = root.resolve("cache").resolve(id);
        Files.createDirectories(cache);
        Files.copy(set, cache.resolve("mk2.zip"));
        var source = new RomSource("Mame/mk2.zip", null, -1);
        assertEquals(set, source.original(library, id, 1024));
        Files.write(set, new byte[] {6});
        assertThrows(IOException.class, () -> source.original(library, id, 1024));
    }
}
