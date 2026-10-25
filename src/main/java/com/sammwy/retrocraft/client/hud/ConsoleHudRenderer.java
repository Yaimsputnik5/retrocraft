package com.sammwy.retrocraft.client.hud;

import java.util.function.Consumer;

import net.minecraft.client.gui.DrawContext;

/** Client-only extension point. The host draws video between the background and overlay passes. */
public interface ConsoleHudRenderer {
    ConsoleHudConfig config();

    /**
     * Evaluated again after resize/layout changes; return the actual video and frame rectangles.
     */
    ConsoleHudLayout layout(ConsoleHudContext context);

    void renderBackground(DrawContext graphics, ConsoleHudContext context, ConsoleHudLayout layout);

    void renderOverlay(DrawContext graphics, ConsoleHudContext context, ConsoleHudLayout layout);

    /** Exactly one layout calculation for the background → video → overlay sandwich. */
    default ConsoleHudLayout render(
            DrawContext graphics,
            ConsoleHudContext context,
            Consumer<ConsoleHudLayout.Panel> video) {
        var composition = layout(context);
        renderBackground(graphics, context, composition);
        composition.panels().forEach(video);
        renderOverlay(graphics, context, composition);
        return composition;
    }
}
