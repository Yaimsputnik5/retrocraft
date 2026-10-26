package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

/** Front-loading NES. Its second controller port takes the Zapper light gun. */
public final class NesConsole extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "nes",
                    MediaKind.CARTRIDGE,
                    DiscoveryStrategy.extension("nes", "nesv2"),
                    BiosRequirement.NONE,
                    Components.FCEUMM);
    public static final ComponentSlot PORT_2 =
            ComponentSlot.of("nes_port_2", "Controller Port 2", Components.NES_ZAPPER);

    public NesConsole() {
        super("nes", "NES Console", SYSTEM, new Bounds(1, 1.79, 15, 14, 5.85), PORT_2);
    }

    @Override
    public int controllerPorts(java.util.List<ConsoleComponent> installed) {
        return installed.contains(Components.NES_ZAPPER) ? 1 : 2;
    }
}
