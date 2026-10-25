package com.sammwy.retrocraft.client;

import java.io.*;
import java.util.*;

import org.joml.Matrix4f;

import com.sammwy.retrocraft.cores.stream.DualScreen;
import com.sammwy.retrocraft.cores.stream.ThumbnailCodec;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.net.Packets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Pocket-size copies of a handheld's live picture; the server refreshes them for whoever can see
 * the item.
 */
public final class Thumbnails {
    private static final long MIN_AGE = 15_000_000_000L;

    /** LCD glass on each item model (model units, south face): x0 x1 y0 y1 z. */
    private static final Map<String, float[]> SCREENS =
            Map.of(
                    "gb_handheld", new float[] {5.2f, 10.8f, 9, 14.04f, 9.22f},
                    "gbc_handheld", new float[] {5.2f, 10.8f, 9.35f, 14.39f, 9.22f},
                    "gba_handheld", new float[] {4.9f, 11.1f, 6.3f, 10.44f, 9.22f},
                    "nes_handheld", new float[] {5.15f, 10.85f, 6.2f, 10.55f, 9.22f},
                    "gba_sp_handheld", new float[] {4.4f, 11.6f, 9.4f, 14.2f, 8.72f},
                    "nds_handheld", new float[] {4.4f, 11.6f, 9.15f, 14.55f, 8.72f},
                    "3ds_handheld", new float[] {3, 13, 8.95f, 14.95f, 8.72f},
                    "psp_handheld", new float[] {3.7f, 12.3f, 5.76f, 10.6333f, 9.22f});

    private static final Map<String, float[]> TOUCH_SCREENS =
            Map.of(
                    "nds_handheld", new float[] {4.4f, 11.6f, 1.9f, 7.3f, 9.18f},
                    "3ds_handheld", new float[] {4, 12, 1.35f, 7.35f, 9.18f});

    private record Entry(Identifier id, int width, int height, long at, byte[] png) {}

    private static final Map<UUID, Entry> entries = new HashMap<>();
    private static final Map<UUID, NativeImageBackedTexture> textures = new HashMap<>();

