package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import java.util.*;

/** Decoded emulator frames as GPU textures, shared by the play screen and the in-world cabinet renderer. */
final class ScreenTextures {
    private static final long FROZEN_TTL = 60_000_000_000L;
    record Entry(Identifier id, int width, int height, long at) {}
    private static final Map<UUID, NativeImageBackedTexture> TEXTURES = new HashMap<>();
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    /** Streams that stopped but whose console may still be on: keep the last frame on the glass briefly. */
    private static final Map<UUID, Long> FROZEN = new HashMap<>();

    static Entry upload(UUID instance, FrameCodec.Frame frame) {
        FROZEN.remove(instance);
        Entry entry = ENTRIES.get(instance);
        NativeImageBackedTexture texture = TEXTURES.get(instance);
        if (texture == null || entry.width != frame.width() || entry.height != frame.height()) {
            release(instance);
            texture = new NativeImageBackedTexture(new NativeImage(frame.width(), frame.height(), false));
            texture.setFilter(false, false);
            Identifier id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("retrocraft/console", texture);
            entry = new Entry(id, frame.width(), frame.height(), 0);
            TEXTURES.put(instance, texture); ENTRIES.put(instance, entry);
        }
        NativeImage image = texture.getImage();
        byte[] rgb = frame.rgb();
        for (int y = 0; y < entry.height; y++) {
            for (int x = 0; x < entry.width; x++) {
                int i = (y * entry.width + x) * 3;
                image.setColor(x, y, 0xff000000 | (rgb[i] & 255) | ((rgb[i + 1] & 255) << 8) | ((rgb[i + 2] & 255) << 16));
            }
        }
        texture.upload();
        entry = new Entry(entry.id(), entry.width(), entry.height(), System.nanoTime());
        ENTRIES.put(instance, entry);
        return entry;
    }

    static Entry get(UUID instance) { return instance == null ? null : ENTRIES.get(instance); }

    /** The stream is actually producing frames right now (not a frozen leftover). */
    static Entry live(UUID instance) {
        Entry entry = get(instance);
        return entry != null && System.nanoTime() - entry.at < 1_000_000_000L ? entry : null;
    }

    static void freeze(UUID instance) { if (ENTRIES.containsKey(instance)) FROZEN.put(instance, System.nanoTime()); }

    static void tick() {
        long now = System.nanoTime();
        for (UUID id : List.copyOf(FROZEN.keySet())) if (now - FROZEN.get(id) > FROZEN_TTL) release(id);
    }

    static void clear() { for (UUID id : List.copyOf(ENTRIES.keySet())) release(id); }

    static void release(UUID instance) {
        FROZEN.remove(instance);
        Entry entry = ENTRIES.remove(instance);
        if (TEXTURES.remove(instance) != null && entry != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
    }
    private ScreenTextures() {}
}
