package com.sammwy.retrocraft.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ContentProperties {
    public static <T> T get(Registry<T> registry, ResourceLocation id) {
        return registry.get(id);
    }

    public static Item.Properties item(ResourceLocation id, Item.Properties properties) {
        return properties;
    }

    public static BlockBehaviour.Properties block(
            ResourceLocation id, BlockBehaviour.Properties properties) {
        return properties;
    }

    private ContentProperties() {}
}
