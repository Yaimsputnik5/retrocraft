package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.Components;

public final class GameCubeConsole extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "gamecube",
                    MediaKind.DISC,
                    DiscoveryStrategy.extension("iso", "chd", "img")
                            .inDir("gamecube", "ngc")
                            .or(DiscoveryStrategy.extension("gcm", "gcz", "nkit", "tgc")),
                    BiosRequirement.NONE,
                    Components.DOLPHIN);

    public GameCubeConsole() {
        super("gamecube", "GameCube", SYSTEM, new Bounds(2.35, 2.36, 13.65, 15.2, 9.35));
    }
}
