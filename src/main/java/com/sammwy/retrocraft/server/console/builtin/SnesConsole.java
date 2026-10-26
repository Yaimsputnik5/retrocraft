package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

/** The Super Multitap in port 2 opens all four controller slots. */
public final class SnesConsole extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "snes",
                    MediaKind.CARTRIDGE,
                    DiscoveryStrategy.extension("sfc", "smc", "swc", "fig"),
                    BiosRequirement.NONE,
                    Components.SNES9X);

    public SnesConsole() {
        super(
                "snes",
                "SNES Console",
                SYSTEM,
                new Bounds(1, 2.43, 15, 13.5, 5.74),
                ComponentSlot.of("snes_port_2", "Controller Port 2", Components.SNES_MULTITAP));
    }

    @Override
    public int controllerPorts(java.util.List<ConsoleComponent> installed) {
        return installed.contains(Components.SNES_MULTITAP) ? 4 : 2;
    }
}
