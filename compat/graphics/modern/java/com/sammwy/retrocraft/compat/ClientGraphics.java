package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class ClientGraphics {
    public static void color(GuiGraphics graphics, float r, float g, float b, float a) {
        RenderSystem.setShaderColor(r, g, b, a);
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
                RenderType::guiTextured,
                texture,
                x,
                y,
                u,
                v,
                width,
                height,
                sourceWidth,
                sourceHeight,
                textureWidth,
                textureHeight);
    }

    public static void pixel(NativeImage image, int x, int y, int abgr) {
        image.setPixel(x, y, (abgr & 0xff00ff00) | ((abgr & 0xff) << 16) | ((abgr >>> 16) & 0xff));
    }

    private ClientGraphics() {}
}
