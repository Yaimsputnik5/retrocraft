package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

@Mixin(ConditionalItemModelProperties.class)
public interface ItemModelPropertiesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<
                    ResourceLocation, MapCodec<? extends ConditionalItemModelProperty>>
            retrocraft$properties() {
        throw new AssertionError("Mixin was not applied");
    }
}
