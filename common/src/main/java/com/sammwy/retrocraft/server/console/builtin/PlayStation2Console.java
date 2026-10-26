package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;

public final class PlayStation2Console extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "ps2",
                    MediaKind.DISC,
                    DiscoveryStrategy.extension("iso", "chd", "cue", "img", "mdf", "nrg", "gz")
                            .inDir("ps2", "playstation 2", "playstation2")
                            .or(DiscoveryStrategy.extension("cso", "zso"))
                            .require(name -> !PspConsole.DISCOVERY.matches(name)),
                    BiosRequirement.anyFile("PS2 BIOS", ".bin"),
                    "pcsx2");

    public PlayStation2Console() {
        super("ps2", "PlayStation 2", SYSTEM, new Bounds(1, 2.01, 15, 13.5, 4.69));
    }
}
