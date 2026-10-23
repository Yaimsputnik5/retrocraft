package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.Map;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.roms.discovery.ArcadeSets;

class BiosFilesTest {
    @TempDir Path root;

    @Test
    void ps2UiUploadListsAndDeletesTheFirmwareWhereTheCoreExpectsIt() throws Exception {
        var library = new RomLibrary(root);
        String dir = BiosFiles.directory("pcsx2");
        library.install(dir, "PS2.BIN", new byte[] {1, 2, 3, 4}, true);
        assertArrayEquals(
                new byte[] {1, 2, 3, 4}, Files.readAllBytes(root.resolve("pcsx2/bios/PS2.BIN")));
        assertEquals("PS2.BIN", library.list(dir).entries().getFirst().name());
        library.delete(dir + "/PS2.BIN");
        assertTrue(library.list(dir).entries().isEmpty());
    }

    @Test
    void existingUploadsAreRecoveredIdempotentlyWithoutOverwritingInstalledFiles()
            throws Exception {
        var library = new RomLibrary(root);
        library.install("pcsx2", "old.bin", new byte[] {1}, false);
        library.install("pcsx2", "collision.bin", new byte[] {2}, false);
        library.install("pcsx2/bios", "collision.bin", new byte[] {3}, false);
        BiosFiles.migrateLegacy(root);
        BiosFiles.migrateLegacy(root);
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(root.resolve("pcsx2/bios/old.bin")));
        assertFalse(Files.exists(root.resolve("pcsx2/old.bin")));
        assertArrayEquals(
                new byte[] {3}, Files.readAllBytes(root.resolve("pcsx2/bios/collision.bin")));
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(root.resolve("pcsx2/collision.bin")));
        assertEquals("pcsx2/bios/old.bin", BiosFiles.canonical("pcsx2/old.bin"));
        assertEquals("pcsx2/bios/old.bin", BiosFiles.canonical("pcsx2/bios/old.bin"));
        assertEquals("pcsx_rearmed", BiosFiles.canonical("pcsx_rearmed"));
    }

    @Test
    void mameBiosUploadStaysZippedAndNormalizesModernNeoGeoMembers() throws Exception {
        var library = new RomLibrary(root);
        byte[] modernNeoGeo =
                RomArchiveTest.zip(
                        Map.of(
                                "000-lo.lo", new byte[] {1, 2, 3, 4},
                                "sm1.sm1", new byte[] {5, 6, 7, 8},
                                "sfix.sfix", new byte[] {9, 10, 11, 12},
                                "sp-e.sp1", new byte[] {13, 14, 15, 16}));
        assertEquals(1, library.install("mame2003", "neogeo.zip", modernNeoGeo, true));
        Path installed = root.resolve("mame2003/neogeo.zip");
        assertTrue(Files.isRegularFile(installed), "MAME BIOS zip must not be unpacked flat");
        try (ZipFile zf = new ZipFile(installed.toFile())) {
            assertNotNull(zf.getEntry("sfix.sfx"));
            assertNotNull(zf.getEntry("mame.sm1"));
            assertNotNull(zf.getEntry("mamelo.lo"));
            assertNotNull(zf.getEntry("sp-s2.sp1"));
        }
    }

    @Test
    void arcadeBiosIsSynthesizedFromMergedSetOrReportedWhenMissing() throws Exception {
        Path biosDir = Files.createDirectories(root.resolve("bios/mame2003"));
        Path romDir = Files.createDirectories(root.resolve("roms/mame"));
        Path splitKof =
                Files.write(
                        romDir.resolve("kof97.zip"),
                        RomArchiveTest.zip(Map.of("232-p1.bin", new byte[] {1, 2, 3})));
        var missing =
                assertThrows(
                        BiosFiles.MissingException.class,
                        () -> BiosFiles.ensureArcadeBios(biosDir, splitKof));
        assertEquals("mame2003/neogeo", missing.packetTarget());
        assertEquals("Neo-Geo", ArcadeSets.bios(missing.biosId()).title());

        Path mergedMslug =
                Files.write(
                        romDir.resolve("mslug.zip"),
                        RomArchiveTest.zip(
                                Map.of(
                                        "201-p1.bin", new byte[] {4, 5, 6},
                                        "000-lo.lo", new byte[] {1, 2},
                                        "sm1.sm1", new byte[] {3, 4},
                                        "sfix.sfix", new byte[] {5, 6},
                                        "sp-e.sp1", new byte[] {7, 8})));
        BiosFiles.ensureArcadeBios(biosDir, splitKof);
        assertTrue(Files.isRegularFile(biosDir.resolve("neogeo.zip")));
        assertTrue(Files.isRegularFile(romDir.resolve("neogeo.zip")));
        assertTrue(Files.isRegularFile(mergedMslug));
    }
}
