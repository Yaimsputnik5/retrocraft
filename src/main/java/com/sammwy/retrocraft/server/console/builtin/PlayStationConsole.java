package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

/** Memory card slot 1 is always the libretro save; slot 2 and the Multitap are components. */
public final class PlayStationConsole extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "ps1",
                    MediaKind.DISC,
                    DiscoveryStrategy.extension("iso", "chd", "cue", "img", "ccd", "m3u")
                            .inDir(
                                    new String[] {"ps1", "psx", "playstation"},
                                    new String[] {"ps2", "playstation 2", "playstation2"})
                            .or(DiscoveryStrategy.extension("pbp", "minis"))
                            .require(name -> !PspConsole.DISCOVERY.matches(name)),
                    BiosRequirement.NONE,
                    Components.BEETLE_PSX);

    public PlayStationConsole() {
        super(
                "ps1",
                "PlayStation",
                SYSTEM,
                new Bounds(1, 2.42, 15, 13.5, 3.94),
                ComponentSlot.of(
                                "ps1_memory_card_2",
                                "Memory Card Slot 2",
                                Components.PS1_MEMORY_CARD)
                        .whenEmpty(
                                Components.BEETLE_PSX,
                                com.sammwy.retrocraft.cores.worker.HardwareProfile.options(
                                        "beetle_psx_enable_memcard1", "disabled")),
                ComponentSlot.of("ps1_port_1", "Controller Port 1", Components.PS1_MULTITAP));
    }

    @Override
    public int controllerPorts(java.util.List<ConsoleComponent> installed) {
        return installed.contains(Components.PS1_MULTITAP) ? 4 : 2;
    }
}
