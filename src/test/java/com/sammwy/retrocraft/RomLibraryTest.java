package com.sammwy.retrocraft;

import com.sammwy.retrocraft.roms.RomLibrary;
import com.sammwy.retrocraft.roms.InstanceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RomLibraryTest {
    @TempDir Path root;
    @Test void listsOnlyValidNesFilesAndRejectsTraversalAndEscapingSymlinks() throws Exception {
        Path libraryPath = Files.createDirectory(root.resolve("roms"));
        byte[] rom = HomebrewRom.bytes();
        Files.write(libraryPath.resolve("game.nes"), rom);
        Files.write(libraryPath.resolve("UPPER.NES"), rom);
        Files.write(libraryPath.resolve("broken.nes"), new byte[64]);
        Files.write(libraryPath.resolve("notes.txt"), rom);
        Files.write(root.resolve("outside.nes"), rom);
        var library = new RomLibrary(libraryPath, 1024 * 1024);
        assertEquals(List.of("game.nes", "UPPER.NES"), library.list());
        assertThrows(IOException.class, () -> library.resolve("../outside.nes"));
        assertThrows(IOException.class, () -> library.resolve("..\\outside.nes"));
        assertThrows(IOException.class, () -> library.resolve("bad\u0000.nes"));
        assertThrows(IOException.class, () -> library.resolve("broken.nes"));
        try {
            Files.createSymbolicLink(libraryPath.resolve("escape.nes"), root.resolve("outside.nes"));
            assertThrows(IOException.class, () -> library.resolve("escape.nes"));
        } catch (UnsupportedOperationException ex) { /* symlinks unavailable on this platform */ }
    }

    @Test void gameIdUsesContentAndLibraryHonorsRegisteredCartridgeFormats() throws Exception {
        Path first = Files.write(root.resolve("first.gb"), new byte[]{1, 2, 3, 4});
        Path renamed = Files.write(root.resolve("renamed.gb"), Files.readAllBytes(first));
        assertEquals(InstanceStore.sha256(first), InstanceStore.sha256(renamed));
        Files.write(root.resolve("nes.nes"), HomebrewRom.bytes());
        assertEquals(List.of("first.gb", "renamed.gb"), new RomLibrary(root, 1024, Set.of("gb")).list());
        Files.write(renamed, new byte[]{1, 2, 3, 5});
        assertNotEquals(InstanceStore.sha256(first), InstanceStore.sha256(renamed));
    }
}
