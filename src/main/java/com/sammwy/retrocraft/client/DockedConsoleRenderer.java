package com.sammwy.retrocraft.client;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.joml.Matrix4f;

import com.sammwy.retrocraft.server.block.*;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
    /** Roof of the cabinet (model units above 32 cannot live in the block's own JSON model). */
    public static final Map<String, Identifier> TOP_MODELS =
            Arrays.stream(ArcadeSkin.values())
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    ArcadeSkin::model,
                                    skin ->
                                            Identifier.of(
                                                    "retrocraft",
                                                    "block/" + skin.model() + "_top")));

    public static List<Identifier> arcadeModels() {
        return Arrays.stream(ArcadeSkin.values())
                .flatMap(
                        skin ->
                                Stream.of(
                                        TOP_MODELS.get(skin.model()),
                                        Identifier.of("retrocraft", "block/" + skin.model()),
                                        Identifier.of(
                                                "retrocraft", "block/" + skin.model() + "_on")))
                .toList();
    }

    private static String model(DockedConsoleBlockEntity console) {
        return console.hasInternalRom()
                ? ArcadeSkin.of(console.cartridge().getName().getString()).model()
                : console.definition().id().getPath();
    }

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
        if (console.definition().isArcade()) {
            if (console.hasInternalRom()) casing(console, matrices, vertices, light, overlay);
            roof(console, matrices, vertices, overlay);
            if (console.hasInternalRom() && model(console).equals(ArcadeSkin.GENERIC.model()))
                marquee(console, matrices, vertices);
            crt(console, matrices, vertices);
        } else if (!console.cartridge().isEmpty()
                && (id.equals("snes_console")
                        || id.equals("n64_console")
                        || id.equals("genesis_console")
                        || id.equals("megadrive_console"))) {
            // Model units, matching the actual slot on each casing. Media models face south;
            // turn the label toward the console's north-facing controller ports. The NES-001
            // encloses its cartridge behind the front-loading door, so it has no exposed media.
            float scale;
            if (id.equals("snes_console")) {
                matrices.translate(0, 8.1 / 16, 1.5 / 16);
                scale = .54f;
            } else if (id.equals("n64_console")) {
                matrices.translate(0, 7.45 / 16, 1.5 / 16);
                scale = .55f;
            } else {
                matrices.translate(-1.0 / 16, 7.05 / 16, .9 / 16);
                scale = .59f;
            }
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.scale(scale, scale, scale);
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

    private static void casing(
            DockedConsoleBlockEntity console,
            MatrixStack matrices,
            VertexConsumerProvider vertices,
            int light,
            int overlay) {
        var client = MinecraftClient.getInstance();
        var model =
                client.getBakedModelManager()
                        .getModel(
                                Identifier.of(
                                        "retrocraft",
                                        "block/"
                                                + model(console)
                                                + (console.instance() == null ? "" : "_on")));
        matrices.push();
        matrices.translate(-.5, 0, -.5);
        client.getBlockRenderManager()
                .getModelRenderer()
                .render(
                        matrices.peek(),
                        vertices.getBuffer(RenderLayer.getSolid()),
                        console.getCachedState(),
                        model,
                        1,
                        1,
                        1,
                        light,
                        overlay);
        matrices.pop();
    }

    /** Unrecognized sets still have a personalized marquee, without guessing another game's art. */
    private static void marquee(
            DockedConsoleBlockEntity console,
            MatrixStack matrices,
            VertexConsumerProvider vertices) {
        var font = MinecraftClient.getInstance().textRenderer;
        String name = console.cartridge().getName().getString();
        if (name.isEmpty()) return;
        float scale = Math.min(.008f, 1.1f / Math.max(1, font.getWidth(name)));
        matrices.push();
        matrices.translate(0, 35.5 / 16, 2.77 / 16 - .5);
        matrices.scale(-scale, -scale, scale);
        font.draw(
                name,
                -font.getWidth(name) / 2f,
                -4,
                0xffbcebf2,
                false,
                matrices.peek().getPositionMatrix(),
                vertices,
                TextRenderer.TextLayerType.NORMAL,
                0xff141923,
                0xf000f0);
        matrices.pop();
    }

    private static void roof(
            DockedConsoleBlockEntity console,
            MatrixStack matrices,
            VertexConsumerProvider vertices,
            int overlay) {
        var client = MinecraftClient.getInstance();
        Identifier top = TOP_MODELS.get(model(console));
        if (top == null) return;
        BakedModel model = client.getBakedModelManager().getModel(top);
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
        // Tilt around the same lower glass edge as the baked model, keeping live video flush.
        matrices.push();
        matrices.translate(0, ArcadeCabinet.SCREEN_Y0 / 16, ArcadeCabinet.SCREEN_Z / 16 - .5);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ArcadeCabinet.SCREEN_TILT));
        matrices.translate(0, -ArcadeCabinet.SCREEN_Y0 / 16, .5 - ArcadeCabinet.SCREEN_Z / 16);
        drawScreen(
                ScreenTextures.get(console.instance()),
                matrices,
                vertices,
                ArcadeCabinet.SCREEN_X0,
                ArcadeCabinet.SCREEN_X1,
                ArcadeCabinet.SCREEN_Y0,
                ArcadeCabinet.SCREEN_Y1,
                ArcadeCabinet.SCREEN_Z);
        matrices.pop();
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
