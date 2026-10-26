package com.sammwy.retrocraft.compat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class ExtraModels {
    private static final Map<ResourceLocation, ExtraModelKey<BlockStateModel>> KEYS =
            new HashMap<>();

    public static void register(Collection<ResourceLocation> models) {
        for (var id : models) KEYS.put(id, ExtraModelKey.create(id::toString));
        ModelLoadingPlugin.register(
                plugin ->
                        KEYS.forEach(
                                (id, key) ->
                                        plugin.addModel(
                                                key, SimpleUnbakedExtraModel.blockStateModel(id))));
    }

    public static void draw(
            ResourceLocation id,
            DockedConsoleBlockEntity console,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            boolean ambient) {
        var model = Minecraft.getInstance().getModelManager().getModel(KEYS.get(id));
        if (model == null) return;
        var state = console.getBlockState();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        if (ambient)
            renderer.tesselateBlock(
                    console.getLevel(),
                    model.collectParts(RandomSource.create()),
                    state,
                    console.getBlockPos().above(2),
                    poses,
                    buffers.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(state)),
                    false,
                    overlay);
        else
            renderer.renderModel(
                    poses.last(),
                    buffers.getBuffer(RenderType.solid()),
                    model,
                    1,
                    1,
                    1,
                    light,
                    overlay);
    }

    private ExtraModels() {}
}
