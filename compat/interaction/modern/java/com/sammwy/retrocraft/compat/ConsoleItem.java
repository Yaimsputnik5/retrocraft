package com.sammwy.retrocraft.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class ConsoleItem extends TooltipItem {
    protected ConsoleItem(Properties properties) {
        super(properties);
    }

    protected abstract void activate(Level level, Player player, ItemStack stack);

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        activate(level, player, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }
}