    static void accept(Packets.Thumbnail packet) {
        if (!ClientPreferences.get().renderHandheldScreens) return;
        if (packet.png().length == 0) {
            release(packet.instance());
            return;
        }
        if (!ThumbnailCodec.valid(packet.png())) return;
        Entry cached = entries.get(packet.instance());
        if (cached != null && Arrays.equals(cached.png, packet.png())) {
            entries.put(
                    packet.instance(),
                    new Entry(
                            cached.id, cached.width, cached.height, System.nanoTime(), cached.png));
            return;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(packet.png()));
            Entry entry = entries.get(packet.instance());
            NativeImageBackedTexture texture = textures.get(packet.instance());
            if (texture == null
                    || entry.width != image.getWidth()
                    || entry.height != image.getHeight()) {
                release(packet.instance());
                texture = new NativeImageBackedTexture(image);
                texture.setFilter(false, false);
                Identifier id =
                        MinecraftClient.getInstance()
                                .getTextureManager()
                                .registerDynamicTexture("retrocraft/thumb", texture);
                textures.put(packet.instance(), texture);
                entry = new Entry(id, image.getWidth(), image.getHeight(), 0, packet.png());
            } else texture.setImage(image);
            texture.upload();
            entries.put(
                    packet.instance(),
                    new Entry(
                            entry.id, entry.width, entry.height, System.nanoTime(), packet.png()));
        } catch (IOException ignored) {
        }
    }

    static void tick() {
        long now = System.nanoTime(),
                maxAge =
                        Math.max(
                                MIN_AGE,
                                RetrocraftClient.EMULATION.thumbnailSeconds() * 3_000_000_000L);
        for (UUID id : List.copyOf(entries.keySet()))
            if (now - entries.get(id).at > maxAge) release(id);
    }

    static void close() {
        for (UUID id : List.copyOf(entries.keySet())) release(id);
    }

    private static void release(UUID instance) {
        Entry entry = entries.remove(instance);
        if (textures.remove(instance) != null && entry != null)
            MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
    }

    /**
     * Overlays the console's picture on the item's LCD glass: live frames when streamed, the
     * thumbnail otherwise.
     */
    public static void render(
            ItemStack stack, MatrixStack matrices, VertexConsumerProvider vertices) {
        if (!ClientPreferences.get().renderHandheldScreens) return;
        if (!(stack.getItem() instanceof HandheldConsoleItem item)) return;
        UUID instance = HandheldConsoleItem.instance(stack);
        float[] rect = instance == null ? null : SCREENS.get(item.definition().id().getPath());
        if (rect == null) return;
        var live = ScreenTextures.live(instance);
        var thumb = live == null ? entries.get(instance) : null;
        if (live == null && thumb == null) return;
        Identifier texture = live != null ? live.id() : thumb.id();
        int w = live != null ? live.width() : thumb.width(),
                h = live != null ? live.height() : thumb.height();
        String console = item.definition().id().getPath();
        DualScreen dual = DualScreen.of(console);
        if (dual != null || console.equals("gba_sp_handheld")) {
            matrices.push();
            fold(matrices, FoldablePose.LID_ANGLE);
            drawScreen(
                    rect,
                    texture,
                    w,
                    h,
                    dual == null ? null : dual.crop(false),
                    matrices,
                    vertices);
            matrices.pop();
        } else drawScreen(rect, texture, w, h, null, matrices, vertices);
        if (dual != null) {
            matrices.push();
            fold(matrices, FoldablePose.BASE_ANGLE);
            drawScreen(
                    TOUCH_SCREENS.get(console), texture, w, h, dual.crop(true), matrices, vertices);
            matrices.pop();
        }
    }

    private static void fold(MatrixStack matrices, float angle) {
        float x = FoldablePose.HINGE_X / 16,
                y = FoldablePose.HINGE_Y / 16,
                z = FoldablePose.HINGE_Z / 16;
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
        matrices.translate(-x, -y, -z);
    }

    private static void drawScreen(
            float[] rect,
            Identifier texture,
            int w,
            int h,
            DualScreen.Crop crop,
            MatrixStack matrices,
            VertexConsumerProvider vertices) {
        float u0 = crop == null ? 0 : crop.u0(),
                v0 = crop == null ? 0 : crop.v0(),
                u1 = crop == null ? 1 : crop.u1(),
                v1 = crop == null ? 1 : crop.v1();
        float x0 = rect[0] / 16,
                x1 = rect[1] / 16,
                y0 = rect[2] / 16,
                y1 = rect[3] / 16,
                z = rect[4] / 16 + .002f;
        float sw = w * (u1 - u0), sh = h * (v1 - v0);
        float fit = Math.min((x1 - x0) / sw, (y1 - y0) / sh), fw = sw * fit, fh = sh * fit;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        quad(
                vertices.getBuffer(RenderLayer.getEntitySolid(texture)),
                matrix,
                x0,
                y0,
                x1,
                y1,
                z,
                0,
                u0,
                v0,
                u1,
                v1);
        quad(
                vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)),
                matrix,
                cx - fw / 2,
                cy - fh / 2,
                cx + fw / 2,
                cy + fh / 2,
                z + .001f,
                255,
                u0,
                v0,
                u1,
                v1);
    }

    /**
     * South-facing quad over the LCD; viewed from +z the viewer's left is -x, so u=0 maps to x0 and
     * v=0 to y1.
     */
    private static void quad(
            VertexConsumer buffer,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            float z,
            int rgb,
            float u0,
            float v0,
            float u1,
            float v1) {
        int light = 0xf000f0, overlay = OverlayTexture.DEFAULT_UV;
        buffer.vertex(matrix, x0, y1, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u0, v0)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, 1);
        buffer.vertex(matrix, x0, y0, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u0, v1)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, 1);
        buffer.vertex(matrix, x1, y0, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u1, v1)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, 1);
        buffer.vertex(matrix, x1, y1, z)
                .color(rgb, rgb, rgb, 255)
                .texture(u1, v0)
                .overlay(overlay)
                .light(light)
                .normal(0, 0, 1);
    }

    private Thumbnails() {}
}
