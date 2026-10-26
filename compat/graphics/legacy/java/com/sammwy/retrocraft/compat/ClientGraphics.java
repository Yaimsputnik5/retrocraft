package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ClientGraphics {
    public static void color(GuiGraphics graphics, float r, float g, float b, float a) {
        graphics.setColor(r, g, b, a);
    }

    public static void blit(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            float u,
            float v,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight) {
        graphics.blit(
                texture,
                x,
                y,
                width,
                height,
                u,
                v,
                sourceWidth,
                sourceHeight,
                textureWidth,
                textureHeight);
    }

    public static void pixel(NativeImage image, int x, int y, int abgr) {
        image.setPixelRGBA(x, y, abgr);
    }

    private ClientGraphics() {}
}
