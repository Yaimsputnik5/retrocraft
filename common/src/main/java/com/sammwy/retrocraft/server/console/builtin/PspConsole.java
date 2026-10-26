package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;

public final class PspConsole extends HandheldConsole {
    /** Shared disc formats only count inside a PSP folder; other disc systems exclude these. */
    public static final DiscoveryStrategy DISCOVERY =
            DiscoveryStrategy.extension("iso", "chd", "cso", "pbp", "elf")
                    .inDir("psp", "playstation portable")
                    .or(DiscoveryStrategy.extension("prx"));

    public static final GameSystem SYSTEM =
            GameSystem.of("psp", MediaKind.UMD, DISCOVERY, BiosRequirement.NONE, "ppsspp");

    public PspConsole() {
        super("psp", "PlayStation Portable (PSP)", SYSTEM);
    }
}
