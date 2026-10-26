package com.sammwy.retrocraft.compat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

public final class ModelData {
    public static void skin(ItemStack stack, int skin) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(skin));
    }

    public static int skin(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT)
                .value();
    }

    private ModelData() {}
}
