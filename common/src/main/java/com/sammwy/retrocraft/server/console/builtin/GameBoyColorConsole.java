package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.Components;

public final class GameBoyColorConsole extends HandheldConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "gbc",
                    MediaKind.CARTRIDGE,
                    DiscoveryStrategy.extension("gbc", "cgb"),
                    BiosRequirement.NONE,
                    Components.GAMBATTE);

    public GameBoyColorConsole() {
        super("gbc", "Game Boy Color", SYSTEM, GameBoyConsole.LINK_PORT);
    }
}
