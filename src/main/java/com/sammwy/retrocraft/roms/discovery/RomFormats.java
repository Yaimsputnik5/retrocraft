package com.sammwy.retrocraft.roms.discovery;

import java.util.*;

/** Deterministic filename and directory rules; discovery never sniffs ROM headers or opens archives. */
public final class RomFormats {
    public static final Map<String, DiscoveryStrategy> ALL;
    static {
        var formats = new LinkedHashMap<String, DiscoveryStrategy>();
        formats.put("nes", DiscoveryStrategy.extension("nes", "nesv2"));
        formats.put("snes", DiscoveryStrategy.extension("sfc", "smc", "swc", "fig"));
        formats.put("gb", DiscoveryStrategy.extension("gb", "dmg"));
        formats.put("gbc", DiscoveryStrategy.extension("gbc", "cgb"));
        formats.put("gba", DiscoveryStrategy.extension("gba", "agb"));
        formats.put("megadrive", DiscoveryStrategy.extension("md", "gen", "smd")
                .or(DiscoveryStrategy.extension("bin").inDir("megadrive", "mega drive", "genesis")));
        formats.put("n64", DiscoveryStrategy.extension("z64", "n64", "v64"));
        formats.put("mame", DiscoveryStrategy.extension("zip").require(ArcadeSets::contains));
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
    private RomFormats() {}
}
