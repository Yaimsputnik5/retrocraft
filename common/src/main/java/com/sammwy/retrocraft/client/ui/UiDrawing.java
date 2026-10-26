package com.sammwy.retrocraft.client.ui;

import org.joml.Vector4f;

import com.sammwy.retrocraft.compat.ClientGraphics;

import net.minecraft.client.gui.GuiGraphics;

final class UiDrawing {
    private UiDrawing() {}

    static void pop(GuiGraphics context, float cx, float cy, float visibility) {
        float scale = .92f + .08f * visibility;
        context.pose().translate(cx, cy + 6f * (1f - visibility), 0);
        context.pose().scale(scale, scale, 1);
        context.pose().translate(-cx, -cy, 0);
        ClientGraphics.color(context, 1, 1, 1, visibility);
    }

    /** Center the visible pixels, including glyphs with blank rows or uneven padding. */
    static void centeredGlyph(
            GuiGraphics context, String[] rows, int x, int y, int width, int height, int color) {
        int minX = Integer.MAX_VALUE, minY = rows.length, maxX = -1, maxY = -1;
        for (int row = 0; row < rows.length; row++)
            for (int col = 0; col < rows[row].length(); col++)
                if (rows[row].charAt(col) == 'X') {
                    minX = Math.min(minX, col);
                    minY = Math.min(minY, row);
                    maxX = Math.max(maxX, col);
                    maxY = row;
                }
        if (maxX < 0) return;
        context.pose().pushPose();
        try {
            context.pose()
                    .translate(
                            x + (width - (maxX - minX + 1)) / 2f - minX,
                            y + (height - (maxY - minY + 1)) / 2f - minY,
                            0);
            for (int row = minY; row <= maxY; row++)
                for (int col = 0; col < rows[row].length(); col++)
                    if (rows[row].charAt(col) == 'X')
                        context.fill(col, row, col + 1, row + 1, color);
        } finally {
            context.pose().popPose();
        }
    }

    /** DrawContext's scissor rectangles do not follow its matrix stack. */
    static void scissor(GuiGraphics context, int x1, int y1, int x2, int y2) {
        var matrix = context.pose().last().pose();
        var min = matrix.transform(new Vector4f(x1, y1, 0, 1));
        var max = matrix.transform(new Vector4f(x2, y2, 0, 1));
        context.enableScissor(
                (int) Math.floor(min.x), (int) Math.floor(min.y),
                (int) Math.ceil(max.x), (int) Math.ceil(max.y));
    }
}
