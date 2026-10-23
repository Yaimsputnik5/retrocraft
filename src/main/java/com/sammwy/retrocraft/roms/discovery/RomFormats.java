package com.sammwy.retrocraft.roms.discovery;

import java.util.*;

/** System detection is independent of directories and Minecraft registries. */
public final class RomFormats {
    public static final int HEADER_BYTES = 131_072;
    private static final byte[] GB_LOGO = HexFormat.of().parseHex("ceed6666cc0d000b03730083000c000d0008111f8889000edccc6ee6ddddd999bbbb67636e0eecccdddc999fbbb9333e");
    public static final Map<String, DiscoveryStrategy> ALL;
    static {
        var formats = new LinkedHashMap<String, DiscoveryStrategy>();
        formats.put("nes", DiscoveryStrategy.extension("nes", "nesv2").or(DiscoveryStrategy.magic(0x4e, 0x45, 0x53, 0x1a)).require((name, b) -> b.length >= 16 && b[0] == 'N' && b[1] == 'E' && b[2] == 'S' && b[3] == 0x1a));
        formats.put("snes", DiscoveryStrategy.extension("sfc", "smc", "swc", "fig").or(DiscoveryStrategy.matching("sfc", (name, b) -> snes(b))));
        formats.put("gb", DiscoveryStrategy.extension("gb", "dmg").or(DiscoveryStrategy.matching("gb", (name, b) -> gameBoy(b) && !color(b))));
        formats.put("gbc", DiscoveryStrategy.extension("gbc", "cgb").or(DiscoveryStrategy.matching("gbc", (name, b) -> gameBoy(b) && color(b))));
        formats.put("gba", DiscoveryStrategy.extension("gba", "agb").or(DiscoveryStrategy.magicAt(4, 0x24, 0xff, 0xae, 0x51, 0x69, 0x9a, 0xa2, 0x21).require((name, b) -> b.length > 0xb2 && (b[0xb2] & 255) == 0x96)));
        var sega = DiscoveryStrategy.magicAt(0x100, 'S', 'E', 'G', 'A');
        formats.put("megadrive", DiscoveryStrategy.extension("md", "gen", "smd").or(DiscoveryStrategy.extension("bin").require(sega::matchesMagic)).or(sega));
        formats.put("n64", DiscoveryStrategy.extension("z64", "n64", "v64").or(DiscoveryStrategy.magic(0x80, 0x37, 0x12, 0x40)).or(DiscoveryStrategy.magic(0x37, 0x80, 0x40, 0x12)).or(DiscoveryStrategy.magic(0x40, 0x12, 0x37, 0x80)));
        formats.put("mame", DiscoveryStrategy.extension("zip").require((name, b) -> ArcadeSets.contains(name)));
        // Shared disc formats (.iso/.chd/.cue/...) only match inside a platform-named folder; console-unique ones match anywhere.
        formats.put("ps2", DiscoveryStrategy.extension("iso", "chd", "cue", "img", "mdf", "nrg", "gz").inDir("ps2", "playstation 2", "playstation2")
                .or(DiscoveryStrategy.extension("cso", "zso")));
        formats.put("ps1", DiscoveryStrategy.extension("iso", "chd", "cue", "img", "ccd", "m3u").inDir(
                new String[]{"ps1", "psx", "playstation"}, new String[]{"ps2", "playstation 2", "playstation2"})
                .or(DiscoveryStrategy.extension("pbp", "minis")));
        formats.put("dreamcast", DiscoveryStrategy.extension("iso", "chd", "cue", "img").inDir("dreamcast", "dc")
                .or(DiscoveryStrategy.extension("cdi", "gdi", "m1", "dsf")));
        formats.put("wii", DiscoveryStrategy.extension("iso", "chd", "img").inDir("wii")
                .or(DiscoveryStrategy.extension("wbfs", "rvz", "wad", "dol", "elf")));
        formats.put("gamecube", DiscoveryStrategy.extension("iso", "chd", "img").inDir("gamecube", "ngc")
                .or(DiscoveryStrategy.extension("gcm", "gcz", "nkit", "tgc")));
        ALL = Collections.unmodifiableMap(formats);
    }
    private static boolean gameBoy(byte[] b) { return b.length > 0x143 && Arrays.equals(b, 0x104, 0x134, GB_LOGO, 0, GB_LOGO.length); }
    private static boolean color(byte[] b) { return (b[0x143] & 0x80) != 0; }
    private static boolean snes(byte[] b) {
        for (int offset : new int[]{0x7fc0, 0xffc0, 0x81c0, 0x101c0}) {
            if (b.length <= offset + 0x3f) continue;
            int inverse = (b[offset + 0x1c] & 255) | (b[offset + 0x1d] & 255) << 8;
            int checksum = (b[offset + 0x1e] & 255) | (b[offset + 0x1f] & 255) << 8;
            int reset = (b[offset + 0x3c] & 255) | (b[offset + 0x3d] & 255) << 8;
            int map = b[offset + 0x15] & 255;
            if (checksum != 0 && (checksum ^ inverse) == 0xffff && reset >= 0x8000 && (map & 0xe0) == 0x20) return true;
        }
        return false;
    }
    private RomFormats() {}
}
