package com.sammwy.retrocraft.server.item;

import java.util.*;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** A registered cartridge item fixes the console family; NBT identifies only the ROM. */
public final class CartridgeItem extends Item {
    private final Identifier id;
    private final DiscoveryStrategy discovery;

    public CartridgeItem(Identifier id, DiscoveryStrategy discovery) {
        super(new Settings().maxCount(1));
        this.id = id;
        this.discovery = discovery;
    }

    public Identifier id() {
        return id;
    }

    public DiscoveryStrategy discovery() {
        return discovery;
    }

    public ItemStack game(String hash, String name) {
        if (!hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("GameID must be SHA-256");
        ItemStack stack = new ItemStack(this);
        NbtCompound data = new NbtCompound();
        data.putString("GameID", hash);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    /** Compatibility heading followed by one line per supported console. */
    @Override
    public void appendTooltip(
            ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        List<String> consoles = new ArrayList<>();
        for (var console : ConsoleRegistry.consoles())
            if (console.cartridge() == this
                    && !console.isInternalArcade()
                    && !console.isLegacyArcade()) consoles.add(console.displayName().getString());
        if (!consoles.isEmpty()) {
            tooltip.add(
                    Text.translatable("tooltip.retrocraft.compatible_with")
                            .formatted(Formatting.GRAY));
            for (String console : consoles)
                tooltip.add(Text.literal("  " + console).formatted(Formatting.GRAY));
        }
    }

    public static String gameId(ItemStack stack) {
        if (!(stack.getItem() instanceof CartridgeItem)) return "";
        String id =
                stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                        .copyNbt()
                        .getString("GameID");
        return id.matches("[0-9a-f]{64}") ? id : "";
    }
}
