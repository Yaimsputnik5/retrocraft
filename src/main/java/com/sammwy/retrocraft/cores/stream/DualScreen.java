package com.sammwy.retrocraft.cores.stream;

/** Canonical core output: upper screen above touch screen, no gap, native proportions. */
public enum DualScreen {
    NDS(256, 192, 256),
    THREEDS(400, 240, 320);
    public final int width, screenHeight, touchWidth;

    DualScreen(int width, int screenHeight, int touchWidth) {
        this.width = width;
        this.screenHeight = screenHeight;
        this.touchWidth = touchWidth;
    }

    public static DualScreen of(String console) {
        return switch (console) {
            case "nds" -> NDS;
            case "3ds" -> THREEDS;
            default -> null;
        };
    }

    public record Crop(float u0, float v0, float u1, float v1) {}

    /** Normalized UVs also work on the small PNG thumbnails. */
    public Crop crop(boolean bottom) {
        float inset = bottom ? (width - touchWidth) / (2f * width) : 0;
        return new Crop(inset, bottom ? .5f : 0, 1 - inset, bottom ? 1 : .5f);
    }

    /** Pointer coordinates refer to the complete core framebuffer, including its margins. */
    public short pointerX(double touchX) {
        return coordinate((width - touchWidth) / (2.0 * width) + touchX * touchWidth / width);
    }

    public short pointerY(double touchY) {
        return coordinate(.5 + touchY / 2);
    }

    private static short coordinate(double value) {
        return (short) Math.round(Math.max(-32767, Math.min(32767, value * 65534 - 32767)));
    }
}
