package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.block.*;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Paints the bound console's live picture on the television glass; the cabinet itself is the baked
 * block model.
 */
public final class TvRenderer implements BlockEntityRenderer<TvBlockEntity> {
    public TvRenderer(BlockEntityRendererFactory.Context context) {}

    @Override
    public void render(
            TvBlockEntity tv,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertices,
            int light,
            int overlay) {
        var entry = ScreenTextures.get(tv.instance());
        if (entry == null) return;
        TvBlock.Size size = tv.size();
        matrices.push();
        matrices.translate(.5, 0, .5);
        float rotation =
                switch (tv.getCachedState().get(TvBlock.FACING)) {
                    case EAST -> -90;
                    case SOUTH -> 180;
                    case WEST -> 90;
                    default -> 0;
                };
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
        DockedConsoleRenderer.drawScreen(
                entry,
                matrices,
                vertices,
                size.screenX0,
                size.screenX1,
                size.screenY0,
                size.screenY1,
                size.screenZ);
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(TvBlockEntity tv) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
