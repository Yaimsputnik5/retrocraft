package com.sammwy.retrocraft.server.item;

import java.util.*;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.compat.TooltipItem;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.component.ConsoleComponent;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A peripheral or expansion; plugs into a matching component slot of a powered-off console. */
public final class ComponentItem extends TooltipItem {
    private final ConsoleComponent component;

    public ComponentItem(ConsoleComponent component) {
        super(ContentProperties.item(component.id(), new Properties().stacksTo(1)));
        this.component = component;
    }

    public ConsoleComponent component() {
        return component;
    }

    @Override
    public void tooltip(ItemStack stack, List<Component> tooltip) {
        Set<String> consoles = new LinkedHashSet<>();
        for (var console : ConsoleRegistry.consoles())
            if (console.listed()
                    && console.componentSlots().stream().anyMatch(s -> s.accepts(component)))
                consoles.add(console.displayName().getString());
        if (consoles.isEmpty()) return;
        tooltip.add(
                Component.translatable("tooltip.retrocraft.compatible_with")
                        .withStyle(ChatFormatting.GRAY));
        for (String console : consoles)
            tooltip.add(Component.literal("  " + console).withStyle(ChatFormatting.GRAY));
    }
}
