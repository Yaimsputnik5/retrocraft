package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.io.*;
import java.util.*;

/** Pocket-size copies of a handheld's live picture; the server refreshes them for whoever can see the item. */
public final class Thumbnails {
    private static final long MIN_AGE = 15_000_000_000L;
    /** LCD glass on each item model (model units, south face): x0 x1 y0 y1 z. */
    private static final Map<String, float[]> SCREENS = Map.of(
            "gb_handheld", new float[]{6, 10.6f, 9.4f, 13.6f, 9.4f},
            "gbc_handheld", new float[]{5.9f, 10.9f, 9.6f, 13.9f, 9.3f},
            "gba_handheld", new float[]{5.1f, 10.9f, 6.6f, 10.6f, 9.2f},
            "nes_handheld", new float[]{5.2f, 10.8f, 6, 10.6f, 9.3f});
    private record Entry(Identifier id, int width, int height, long at) {}
    private static final Map<UUID, Entry> entries = new HashMap<>();
    private static final Map<UUID, NativeImageBackedTexture> textures = new HashMap<>();

    static void accept(Packets.Thumbnail packet) {
        if (packet.png().length == 0) { release(packet.instance()); return; }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(packet.png()));
            Entry entry = entries.get(packet.instance());
            NativeImageBackedTexture texture = textures.get(packet.instance());
            if (texture == null || entry.width != image.getWidth() || entry.height != image.getHeight()) {
                release(packet.instance());
                texture = new NativeImageBackedTexture(image);
                texture.setFilter(false, false);
                Identifier id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("retrocraft/thumb", texture);
                textures.put(packet.instance(), texture);
                entry = new Entry(id, image.getWidth(), image.getHeight(), 0);
            } else texture.setImage(image);
            texture.upload();
            entries.put(packet.instance(), new Entry(entry.id, entry.width, entry.height, System.nanoTime()));
        } catch (IOException ignored) { }
    }

    static void tick() {
        long now = System.nanoTime(), maxAge = Math.max(MIN_AGE, RetrocraftClient.EMULATION.thumbnailSeconds() * 3_000_000_000L);
        for (UUID id : List.copyOf(entries.keySet())) if (now - entries.get(id).at > maxAge) release(id);
    }
    static void close() { for (UUID id : List.copyOf(entries.keySet())) release(id); }
    private static void release(UUID instance) {
        Entry entry = entries.remove(instance);
        if (textures.remove(instance) != null && entry != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
    }

    /** Overlays the console's picture on the item's LCD glass: live frames when streamed, the thumbnail otherwise. */
    public static void render(ItemStack stack, MatrixStack matrices, VertexConsumerProvider vertices) {
        if (!(stack.getItem() instanceof HandheldConsoleItem item)) return;
        UUID instance = HandheldConsoleItem.instance(stack);
        float[] rect = instance == null ? null : SCREENS.get(item.definition().id().getPath());
        if (rect == null) return;
        var live = ScreenTextures.live(instance);
        var thumb = live == null ? entries.get(instance) : null;
        if (live == null && thumb == null) return;
        Identifier texture = live != null ? live.id() : thumb.id();
        int w = live != null ? live.width() : thumb.width(), h = live != null ? live.height() : thumb.height();
        float x0 = rect[0] / 16, x1 = rect[1] / 16, y0 = rect[2] / 16, y1 = rect[3] / 16, z = rect[4] / 16 + .002f;
        float fit = Math.min((x1 - x0) / w, (y1 - y0) / h), fw = w * fit, fh = h * fit;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        quad(vertices.getBuffer(RenderLayer.getEntitySolid(texture)), matrix, x0, y0, x1, y1, z, 0);
        quad(vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)), matrix, cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2, z + .001f, 255);
    }

    /** South-facing quad over the LCD; viewed from +z the viewer's left is -x, so u=0 maps to x0 and v=0 to y1. */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int rgb) {
        int light = 0xf000f0, overlay = OverlayTexture.DEFAULT_UV;
        buffer.vertex(matrix, x0, y1, z).color(rgb, rgb, rgb, 255).texture(0, 0).overlay(overlay).light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x0, y0, z).color(rgb, rgb, rgb, 255).texture(0, 1).overlay(overlay).light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x1, y0, z).color(rgb, rgb, rgb, 255).texture(1, 1).overlay(overlay).light(light).normal(0, 0, 1);
        buffer.vertex(matrix, x1, y1, z).color(rgb, rgb, rgb, 255).texture(1, 0).overlay(overlay).light(light).normal(0, 0, 1);
    }
    private Thumbnails() {}
}
