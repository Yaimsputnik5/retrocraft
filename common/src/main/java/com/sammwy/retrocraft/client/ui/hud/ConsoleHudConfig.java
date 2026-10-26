package com.sammwy.retrocraft.client.ui.hud;

import com.sammwy.retrocraft.cores.stream.DualScreen;

/** Hardware metadata used before the first video frame and by the layout selector. */
public record ConsoleHudConfig(
        int nativeWidth,
        int nativeHeight,
        int screenOff,
        DualScreen dualScreen,
        String layoutPreference) {
    public ConsoleHudConfig {
        if (nativeWidth < 1 || nativeHeight < 1)
            throw new IllegalArgumentException("Invalid native video size");
        if ((dualScreen == null) != (layoutPreference == null)
                || layoutPreference != null && layoutPreference.isBlank())
            throw new IllegalArgumentException("Dual screens need a layout preference key");
    }

    public static ConsoleHudConfig single(int width, int height, int screenOff) {
        return new ConsoleHudConfig(width, height, screenOff, null, null);
    }
}
