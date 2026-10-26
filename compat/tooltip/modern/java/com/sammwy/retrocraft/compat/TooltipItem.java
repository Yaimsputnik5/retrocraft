package com.sammwy.retrocraft.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public abstract class TooltipItem extends Item {
    protected TooltipItem(Properties properties) {
        super(properties);
    }

    public abstract void tooltip(ItemStack stack, List<Component> lines);

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> output,
            TooltipFlag flag) {
        List<Component> lines = new ArrayList<>();
        tooltip(stack, lines);
        lines.forEach(output);
    }
}
