package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.roms.discovery.RomFormats;

class RomRepositoryTest {
    @TempDir Path root;

    @Test
    void nonemptyCatalogIsReusedUntilExplicitScanAndEmptyCatalogBootstrapsAgain() throws Exception {
        Path library = Files.createDirectories(root.resolve("roms")),
                cache = root.resolve("cache/server");
        var repository = new RomRepository(library, cache, RomFormats.ALL, 1024 * 1024);
        assertTrue(repository.initialize(RomDiscoveryTest.failWarning()).isEmpty());
        Files.write(library.resolve("first.nes"), HomebrewRom.bytes());
        assertEquals(1, repository.initialize(RomDiscoveryTest.failWarning()).size());
        byte[] other = HomebrewRom.bytes().clone();
        other[20] ^= 1;
        Files.write(library.resolve("new.nes"), other);
        Files.delete(library.resolve("first.nes"));
        var cached = repository.initialize(RomDiscoveryTest.failWarning());
        assertEquals("first", cached.getFirst().name());
        assertFalse(Files.exists(cache.resolve("roms")));
        var scanned = repository.scan(RomDiscoveryTest.failWarning());
        assertEquals(1, scanned.size());
        assertEquals("new", scanned.getFirst().name());
        assertEquals(scanned, repository.initialize(RomDiscoveryTest.failWarning()));
    }

    @Test
    void corruptCacheIsRebuiltAndLegacyCoreReceiptsArePreservedSeparately() throws Exception {
        Path library = Files.createDirectories(root.resolve("roms")),
                cache = Files.createDirectories(root.resolve("cache/server"));
        Files.write(library.resolve("game.nes"), HomebrewRom.bytes());
        Files.writeString(cache.resolve("catalog.json"), "broken json");
        List<String> warnings = new ArrayList<>();
        assertEquals(
                1,
                new RomRepository(library, cache, RomFormats.ALL, 1024 * 1024)
                        .initialize(warnings::add)
                        .size());
        assertFalse(warnings.isEmpty());
        Path legacy =
                Files.createDirectories(root.resolve("config/cores/managed/pool/platform/core"));
        Files.writeString(legacy.resolve("receipt.properties"), "sha256=pinned");
        Files.write(legacy.resolve("core.so"), new byte[] {1, 2, 3});
        CacheMigration.prepare(root.resolve("config"), root.resolve("cache"), false);
        CacheMigration.prepare(root.resolve("config"), root.resolve("cache"), true);
        for (String side : List.of("client", "server"))
            assertEquals(
                    "sha256=pinned",
                    Files.readString(
                            root.resolve(
                                    "cache/"
                                            + side
                                            + "/cores/pool/platform/core/receipt.properties")));
    }
}
