package com.sammwy.retrocraft;

import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.roms.discovery.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RomDiscoveryTest {
    @TempDir Path root;
    @Test void extensionsWinBeforeMagicAndAreCaseInsensitive() throws Exception {
        Map<String, DiscoveryStrategy> formats = new LinkedHashMap<>();
        formats.put("magic", DiscoveryStrategy.magic(0x08, 0x0f));
        formats.put("extension", DiscoveryStrategy.extension("NES", "NESV2"));
        Files.write(root.resolve("game.NeSv2"), new byte[]{8, 15, 1});
        assertEquals("extension", new RomScanner(formats, 1024).scan(root, failWarning()).getFirst().cartridge());
        Files.move(root.resolve("game.NeSv2"), root.resolve("game.unknown"));
        assertEquals("magic", new RomScanner(formats, 1024).scan(root, failWarning()).getFirst().cartridge());
    }
    @Test void builtinHeadersRecognizeNesGameBoyColorGbaSegaN64AndSnesWithoutExtensions() throws Exception {
        Files.write(root.resolve("nes.dump"), HomebrewRom.bytes());
        byte[] gb = new byte[0x150];
        byte[] logo = HexFormat.of().parseHex("ceed6666cc0d000b03730083000c000d0008111f8889000edccc6ee6ddddd999bbbb67636e0eecccdddc999fbbb9333e");
        System.arraycopy(logo, 0, gb, 0x104, logo.length); Files.write(root.resolve("gb.dump"), gb);
        gb[0x143] = (byte) 0xc0; Files.write(root.resolve("gbc.dump"), gb);
        byte[] gba = new byte[0xc0]; byte[] gbaMagic = HexFormat.of().parseHex("24ffae51699aa221");
        System.arraycopy(gbaMagic, 0, gba, 4, gbaMagic.length); gba[0xb2] = (byte) 0x96; Files.write(root.resolve("gba.dump"), gba);
        byte[] md = new byte[0x200]; System.arraycopy(new byte[]{'S','E','G','A'}, 0, md, 0x100, 4); Files.write(root.resolve("md.dump"), md);
        Files.write(root.resolve("n64.dump"), new byte[]{(byte) 0x80, 0x37, 0x12, 0x40});
        Files.write(root.resolve("n64-swapped.dump"), new byte[]{0x37, (byte) 0x80, 0x40, 0x12});
        byte[] snes = new byte[0x8000]; snes[0x7fd5] = 0x20; snes[0x7fdc] = (byte) 0xfe; snes[0x7fdd] = (byte) 0xff; snes[0x7fde] = 1; snes[0x7ffd] = (byte) 0x80;
        Files.write(root.resolve("snes.dump"), snes);
        assertEquals(Set.of("nes", "gb", "gbc", "gba", "megadrive", "n64", "snes"), new HashSet<>(new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, failWarning()).stream().map(RomScanner.Game::cartridge).toList()));
    }
    @Test void recursiveDiscoveryIgnoresFolderNamesAndDeduplicatesByContent() throws Exception {
        Path a = Files.createDirectories(root.resolve("random/mixed")), b = Files.createDirectories(root.resolve("wrong-console"));
        Files.write(a.resolve("game.NES"), HomebrewRom.bytes()); Files.write(b.resolve("renamed.nes"), HomebrewRom.bytes());
        Files.write(b.resolve("broken.nes"), new byte[100]); Files.write(b.resolve("unknown.bin"), new byte[200]);
        var games = new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, failWarning());
        assertEquals(1, games.size()); assertEquals("nes", games.getFirst().cartridge());
        assertEquals(InstanceStore.sha256(a.resolve("game.NES")), games.getFirst().id());
    }
    static java.util.function.Consumer<String> failWarning() { return warning -> fail(warning); }
}
