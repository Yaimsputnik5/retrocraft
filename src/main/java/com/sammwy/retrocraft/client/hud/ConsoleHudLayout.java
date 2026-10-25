package com.sammwy.retrocraft.client.hud;

import java.util.*;

import com.sammwy.retrocraft.cores.stream.DualScreen;

/** One immutable composition is shared by both rendering passes, video and touch input. */
public record ConsoleHudLayout(List<Panel> panels) {
    public ConsoleHudLayout {
        panels = List.copyOf(panels);
        if (panels.isEmpty()) throw new IllegalArgumentException("HUD needs a video panel");
    }

    public Optional<Panel> touchAt(double x, double y) {
        return panels.stream().filter(p -> p.touch() && p.video().contains(x, y)).findFirst();
    }

    /** Crop coordinates address the complete core framebuffer, including any native margins. */
    public record Panel(HudRect video, HudRect frame, DualScreen.Crop crop, boolean touch) {
        public Panel {
            Objects.requireNonNull(video);
            Objects.requireNonNull(frame);
            Objects.requireNonNull(crop);
            if (video.x() < frame.x()
                    || video.y() < frame.y()
                    || video.x() + video.width() > frame.x() + frame.width()
                    || video.y() + video.height() > frame.y() + frame.height())
                throw new IllegalArgumentException("Video must fit its HUD frame");
            if (!(crop.u0() >= 0
                    && crop.u1() <= 1
                    && crop.u0() < crop.u1()
                    && crop.v0() >= 0
                    && crop.v1() <= 1
                    && crop.v0() < crop.v1()))
                throw new IllegalArgumentException("Invalid framebuffer crop");
        }

        public short pointerX(double x) {
            return coordinate(
                    crop.u0() + (x - video.x()) / video.width() * (crop.u1() - crop.u0()));
        }

        public short pointerY(double y) {
            return coordinate(
                    crop.v0() + (y - video.y()) / video.height() * (crop.v1() - crop.v0()));
        }

        private static short coordinate(double value) {
            return (short) Math.round(Math.max(-32767, Math.min(32767, value * 65534 - 32767)));
        }
    }
}
