package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.*;

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
}
