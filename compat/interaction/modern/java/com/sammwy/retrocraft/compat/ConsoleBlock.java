package com.sammwy.retrocraft.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public abstract class ConsoleBlock extends CloneableConsoleBlock {
    protected ConsoleBlock(Properties properties) {
        super(properties);
    }

    protected abstract boolean insert(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit);

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return insert(stack, state, level, pos, player, hand, hit)
                ? InteractionResult.SUCCESS
                : InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
