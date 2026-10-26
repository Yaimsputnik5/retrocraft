package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.client.render.Thumbnails;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the handheld's live LCD over the item model, in every render context (hands, ground, GUI,
 * frames).
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(
            method =
                    "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void retrocraft$handheldScreen(
            ItemStack stack,
            ItemDisplayContext mode,
            boolean leftHanded,
            PoseStack matrices,
            MultiBufferSource vertices,
            int light,
            int overlay,
            BakedModel model,
            CallbackInfo ci) {
        Thumbnails.render(stack, matrices, vertices);
    }
}
