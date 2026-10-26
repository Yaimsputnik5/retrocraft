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
    void pspSharedFormatsRequireItsFolderAndKeepOtherConsoleMappings() throws Exception {
        var expected =
                Map.of(
                        "PSP/homebrew.PBP",
                        "psp",
                        "PSP/disc.CSO",
                        "psp",
                        "PSP/game.ISO",
                        "psp",
                        "PlayStation Portable/app.ELF",
                        "psp",
                        "psp/compressed.CHD",
                        "psp",
                        "anywhere/test.PRX",
                        "psp",
                        "classic.pbp",
                        "ps1",
                        "PS2/racer.cso",
                        "ps2",
                        "Wii/demo.elf",
                        "wii");
        int i = 0;
        for (String file : expected.keySet()) {
            Path path = root.resolve(file);
            Files.createDirectories(path.getParent());
            Files.write(path, new byte[] {(byte) i++});
        }
        var games = new RomScanner(RomFormats.ALL, 1024).scan(root, failWarning());
        assertEquals(expected.size(), games.size());
        for (var game : games) assertEquals(expected.get(game.source().file()), game.cartridge());
        assertFalse(RomFormats.ALL.get("ps1").matches("PSP/homebrew.PBP"));
        assertFalse(RomFormats.ALL.get("ps2").matches("PSP/disc.CSO"));
        assertFalse(RomFormats.ALL.get("wii").matches("PSP/app.ELF"));
    }

    @Test
    void dsAndDecrypted3dsFormatsAreDiscoveredWithoutAPlatformFolder() throws Exception {
        String[] names = {
            "touch.NDS",
            "demo.ids",
            "adventure.3DS",
            "homebrew.3dsx",
            "cart.cci",
            "title.cxi",
            "title.app"
        };
        for (int i = 0; i < names.length; i++)
            Files.write(root.resolve(names[i]), new byte[] {(byte) i});
        Files.write(root.resolve("installer.cia"), new byte[] {100});
        var games = new RomScanner(RomFormats.ALL, 1024).scan(root, failWarning());
        assertEquals(7, games.size());
        assertEquals(2, games.stream().filter(g -> g.cartridge().equals("nds")).count());
        assertEquals(5, games.stream().filter(g -> g.cartridge().equals("3ds")).count());
    }

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
                ArchiveFixtures.zip(Map.of("game.nes", HomebrewRom.bytes())));
        Files.write(root.resolve("game.nes.gz"), ArchiveFixtures.gzip(HomebrewRom.bytes()));
        Files.write(
                root.resolve("pack.tar"),
                ArchiveFixtures.tar("game.nes", HomebrewRom.bytes(), '0'));
        Files.write(
                root.resolve("pack.tar.gz"),
                ArchiveFixtures.gzip(ArchiveFixtures.tar("game.nes", HomebrewRom.bytes(), '0')));
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
                Set.of("arcade", "ps2", "ps1", "dreamcast", "wii", "gamecube"),
                new HashSet<>(games.stream().map(RomScanner.Game::cartridge).toList()));
        var arcadeGame =
                games.stream()
                        .filter(g -> g.cartridge().equals("arcade"))
                        .findFirst()
                        .orElseThrow();
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
