package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.client.render.Thumbnails;
import com.sammwy.retrocraft.compat.ThumbnailItem;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;

/** Render one LCD after all model layers, using the same item transform as the first layer. */
@Mixin(ItemStackRenderState.class)
abstract class ItemRendererMixin implements ThumbnailItem {
    @Shadow boolean isLeftHand;

    @Shadow
    public abstract ItemTransform transform();

    @Unique private ItemStack retrocraft$stack = ItemStack.EMPTY;

    @Override
    public void retrocraft$stack(ItemStack stack) {
        retrocraft$stack = stack;
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void retrocraft$clear(CallbackInfo ci) {
        retrocraft$stack = ItemStack.EMPTY;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void retrocraft$screen(
            PoseStack matrices,
            MultiBufferSource vertices,
            int light,
            int overlay,
            CallbackInfo ci) {
        if (retrocraft$stack.isEmpty()) return;
        matrices.pushPose();
        transform().apply(isLeftHand, matrices);
        matrices.translate(-0.5, -0.5, -0.5);
        Thumbnails.render(retrocraft$stack, matrices, vertices);
        matrices.popPose();
    }
}
