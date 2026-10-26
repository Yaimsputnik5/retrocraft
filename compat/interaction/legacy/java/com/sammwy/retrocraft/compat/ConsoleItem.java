package com.sammwy.retrocraft.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class ConsoleItem extends TooltipItem {
    protected ConsoleItem(Properties properties) {
        super(properties);
    }

    protected abstract void activate(Level level, Player player, ItemStack stack);

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        activate(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
