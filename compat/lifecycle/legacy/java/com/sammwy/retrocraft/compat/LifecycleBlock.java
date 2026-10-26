package com.sammwy.retrocraft.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

public abstract class LifecycleBlock extends BaseEntityBlock {
    protected LifecycleBlock(Properties properties) {
        super(properties);
    }

    public abstract void removed(Level level, BlockPos pos);

    @Override
    protected void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) removed(level, pos);
        super.onRemove(state, level, pos, next, moved);
    }
}
