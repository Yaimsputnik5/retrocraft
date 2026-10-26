package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;

public final class NintendoDsConsole extends HandheldConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "nds",
                    MediaKind.CARD,
                    DiscoveryStrategy.extension("nds", "ids"),
                    BiosRequirement.NONE,
                    "desmume");

    public NintendoDsConsole() {
        super("nds", "Nintendo DS", SYSTEM);
    }
}
