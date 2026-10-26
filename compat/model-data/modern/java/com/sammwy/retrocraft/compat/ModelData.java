package com.sammwy.retrocraft.compat;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

public final class ModelData {
    public static void skin(ItemStack stack, int skin) {
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of((float) skin), List.of(), List.of(), List.of()));
    }

    public static int skin(ItemStack stack) {
        Float value =
                stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY)
                        .getFloat(0);
        return value == null ? 0 : Math.round(value);
    }

    private ModelData() {}
}
