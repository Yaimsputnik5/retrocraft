package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;

/** Mega Drive and its North American Genesis twin share cartridges and shell. */
public final class MegaDriveConsole extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "megadrive",
                    MediaKind.CARTRIDGE,
                    DiscoveryStrategy.extension("md", "gen", "smd")
                            .or(
                                    DiscoveryStrategy.extension("bin")
                                            .inDir("megadrive", "mega drive", "genesis")),
                    BiosRequirement.NONE,
                    "genesis_plus_gx");
    public static final Bounds SHELL = new Bounds(1, 2.43, 15, 13.5, 4.39);

    public MegaDriveConsole() {
        super("megadrive", "Mega Drive", SYSTEM, SHELL);
    }
}
