package com.sammwy.retrocraft.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ContentProperties {
    public static <T> T get(Registry<T> registry, ResourceLocation id) {
        return registry.getValue(id);
    }

    public static Item.Properties item(ResourceLocation id, Item.Properties properties) {
        return properties.setId(ResourceKey.create(Registries.ITEM, id));
    }

    public static BlockBehaviour.Properties block(
            ResourceLocation id, BlockBehaviour.Properties properties) {
        return properties.setId(ResourceKey.create(Registries.BLOCK, id));
    }

    private ContentProperties() {}
}
