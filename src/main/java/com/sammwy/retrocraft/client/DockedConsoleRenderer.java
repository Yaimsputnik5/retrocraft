package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.block.*;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** The casing is a baked vanilla model; only the actual inserted cartridge needs a renderer. */
public final class DockedConsoleRenderer implements BlockEntityRenderer<DockedConsoleBlockEntity> {
    private final ItemRenderer items;
    public DockedConsoleRenderer(BlockEntityRendererFactory.Context context) { items = context.getItemRenderer(); }
    @Override public void render(DockedConsoleBlockEntity console, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
        if (console.cartridge().isEmpty()) return;
        String id = console.definition().id().getPath();
        if (id.equals("mame_console")) return; // The PCB is inside the arcade cabinet.
        matrices.push();
        matrices.translate(.5, 0, .5);
        float rotation = switch (console.getCachedState().get(DockedConsoleBlock.FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        };
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
        if (id.equals("nes_console")) {
            matrices.translate(0, .33, -.37);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.scale(.4f, .4f, .4f);
        } else {
            matrices.translate(0, id.equals("n64_console") ? .48 : .44, -.10);
            matrices.scale(.62f, .62f, .62f);
        }
        items.renderItem(console.cartridge(), ModelTransformationMode.FIXED, light, overlay, matrices, vertices, console.getWorld(), (int) console.getPos().asLong());
        matrices.pop();
    }
}
