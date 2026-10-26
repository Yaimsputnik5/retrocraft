package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;

/** mGBA's libretro build has no link support, so the GBA exposes no link port. */
public final class GameBoyAdvanceConsole extends HandheldConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "gba",
                    MediaKind.CARTRIDGE,
                    DiscoveryStrategy.extension("gba", "agb"),
                    BiosRequirement.NONE,
                    "mgba");

    public GameBoyAdvanceConsole() {
        super("gba", "Game Boy Advance", SYSTEM);
    }
}
