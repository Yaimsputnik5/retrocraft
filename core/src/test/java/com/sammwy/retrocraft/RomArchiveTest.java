package com.sammwy.retrocraft;

import static com.sammwy.retrocraft.ArchiveFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.*;

/**
 * Archive utilities remain available for explicit BIOS installation, never automatic ROM discovery.
 */
class RomArchiveTest {
    @TempDir Path root;

    @Test
    void explicitArchiveReadsSupportZipGzipTarAndTarGzip() throws Exception {
        byte[] rom = HomebrewRom.bytes();
        for (String type : List.of("zip", "gz", "tar", "tar.gz")) {
            String name = type.equals("gz") ? "game.nes.gz" : "collection." + type;
            byte[] archive =
                    switch (type) {
                        case "zip" -> zip(Map.of("game.nes", rom));
                        case "tar" -> tar("game.nes", rom, '0');
                        case "tar.gz" -> gzip(tar("game.nes", rom, '0'));
                        default -> gzip(rom);
                    };
            Path file = Files.write(root.resolve(name), archive);
            var member = RomArchives.list(file).getFirst();
            assertArrayEquals(
                    rom, RomArchives.read(file, member.name(), member.ordinal(), 1024 * 1024));
        }
    }

    @Test
    void explicitArchiveReadsRejectTraversalLinksTruncationAndOversizedMembers() throws Exception {
        Path traversal =
                Files.write(
                        root.resolve("traversal.zip"),
                        zip(
                                Map.of(
                                        "../escape.nes",
                                        HomebrewRom.bytes(),
                                        "/absolute.nes",
                                        HomebrewRom.bytes())));
        assertTrue(RomArchives.list(traversal).isEmpty());
        assertTrue(
                RomArchives.list(
                                Files.write(
                                        root.resolve("link.tar"),
                                        tar("linked.nes", new byte[0], '2')))
                        .isEmpty());
        Path truncated =
                Files.write(
                        root.resolve("truncated.tar"),
                        Arrays.copyOf(tar("broken.nes", HomebrewRom.bytes(), '0'), 700));
        assertThrows(IOException.class, () -> RomArchives.list(truncated));
        Path large = Files.write(root.resolve("large.nes.gz"), gzip(new byte[100_000]));
        assertThrows(IOException.class, () -> RomArchives.read(large, "large.nes", 0, 50_000));
        assertThrows(
                IllegalArgumentException.class, () -> new RomSource("../escape.nes", null, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RomSource("safe.zip", "../escape.nes", 0));
    }

    @Test
    void explicitTarReadsSupportPaxAndGnuLongNames() throws Exception {
        byte[] rom = HomebrewRom.bytes();
        String name = "nested/" + "a".repeat(130) + ".nes";
        Path pax =
                Files.write(
                        root.resolve("pax.tar"),
                        concat(
                                tarEntry("PaxHeader", pax("path", name), 'x'),
                                tar("short", rom, '0')));
        var member = RomArchives.list(pax).getFirst();
        assertEquals(name, member.name());
        assertArrayEquals(rom, RomArchives.read(pax, name, member.ordinal(), 1024 * 1024));
        Path gnu =
                Files.write(
                        root.resolve("gnu.tar"),
                        concat(
                                tarEntry(
                                        "././@LongLink",
                                        (name + "\0").getBytes(StandardCharsets.UTF_8),
                                        'L'),
                                tar("short", rom, '0')));
        assertEquals(name, RomArchives.list(gnu).getFirst().name());
    }
}
