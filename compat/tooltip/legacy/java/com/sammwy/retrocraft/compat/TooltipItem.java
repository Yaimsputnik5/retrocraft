package com.sammwy.retrocraft.compat;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public abstract class TooltipItem extends Item {
    protected TooltipItem(Properties properties) {
        super(properties);
    }

    public abstract void tooltip(ItemStack stack, List<Component> lines);

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        tooltip(stack, lines);
    }
}
