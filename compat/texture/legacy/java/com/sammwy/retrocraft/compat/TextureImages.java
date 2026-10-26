package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.DynamicTexture;

public final class TextureImages {
    public static DynamicTexture create(NativeImage image) {
        return new DynamicTexture(image);
    }

    public static void beginBlend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static void endBlend() {
        RenderSystem.disableBlend();
    }

    private TextureImages() {}
}
