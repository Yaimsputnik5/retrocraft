package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;

public final class Nintendo3dsConsole extends HandheldConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "3ds",
                    MediaKind.CARD,
                    DiscoveryStrategy.extension("3ds", "3dsx", "cci", "cxi", "app"),
                    BiosRequirement.NONE,
                    "citra");

    public Nintendo3dsConsole() {
        super("3ds", "Nintendo 3DS", SYSTEM);
    }
}
