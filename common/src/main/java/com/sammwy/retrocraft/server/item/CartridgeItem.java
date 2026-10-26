package com.sammwy.retrocraft.server.item;

import java.util.*;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.compat.TagData;
import com.sammwy.retrocraft.compat.TooltipItem;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.GameSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Game media of one system (cartridge, card, disc, UMD or board); NBT identifies only the ROM. */
public final class CartridgeItem extends TooltipItem {
    private final GameSystem system;

    public CartridgeItem(GameSystem system) {
        super(ContentProperties.item(system.mediaId(), new Properties().stacksTo(1)));
        this.system = system;
    }

    public GameSystem system() {
        return system;
    }

    public ItemStack game(String hash, String name) {
        if (!hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("GameID must be SHA-256");
        ItemStack stack = new ItemStack(this);
        CompoundTag data = new CompoundTag();
        data.putString("GameID", hash);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    /** Compatibility heading followed by one line per supported console. */
    @Override
    public void tooltip(ItemStack stack, List<Component> tooltip) {
        List<String> consoles = new ArrayList<>();
        for (var console : ConsoleRegistry.consoles())
            if (console.cartridge() == this && console.listed())
                consoles.add(console.displayName().getString());
        if (!consoles.isEmpty()) {
            tooltip.add(
                    Component.translatableWithFallback(
                                    "tooltip.retrocraft.compatible_with", "Compatible with:")
                            .withStyle(ChatFormatting.GRAY));
            for (String console : consoles)
                tooltip.add(Component.literal("  " + console).withStyle(ChatFormatting.GRAY));
        }
    }

    public static String gameId(ItemStack stack) {
        if (!(stack.getItem() instanceof CartridgeItem)) return "";
        String id =
                TagData.string(
                        stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(),
                        "GameID");
        return id.matches("[0-9a-f]{64}") ? id : "";
    }
}
