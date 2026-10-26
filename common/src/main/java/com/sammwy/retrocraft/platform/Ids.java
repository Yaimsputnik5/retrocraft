package com.sammwy.retrocraft.platform;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/** Stable identifier construction across the Java 21 Minecraft releases. */
public final class Ids {
    public static ResourceLocation of(String namespace, String path) {
        return parse(namespace + ":" + path);
    }

    public static ResourceLocation parse(String id) {
        return Objects.requireNonNull(ResourceLocation.tryParse(id), "Invalid resource ID: " + id);
    }

    private Ids() {}
}
