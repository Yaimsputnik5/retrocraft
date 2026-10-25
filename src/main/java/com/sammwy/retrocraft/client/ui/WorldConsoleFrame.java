package com.sammwy.retrocraft.client.ui;

import com.sammwy.retrocraft.client.ui.hud.*;

import net.minecraft.client.gui.DrawContext;

/** Existing TV/arcade flat-view fallback; normal play uses TvRenderer/DockedConsoleRenderer. */
final class WorldConsoleFrame {
    static HudInsets insets(ConsoleTheme theme) {
        return theme.bezel() == ConsoleTheme.Bezel.ARCADE
                ? new HudInsets(14, 22, 14, 12)
                : new HudInsets(14, 12, 14, 22);
    }

    static void render(DrawContext context, ConsoleTheme theme, HudRect video) {
        int x = video.x(), y = video.y(), w = video.width(), h = video.height();
        var margins = insets(theme);
        var b = new Bezel(margins.left(), margins.top(), margins.right(), margins.bottom(), 6, 8);
        int shell = theme.shell(), dark = theme.bezelColor(), accent = theme.accent();
        int ox = x - b.left, oy = y - b.top, ow = w + b.left + b.right, oh = h + b.top + b.bottom;
        int ix = x - b.inner, iy = y - b.inner, iw = w + 2 * b.inner, ih = h + 2 * b.inner;
        rounded(context, ox, oy, ow, oh, shell, 3);
        context.fill(ox + 3, oy + 1, ox + ow - 3, oy + 2, ConsoleTheme.shade(shell, 1.25f));
        context.fill(ox + 3, oy + oh - 2, ox + ow - 3, oy + oh - 1, ConsoleTheme.shade(shell, .6f));
        switch (theme.bezel()) {
            case TV -> {
                rounded(
                        context,
                        ox + b.rim,
                        oy + b.rim,
                        ow - 2 * b.rim,
                        oh - b.rim - b.bottom + b.inner,
                        dark,
                        4);
                int strip = iy + ih + 4;
                context.fill(
                        ox + 10,
                        strip + 3,
                        ox + 10 + Math.min(60, ow / 4),
                        strip + 4,
                        ConsoleTheme.shade(shell, .7f));
                context.fill(
                        ox + 10,
                        strip + 6,
                        ox + 10 + Math.min(60, ow / 4),
                        strip + 7,
                        ConsoleTheme.shade(shell, .7f));
                context.fill(ox + 12, strip + 1, ox + 18, strip + 2, accent);
                for (int k = 0; k < 3; k++)
                    context.fill(
                            ox + ow - 40 + k * 9,
                            strip + 2,
                            ox + ow - 35 + k * 9,
                            strip + 7,
                            0xff8a8a92);
                led(context, ox + ow - 12, strip + 3, 0xffd9453a);
            }
            case ARCADE -> {
                context.fill(ox + 2, oy + 2, ox + 5, oy + oh - 2, 0xffc04f8a);
                context.fill(ox + 6, oy + 6, ox + 8, oy + oh - 6, 0xff2b7fc4);
                context.fill(ox + ow - 5, oy + 2, ox + ow - 2, oy + oh - 2, 0xffc04f8a);
                context.fill(ox + ow - 8, oy + 6, ox + ow - 6, oy + oh - 6, 0xff2b7fc4);
                context.fill(ox + 9, oy + 3, ox + ow - 9, oy + 13, 0xff1b1d3f);
                context.fill(ox + 9, oy + 3, ox + ow - 9, oy + 4, 0xff2b7fc4);
                context.fill(ox + 9, oy + 12, ox + ow - 9, oy + 13, 0xffc04f8a);
                context.fill(ox + ow / 2 - 24, oy + 6, ox + ow / 2 + 24, oy + 10, accent);
                led(context, ox + ow - 20, oy + 6, 0xff3ab064);
                context.fill(ox + b.rim + 3, oy + 15, ox + ow - b.rim - 3, oy + oh - b.rim, dark);
            }
            default -> throw new IllegalArgumentException("World frame needs TV or arcade theme");
        }
        context.fill(ix, iy, ix + iw, iy + ih, dark);
        context.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xff000000);
    }

    private static void rounded(DrawContext context, int x, int y, int w, int h, int color, int r) {
        context.fill(x + r, y, x + w - r, y + h, color);
        context.fill(x, y + r, x + r, y + h - r, color);
        context.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int k = 1; k < r; k++) {
            context.fill(x + r - k, y + k, x + r, y + k + 1, color);
            context.fill(x + w - r, y + k, x + w - r + k, y + k + 1, color);
            context.fill(x + r - k, y + h - k - 1, x + r, y + h - k, color);
            context.fill(x + w - r, y + h - k - 1, x + w - r + k, y + h - k, color);
        }
    }

    private static void led(DrawContext context, int x, int y, int color) {
        context.fill(x - 1, y - 1, x + 4, y + 4, 0xff0b0d12);
        context.fill(x, y, x + 3, y + 3, color);
        context.fill(x, y, x + 1, y + 1, ConsoleTheme.shade(color, 1.4f));
    }

    private record Bezel(int left, int top, int right, int bottom, int rim, int inner) {}

    private WorldConsoleFrame() {}
}
