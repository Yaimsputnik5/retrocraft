package com.sammwy.retrocraft.server.item;

import java.util.*;

import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.component.ConsoleComponent;

import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** A peripheral or expansion; plugs into a matching component slot of a powered-off console. */
public final class ComponentItem extends Item {
    private final ConsoleComponent component;

    public ComponentItem(ConsoleComponent component) {
        super(new Settings().maxCount(1));
        this.component = component;
    }

    public ConsoleComponent component() {
        return component;
    }

    @Override
    public void appendTooltip(
            ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Set<String> consoles = new LinkedHashSet<>();
        for (var console : ConsoleRegistry.consoles())
            if (console.listed()
                    && console.componentSlots().stream().anyMatch(s -> s.accepts(component)))
                consoles.add(console.displayName().getString());
        if (consoles.isEmpty()) return;
        tooltip.add(
                Text.translatable("tooltip.retrocraft.compatible_with").formatted(Formatting.GRAY));
        for (String console : consoles)
            tooltip.add(Text.literal("  " + console).formatted(Formatting.GRAY));
    }
}
