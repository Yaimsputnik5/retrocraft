package com.sammwy.retrocraft.compat;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class Vertices {
    public static void vertex(
            VertexConsumer buffer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float u,
            float v,
            int rgb,
            int overlay,
            int light,
            int normalZ) {
        buffer.vertex(matrix, x, y, z)
                .color(rgb, rgb, rgb, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(0, 0, normalZ)
                .endVertex();
    }

    private Vertices() {}
}
