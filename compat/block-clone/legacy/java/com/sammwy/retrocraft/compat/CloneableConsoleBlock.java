package com.sammwy.retrocraft.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public abstract class CloneableConsoleBlock extends LifecycleBlock {
    public static ItemStack pick(Block block, LevelReader level, BlockPos pos, BlockState state) {
        return block.getCloneItemStack(level, pos, state);
    }

    protected CloneableConsoleBlock(Properties properties) {
        super(properties);
    }

    protected abstract ItemStack cloneConsole(LevelReader level, BlockPos pos, BlockState state);

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var stack = cloneConsole(level, pos, state);
        return stack == null ? super.getCloneItemStack(level, pos, state) : stack;
    }
}
