package com.sammwy.retrocraft.client.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sammwy.retrocraft.client.ui.hud.HudRect;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Where each in-world picture landed on screen last frame, in GUI pixels, so a light gun can aim at
 * a television or cabinet glass with the mouse.
 */
public final class ScreenProjection {
    private record Capture(HudRect rect, long at) {}

    private static final Map<Identifier, Capture> CAPTURES = new ConcurrentHashMap<>();
    private static final long STALE_NANOS = 250_000_000L;

    /** Called while rendering a block entity: world projection and camera rotation are bound. */
    static void capture(
            Identifier texture, Matrix4f model, float x0, float y0, float x1, float y1, float z) {
        var window = MinecraftClient.getInstance().getWindow();
        Matrix4f transform =
                new Matrix4f(RenderSystem.getProjectionMatrix())
                        .mul(RenderSystem.getModelViewMatrix())
                        .mul(model);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] corner : new float[][] {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}}) {
            Vector4f point = transform.transform(new Vector4f(corner[0], corner[1], z, 1));
            if (point.w <= 0) return;
            float gx = (point.x / point.w + 1) / 2 * window.getScaledWidth();
            float gy = (1 - point.y / point.w) / 2 * window.getScaledHeight();
            minX = Math.min(minX, gx);
            minY = Math.min(minY, gy);
            maxX = Math.max(maxX, gx);
            maxY = Math.max(maxY, gy);
        }
        int w = Math.round(maxX - minX), h = Math.round(maxY - minY);
        if (w < 2 || h < 2) return;
        CAPTURES.put(
                texture,
                new Capture(
                        new HudRect(Math.round(minX), Math.round(minY), w, h), System.nanoTime()));
    }

    /** The picture's on-screen rectangle if it was drawn in the last few frames. */
    public static HudRect get(Identifier texture) {
        Capture capture = texture == null ? null : CAPTURES.get(texture);
        if (capture == null || System.nanoTime() - capture.at > STALE_NANOS) return null;
        return capture.rect;
    }

    private ScreenProjection() {}
}
