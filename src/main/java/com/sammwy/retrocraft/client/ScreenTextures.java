package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import java.util.*;

/** Decoded emulator frames as GPU textures, shared by the play screen and the in-world cabinet renderer. */
final class ScreenTextures {
    record Entry(Identifier id, int width, int height) {}
    private static final Map<UUID, NativeImageBackedTexture> TEXTURES = new HashMap<>();
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    static Entry upload(UUID instance, FrameCodec.Frame frame) {
        Entry entry = ENTRIES.get(instance);
        NativeImageBackedTexture texture = TEXTURES.get(instance);
        if (texture == null || entry.width != frame.width() || entry.height != frame.height()) {
            release(instance);
            texture = new NativeImageBackedTexture(new NativeImage(frame.width(), frame.height(), false));
            texture.setFilter(false, false);
            Identifier id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("retrocraft/console", texture);
            entry = new Entry(id, frame.width(), frame.height());
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
        return entry;
    }

    static Entry get(UUID instance) { return instance == null ? null : ENTRIES.get(instance); }

    static void release(UUID instance) {
        Entry entry = ENTRIES.remove(instance);
        if (TEXTURES.remove(instance) != null && entry != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
    }
    private ScreenTextures() {}
}
