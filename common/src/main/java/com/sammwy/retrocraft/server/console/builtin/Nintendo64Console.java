package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

/**
 * The memory expansion bay runs at 4 MB with its Jumper Pak; games that need 8 MB (Donkey Kong 64,
 * Majora's Mask) require the Expansion Pak. Controller paks plug into every pad.
 */
public final class Nintendo64Console extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "n64",
                    MediaKind.CARTRIDGE,
                    DiscoveryStrategy.extension("z64", "n64", "v64"),
                    BiosRequirement.NONE,
                    Components.PARALLEL_N64);

    public Nintendo64Console() {
        super(
                "n64",
                "Nintendo 64",
                SYSTEM,
                new Bounds(1, 2.43, 15, 13.5, 5.24),
                ComponentSlot.of(
                                "n64_memory_bay",
                                "Memory Expansion Bay",
                                Components.N64_EXPANSION_PAK)
                        .whenEmpty(
                                Components.PARALLEL_N64,
                                HardwareProfile.options("parallel-n64-disable_expmem", "disabled")),
                ComponentSlot.of(
                                "n64_controller_pak",
                                "Controller Accessory",
                                Components.N64_CONTROLLER_PAK,
                                Components.N64_RUMBLE_PAK)
                        .whenEmpty(Components.PARALLEL_N64, Components.n64Paks("none")));
    }
}
