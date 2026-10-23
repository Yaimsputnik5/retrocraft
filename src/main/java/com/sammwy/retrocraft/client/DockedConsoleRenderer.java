package com.sammwy.retrocraft.client;

import org.joml.Matrix4f;

import com.sammwy.retrocraft.server.block.*;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

/**
 * The casing is a baked vanilla model; this draws the inserted cartridge and, on the arcade
 * cabinet, the live CRT picture.
 */
public final class DockedConsoleRenderer implements BlockEntityRenderer<DockedConsoleBlockEntity> {
    // Cabinet CRT glass in model units (front = north, symmetric in x): x 1.5..14.5, y 16..26,
    // marquee face at z 2.5.
    private static final float CRT_X0 = 1.5f,
            CRT_X1 = 14.5f,
            CRT_Y0 = 16,
            CRT_Y1 = 26,
            CRT_Z = 2.5f;

    /** Roof of the cabinet (model units above 32 cannot live in the block's own JSON model). */
    public static final Identifier TOP_MODEL =
            Identifier.of("retrocraft", "block/mame_console_top");

    private final ItemRenderer items;

    public DockedConsoleRenderer(BlockEntityRendererFactory.Context context) {
        items = context.getItemRenderer();
    }

    @Override
    public void render(
            DockedConsoleBlockEntity console,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertices,
            int light,
            int overlay) {
        String id = console.definition().id().getPath();
        matrices.push();
        matrices.translate(.5, 0, .5);
        float rotation =
                switch (console.getCachedState().get(DockedConsoleBlock.FACING)) {
                    case EAST -> -90;
                    case SOUTH -> 180;
                    case WEST -> 90;
                    default -> 0;
                };
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
        if (id.equals("mame_console")) {
            roof(console, matrices, vertices, overlay);
            crt(console, matrices, vertices);
        } else if (!console.cartridge().isEmpty()
                && (id.equals("nes_console")
                        || id.equals("snes_console")
                        || id.equals("n64_console")
                        || id.equals("genesis_console")
                        || id.equals("megadrive_console"))) {
            if (id.equals("nes_console")) {
                matrices.translate(0, .33, -.37);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                matrices.scale(.4f, .4f, .4f);
            } else {
                matrices.translate(0, id.equals("n64_console") ? .48 : .44, -.10);
                matrices.scale(.62f, .62f, .62f);
            }
            items.renderItem(
                    console.cartridge(),
                    ModelTransformationMode.FIXED,
                    light,
                    overlay,
                    matrices,
                    vertices,
                    console.getWorld(),
                    (int) console.getPos().asLong());
        }
        matrices.pop();
    }

    private static void roof(
            DockedConsoleBlockEntity console,
            MatrixStack matrices,
            VertexConsumerProvider vertices,
            int overlay) {
        var client = MinecraftClient.getInstance();
        BakedModel model =
                client.getBakedModelManager().getModel(TOP_MODEL); // Fabric interface injection
        if (model == null || console.getWorld() == null) return;
        BlockState state = console.getCachedState();
        matrices.push();
        matrices.translate(-.5, 2, -.5);
        client.getBlockRenderManager()
                .getModelRenderer()
                .render(
                        console.getWorld(),
                        model,
                        state,
                        console.getPos().up(2),
                        matrices,
                        vertices.getBuffer(RenderLayers.getMovingBlockLayer(state)),
                        false,
                        Random.create(),
                        state.getRenderingSeed(console.getPos()),
                        overlay);
        matrices.pop();
    }

    private static void crt(
            DockedConsoleBlockEntity console,
            MatrixStack matrices,
            VertexConsumerProvider vertices) {
        drawScreen(
                ScreenTextures.get(console.instance()),
                matrices,
                vertices,
                CRT_X0,
                CRT_X1,
                CRT_Y0,
                CRT_Y1,
                CRT_Z);
    }

    /**
     * Emulator frame fitted inside a glass rectangle (model units, front = north, matrices at the
     * block centre), full bright over a black letterbox.
     */
    static void drawScreen(
            ScreenTextures.Entry entry,
            MatrixStack matrices,
            VertexConsumerProvider vertices,
            float x0,
            float x1,
            float y0,
            float y1,
            float zUnits) {
        if (entry == null || !ClientPreferences.get().renderWorldScreens) return;
        float gw = (x1 - x0) / 16f, gh = (y1 - y0) / 16f;
        float fit = Math.min(gw / entry.width(), gh / entry.height());
        float w = entry.width() * fit, h = entry.height() * fit;
        float cx = (x0 + x1) / 32f - .5f, cy = (y0 + y1) / 32f, z = zUnits / 16f - .5f;
        var matrix = matrices.peek().getPositionMatrix();
        var black = vertices.getBuffer(RenderLayer.getEntitySolid(entry.id()));
        quad(
                black,
                matrix,
                cx - gw / 2,
                cy - gh / 2,
                cx + gw / 2,
                cy + gh / 2,
                z - .002f,
                0,
                0,
                0,
                0,
                0);
        var glass = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(entry.id()));
        quad(
                glass,
                matrix,
                cx - w / 2,
                cy - h / 2,
                cx + w / 2,
                cy + h / 2,
                z - .0045f,
                0,
                0,
                1,
                1,
                255);
    }

    /**
     * Quad facing -z (the cabinet front). Seen from the front, +x is the viewer's left, so u=0 maps
     * to x1.
     */
    private static void quad(
            VertexConsumer buffer,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            float z,
            float u0,
            float v0,
            float u1,
            float v1,
            int rgb) {
        int light = 0xf000f0, overlay = OverlayTexture.DEFAULT_UV;
        buffer.vertex(matrix, x1, y1, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u0, v0)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, -1);
        buffer.vertex(matrix, x1, y0, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u0, v1)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, -1);
        buffer.vertex(matrix, x0, y0, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u1, v1)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, -1);
        buffer.vertex(matrix, x0, y1, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u1, v0)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, -1);
    }

    @Override
    public boolean rendersOutsideBoundingBox(DockedConsoleBlockEntity console) {
        return true;
    }
}
