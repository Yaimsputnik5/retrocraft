package com.sammwy.retrocraft.server.console.component;

import com.sammwy.retrocraft.compat.TagData;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.item.ComponentItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Installed components as NBT: {@code Components: {<slot id>: "<component id>"}}. Components carry
 * no per-item data, so only the identity is stored; unknown or no-longer-accepted IDs read as an
 * empty slot.
 */
public final class ComponentStorage {
    public static final String KEY = "Components";

    public static ItemStack read(CompoundTag components, ComponentSlot slot) {
        ComponentItem item =
                ConsoleRegistry.component(
                        ResourceLocation.tryParse(TagData.string(components, slot.id())));
        return item != null && slot.accepts(item.component())
                ? new ItemStack(item)
                : ItemStack.EMPTY;
    }

    public static void write(CompoundTag components, ComponentSlot slot, ItemStack stack) {
        if (stack.getItem() instanceof ComponentItem item && slot.accepts(item.component()))
            components.putString(slot.id(), item.component().id().toString());
        else components.remove(slot.id());
    }

    public static ConsoleComponent component(ItemStack stack) {
        return stack.getItem() instanceof ComponentItem item ? item.component() : null;
    }

    private ComponentStorage() {}
}
