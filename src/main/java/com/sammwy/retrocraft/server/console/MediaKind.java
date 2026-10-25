package com.sammwy.retrocraft.server.console;

import java.util.Locale;

/** Physical game media; its name becomes the item ID suffix, e.g. {@code ps2_disc}. */
public enum MediaKind {
    CARTRIDGE,
    CARD,
    DISC,
    UMD,
    BOARD;

    public String suffix() {
        return name().toLowerCase(Locale.ROOT);
    }
}
