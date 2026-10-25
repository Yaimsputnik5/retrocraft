package com.sammwy.retrocraft.client.hud;

/** Fixed GUI-pixel margins, also used as the source borders of a nine-sliced texture. */
public record HudInsets(int left, int top, int right, int bottom) {
    public HudInsets {
        if (left < 0 || top < 0 || right < 0 || bottom < 0)
            throw new IllegalArgumentException("Negative HUD margin");
    }

    public int maximum() {
        return Math.max(Math.max(left, right), Math.max(top, bottom));
    }
}
