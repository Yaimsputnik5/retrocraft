package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

@Mixin(ItemProperties.class)
public interface ItemModelPropertiesAccessor {
    @Invoker("register")
    static void retrocraft$register(
            Item item, ResourceLocation id, ClampedItemPropertyFunction predicate) {
        throw new AssertionError("Mixin was not applied");
    }
}
