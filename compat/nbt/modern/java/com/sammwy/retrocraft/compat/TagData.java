package com.sammwy.retrocraft.compat;

import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Stable accessors preserve the old empty/default and UUID wire representation. */
public final class TagData {
    public static String string(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }

    public static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    public static Long longValue(CompoundTag tag, String key) {
        return tag.getLong(key).orElse(null);
    }

    public static UUID uuid(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).orElse(null);
    }

    public static void uuid(CompoundTag tag, String key, UUID value) {
        tag.store(key, UUIDUtil.CODEC, value);
    }

    public static Tag stack(ItemStack stack, HolderLookup.Provider registries) {
        return ItemStack.OPTIONAL_CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow();
    }

    public static ItemStack stack(CompoundTag tag, HolderLookup.Provider registries) {
        return ItemStack.OPTIONAL_CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result()
                .orElse(ItemStack.EMPTY);
    }

    private TagData() {}
}
