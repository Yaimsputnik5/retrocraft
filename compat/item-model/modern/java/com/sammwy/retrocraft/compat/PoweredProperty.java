package com.sammwy.retrocraft.compat;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

record PoweredProperty() implements ConditionalItemModelProperty {
    static final MapCodec<PoweredProperty> CODEC = MapCodec.unit(new PoweredProperty());

    @Override
    public boolean get(
            ItemStack stack,
            ClientLevel level,
            LivingEntity entity,
            int seed,
            ItemDisplayContext context) {
        var predicate = ItemModels.PREDICATES.get(stack.getItem());
        return predicate != null && predicate.value(stack, level, entity, seed) > 0;
    }

    @Override
    public MapCodec<PoweredProperty> type() {
        return CODEC;
    }
}
