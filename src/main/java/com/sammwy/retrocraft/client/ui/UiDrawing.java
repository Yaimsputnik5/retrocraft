package com.sammwy.retrocraft.client.ui;

import org.joml.Vector4f;

import net.minecraft.client.gui.DrawContext;

final class UiDrawing {
    private UiDrawing() {}

    static void pop(DrawContext context, float cx, float cy, float visibility) {
        float scale = .92f + .08f * visibility;
        context.getMatrices().translate(cx, cy + 6f * (1f - visibility), 0);
        context.getMatrices().scale(scale, scale, 1);
        context.getMatrices().translate(-cx, -cy, 0);
        context.setShaderColor(1, 1, 1, visibility);
    }

    /** DrawContext's scissor rectangles do not follow its matrix stack. */
    static void scissor(DrawContext context, int x1, int y1, int x2, int y2) {
        var matrix = context.getMatrices().peek().getPositionMatrix();
        var min = matrix.transform(new Vector4f(x1, y1, 0, 1));
        var max = matrix.transform(new Vector4f(x2, y2, 0, 1));
        context.enableScissor(
                (int) Math.floor(min.x), (int) Math.floor(min.y),
                (int) Math.ceil(max.x), (int) Math.ceil(max.y));
    }
}
