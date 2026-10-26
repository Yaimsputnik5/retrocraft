package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.RomLibrary;

class LibraryIntegrityTest {
    @TempDir Path root;

    @Test
    void missingPathsCannotEscapeThroughSymlinkedAncestors() throws Exception {
        var library = new RomLibrary(root.resolve("library"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(library.root().resolve("link"), outside);
        assertThrows(
                IOException.class, () -> library.destination("link/new-folder", "payload.bin"));
        assertFalse(Files.exists(outside.resolve("new-folder")));
        assertTrue(library.list("").entries().isEmpty());
    }

    @Test
    void libraryRootCannotBeRenamedOrMoved() throws Exception {
        var library = new RomLibrary(root.resolve("library"));
        assertThrows(IOException.class, () -> library.rename(".", "escaped"));
        assertThrows(IOException.class, () -> library.move(".", ""));
        assertThrows(IOException.class, () -> library.delete("."));
        assertTrue(Files.isDirectory(library.root()));
    }

    @Test
    void explicitBiosArchiveInstallPreservesExistingFilesAndSupportsGzip() throws Exception {
        var library = new RomLibrary(root.resolve("bios"));
        library.install("core", "existing.bin", new byte[] {1}, false);
        library.install(
                "core",
                "bios.zip",
                ArchiveFixtures.zip(
                        Map.of("existing.bin", new byte[] {2}, "new.bin", new byte[] {3})),
                true);
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(library.read("core/existing.bin")));
        assertArrayEquals(new byte[] {3}, Files.readAllBytes(library.read("core/new.bin")));
        library.install("core", "gzip.bin.gz", ArchiveFixtures.gzip(new byte[] {4}), true);
        assertArrayEquals(new byte[] {4}, Files.readAllBytes(library.read("core/gzip.bin")));
    }
}
