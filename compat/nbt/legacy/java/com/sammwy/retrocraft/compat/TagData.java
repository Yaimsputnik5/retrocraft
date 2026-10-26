package com.sammwy.retrocraft.compat;

import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Stable accessors preserve the old empty/default and UUID wire representation. */
public final class TagData {
    public static String string(CompoundTag tag, String key) {
        return tag.getString(key);
    }

    public static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompound(key);
    }

    public static Long longValue(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : null;
    }

    public static UUID uuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    public static void uuid(CompoundTag tag, String key, UUID value) {
        tag.putUUID(key, value);
    }

    public static Tag stack(ItemStack stack, HolderLookup.Provider registries) {
        return stack.saveOptional(registries);
    }

    public static ItemStack stack(CompoundTag tag, HolderLookup.Provider registries) {
        return ItemStack.parseOptional(registries, tag);
    }

    private TagData() {}
}
