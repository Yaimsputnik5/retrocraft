package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.ArcadeSets;
import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;

/** Cabinet variants share the board system; internal-ROM cabinets bake in one game each. */
public final class ArcadeMachine extends ArcadeConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "arcade",
                    MediaKind.BOARD,
                    DiscoveryStrategy.extension("zip").require(ArcadeSets::contains),
                    BiosRequirement.ARCADE_SET,
                    "mame2003");

    public ArcadeMachine(String path, String name, boolean listed, boolean internalRom) {
        super(path, name, SYSTEM, listed, internalRom);
    }
}
