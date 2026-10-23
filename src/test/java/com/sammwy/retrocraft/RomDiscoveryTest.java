package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.roms.discovery.*;

class RomDiscoveryTest {
    @TempDir Path root;

    @Test
    void extensionsAreCaseInsensitiveAndDoNotValidateHeaders() throws Exception {
        Files.write(root.resolve("game.NeSv2"), new byte[] {8, 15, 1});
        var games = new RomScanner(RomFormats.ALL, 1024).scan(root, failWarning());
        assertEquals("nes", games.getFirst().cartridge());
        Files.move(root.resolve("game.NeSv2"), root.resolve("game.unknown"));
        assertTrue(new RomScanner(RomFormats.ALL, 1024).scan(root, failWarning()).isEmpty());
    }

    @Test
    void headersAndArchiveMembersNeverDriveDiscovery() throws Exception {
        Files.write(root.resolve("header.dump"), HomebrewRom.bytes());
        Files.write(
                root.resolve("pack.zip"),
                RomArchiveTest.zip(Map.of("game.nes", HomebrewRom.bytes())));
        Files.write(root.resolve("game.nes.gz"), RomArchiveTest.gzip(HomebrewRom.bytes()));
        Files.write(
                root.resolve("pack.tar"), RomArchiveTest.tar("game.nes", HomebrewRom.bytes(), '0'));
        Files.write(
                root.resolve("pack.tar.gz"),
                RomArchiveTest.gzip(RomArchiveTest.tar("game.nes", HomebrewRom.bytes(), '0')));
        assertTrue(new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, failWarning()).isEmpty());
    }

    @Test
    void sharedDiscExtensionsRequirePlatformDirectoriesAndArcadeNamesStayWhole() throws Exception {
        Files.write(root.resolve("ambiguous.iso"), new byte[] {1});
        Files.write(root.resolve("unknown.bin"), new byte[] {2});
        Files.write(
                root.resolve("PACMAN.ZIP"),
                new byte[] {3}); // No ZIP parsing, even for a named arcade set.
        Files.write(
                root.resolve("neogeo.zip"),
                new byte[] {9}); // BIOS archives are never indexed as playable games.
        for (String folder : List.of("PS2", "PlayStation", "Dreamcast", "Wii", "GameCube"))
            Files.write(
                    Files.createDirectories(root.resolve(folder)).resolve("game.iso"),
                    new byte[] {4});
        var games = new RomScanner(RomFormats.ALL, 1024).scan(root, failWarning());
        assertEquals(
                Set.of("mame", "ps2", "ps1", "dreamcast", "wii", "gamecube"),
                new HashSet<>(games.stream().map(RomScanner.Game::cartridge).toList()));
        var arcadeGame =
                games.stream().filter(g -> g.cartridge().equals("mame")).findFirst().orElseThrow();
        assertEquals("pacman.zip", arcadeGame.filename());
        assertEquals("Pac-Man (Midway)", arcadeGame.name());
        assertEquals("neogeo", ArcadeSets.requiredBios("mslug.zip").id());
        assertEquals("kof99", ArcadeSets.machine("kof99p.zip").parent());
        assertTrue(
                games.stream()
                        .allMatch(g -> g.source().ordinal() == -1 && g.source().member() == null));
    }

    @Test
    void recursiveDiscoveryDeduplicatesByContentAndSkipsEscapingSymlinks() throws Exception {
        Path a = Files.createDirectories(root.resolve("random/mixed")),
                b = Files.createDirectories(root.resolve("wrong-console"));
        Files.write(a.resolve("game.NES"), HomebrewRom.bytes());
        Files.write(b.resolve("renamed.nes"), HomebrewRom.bytes());
        Path external = Files.createTempFile("retrocraft-external", ".nes");
        try {
            Files.write(external, new byte[] {1, 2});
            Files.createSymbolicLink(root.resolve("escape.nes"), external);
            var games = new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, failWarning());
            assertEquals(1, games.size());
            assertEquals(InstanceStore.sha256(a.resolve("game.NES")), games.getFirst().id());
        } finally {
            Files.deleteIfExists(external);
        }
    }

    static Consumer<String> failWarning() {
        return warning -> fail(warning);
    }
}
