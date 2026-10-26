package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

public final class GameBoyConsole extends HandheldConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "gb",
                    MediaKind.CARTRIDGE,
                    DiscoveryStrategy.extension("gb", "dmg"),
                    BiosRequirement.NONE,
                    Components.GAMBATTE);

    /** Game Boy and Game Boy Color share the serial port, so their cables link either model. */
    public static final ComponentSlot LINK_PORT =
            ComponentSlot.of("gb_link_port", "Link Port", Components.GB_LINK_CABLE);

    public GameBoyConsole() {
        super("gb", "Game Boy", SYSTEM, LINK_PORT);
    }
}
