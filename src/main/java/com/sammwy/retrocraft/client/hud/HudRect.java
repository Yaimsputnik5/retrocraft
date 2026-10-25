package com.sammwy.retrocraft.client.hud;

/** Screen-space GUI pixels, independent of framebuffer texels and window pixel density. */
public record HudRect(int x, int y, int width, int height) {
    public HudRect {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Empty HUD rectangle");
    }

    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    public HudRect expand(HudInsets insets) {
        return new HudRect(
                x - insets.left(),
                y - insets.top(),
                width + insets.left() + insets.right(),
                height + insets.top() + insets.bottom());
    }
}
