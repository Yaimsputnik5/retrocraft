package com.sammwy.retrocraft.compat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.DynamicTexture;
public final class TextureImages {
    public static DynamicTexture create(NativeImage image) { return new DynamicTexture(() -> "Retrocraft screen", image); }
    public static void beginBlend() { // The render pipeline owns blending. }
    public static void endBlend() { // The render pipeline owns blending. }
    private TextureImages() {}
}
