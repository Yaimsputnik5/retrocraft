package com.sammwy.retrocraft.client.config;

import java.util.Locale;

import com.sammwy.retrocraft.cores.stream.DualScreen;

/** Shared geometry for drawing and hit testing, independent of the selected arrangement. */
public enum DualScreenLayout {
    VERTICAL,
    HORIZONTAL,
    TOP_LARGE,
    BOTTOM_LARGE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public DualScreenLayout next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static DualScreenLayout parse(String id) {
        if (id == null) throw new IllegalArgumentException("Missing dual screen layout");
        for (var value : values()) if (value.id().equals(id)) return value;
        throw new IllegalArgumentException("Unknown dual screen layout: " + id);
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }

    public record Pair(Rect top, Rect bottom) {}

    /** Each screen has its own rim; large modes place the small screen beside the large one. */
    public Pair fit(DualScreen system, int x, int y, int width, int height, int rim) {
        double topW = system.width, bottomW = system.touchWidth, screenH = system.screenHeight;
        double small = this == TOP_LARGE || this == BOTTOM_LARGE ? .36 : 1;
        double ts = this == BOTTOM_LARGE ? small : 1, bs = this == TOP_LARGE ? small : 1;
        int gap = rim * 2 + 8;
        double availableW = Math.max(1, width - 2 * rim - (this == VERTICAL ? 0 : gap));
        double availableH = Math.max(1, height - 2 * rim - (this == VERTICAL ? gap : 0));
        double scale =
                Math.min(
                        availableW
                                / (this == VERTICAL
                                        ? Math.max(topW, bottomW)
                                        : topW * ts + bottomW * bs),
                        availableH / (this == VERTICAL ? 2 * screenH : screenH));
        int tw = Math.max(1, (int) Math.floor(topW * ts * scale)),
                th = Math.max(1, (int) Math.floor(screenH * ts * scale)),
                bw = Math.max(1, (int) Math.floor(bottomW * bs * scale)),
                bh = Math.max(1, (int) Math.floor(screenH * bs * scale));
        if (this == VERTICAL) {
            int startY = y + (height - th - bh - gap) / 2;
            return new Pair(
                    new Rect(x + (width - tw) / 2, startY, tw, th),
                    new Rect(x + (width - bw) / 2, startY + th + gap, bw, bh));
        }
        int startX = x + (width - tw - bw - gap) / 2;
        return new Pair(
                new Rect(startX, y + (height - th) / 2, tw, th),
                new Rect(startX + tw + gap, y + (height - bh) / 2, bw, bh));
    }
}
