package com.sammwy.retrocraft.server.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Set;

/** A registered cartridge item fixes the console family; NBT identifies only the ROM. */
public final class CartridgeItem extends Item {
    private final Identifier id;
    private final Set<String> extensions;
    public CartridgeItem(Identifier id, Set<String> extensions) {
        super(new Settings().maxCount(1)); this.id = id; this.extensions = Set.copyOf(extensions);
    }
    public Identifier id() { return id; }
    public Set<String> extensions() { return extensions; }
    public String folder() { return id.getNamespace().equals("retrocraft") ? id.getPath() : id.getNamespace() + "/" + id.getPath(); }
    public ItemStack game(String hash, String name) {
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("GameID must be SHA-256");
        ItemStack stack = new ItemStack(this);
        NbtCompound data = new NbtCompound();
        data.putString("GameID", hash);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }
    public static String gameId(ItemStack stack) {
        if (!(stack.getItem() instanceof CartridgeItem)) return "";
        String id = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getString("GameID");
        return id.matches("[0-9a-f]{64}") ? id : "";
    }
}
