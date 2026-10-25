package com.sammwy.retrocraft.client.hud;

import java.util.*;

import com.sammwy.retrocraft.cores.stream.DualScreen;

import net.minecraft.client.gui.DrawContext;

/** Default two-layer implementation. Override layout or either pass for a custom handheld. */
public class TexturedConsoleHudRenderer implements ConsoleHudRenderer {
    private final ConsoleHudConfig config;
    private final HudFrame frame;

    public TexturedConsoleHudRenderer(ConsoleHudConfig config, HudFrame frame) {
        this.config = Objects.requireNonNull(config);
        this.frame = Objects.requireNonNull(frame);
    }

    @Override
    public ConsoleHudConfig config() {
        return config;
    }

    public HudFrame frame() {
        return frame;
    }

    @Override
    public ConsoleHudLayout layout(ConsoleHudContext context) {
        var viewport = context.viewport();
        var border = frame.border();
        if (config.dualScreen() != null) {
            var pair =
                    context.arrangement()
                            .fit(
                                    config.dualScreen(),
                                    viewport.x(),
                                    viewport.y(),
                                    viewport.width(),
                                    viewport.height(),
                                    border.maximum());
            var top = pair.top();
            var bottom = pair.bottom();
            return new ConsoleHudLayout(
                    List.of(
                            panel(
                                    new HudRect(top.x(), top.y(), top.width(), top.height()),
                                    config.dualScreen().crop(false),
                                    false),
                            panel(
                                    new HudRect(
                                            bottom.x(),
                                            bottom.y(),
                                            bottom.width(),
                                            bottom.height()),
                                    config.dualScreen().crop(true),
                                    true)));
        }
        int availableW = Math.max(1, viewport.width() - border.left() - border.right()),
                availableH = Math.max(1, viewport.height() - border.top() - border.bottom());
        double scale =
                Math.min(
                        (double) availableW / context.frameWidth(),
                        (double) availableH / context.frameHeight());
        int w = Math.max(1, (int) Math.floor(context.frameWidth() * scale)),
                h = Math.max(1, (int) Math.floor(context.frameHeight() * scale));
        int x = viewport.x() + border.left() + (availableW - w) / 2,
                y = viewport.y() + border.top() + (availableH - h) / 2;
        return new ConsoleHudLayout(
                List.of(panel(new HudRect(x, y, w, h), new DualScreen.Crop(0, 0, 1, 1), false)));
    }

    private ConsoleHudLayout.Panel panel(HudRect video, DualScreen.Crop crop, boolean touch) {
        return new ConsoleHudLayout.Panel(video, video.expand(frame.border()), crop, touch);
    }

    @Override
    public void renderBackground(
            DrawContext graphics, ConsoleHudContext context, ConsoleHudLayout layout) {
        for (var panel : layout.panels()) frame.draw(graphics, frame.background(), panel.frame());
    }

    @Override
    public void renderOverlay(
            DrawContext graphics, ConsoleHudContext context, ConsoleHudLayout layout) {
        for (var panel : layout.panels()) frame.draw(graphics, frame.overlay(), panel.frame());
    }
}
