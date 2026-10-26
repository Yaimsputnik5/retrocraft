package com.sammwy.retrocraft.server.console.builtin;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

/** Each controller has two expansion slots: the VMU (saves) and the Puru Puru vibration pack. */
public final class DreamcastConsole extends DockedConsole {
    public static final GameSystem SYSTEM =
            GameSystem.of(
                    "dreamcast",
                    MediaKind.DISC,
                    DiscoveryStrategy.extension("iso", "chd", "cue", "img")
                            .inDir("dreamcast", "dc")
                            .or(DiscoveryStrategy.extension("cdi", "gdi", "m1", "dsf")),
                    BiosRequirement.NONE,
                    Components.FLYCAST);

    public DreamcastConsole() {
        super(
                "dreamcast",
                "Dreamcast",
                SYSTEM,
                new Bounds(1.35, 2.42, 14.4, 13.5, 4.22),
                ComponentSlot.of("dc_expansion_1", "Expansion Slot 1", Components.DC_VMU)
                        .whenEmpty(Components.FLYCAST, Components.dreamcastSlots(1, "None")),
                ComponentSlot.of("dc_expansion_2", "Expansion Slot 2", Components.DC_PURU_PURU)
                        .whenEmpty(Components.FLYCAST, Components.dreamcastSlots(2, "None")));
    }
}
