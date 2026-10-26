package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

/** Without an extension every pad is an upright Wii Remote. */
public final class WiiConsole extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "wii",
                    MediaKind.DISC,
                    DiscoveryStrategy.extension("iso", "chd", "img")
                            .inDir("wii")
                            .or(DiscoveryStrategy.extension("wbfs", "rvz", "wad", "dol", "elf"))
                            .require(name -> !PspConsole.DISCOVERY.matches(name)),
                    BiosRequirement.NONE,
                    Components.DOLPHIN);

    public WiiConsole() {
        super(
                "wii",
                "Wii",
                SYSTEM,
                new Bounds(5.15, 2.1, 10.85, 14.3, 14.39),
                ComponentSlot.of(
                        "wii_extension",
                        "Wii Remote Extension",
                        Components.WII_NUNCHUK,
                        Components.WII_CLASSIC_CONTROLLER));
    }
}
