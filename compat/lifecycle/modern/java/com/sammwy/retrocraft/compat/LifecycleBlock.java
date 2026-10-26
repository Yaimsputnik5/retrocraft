package com.sammwy.retrocraft.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;

public abstract class LifecycleBlock extends BaseEntityBlock {
    protected LifecycleBlock(Properties properties) {
        super(properties);
    }

    public abstract void removed(Level level, BlockPos pos);
}
