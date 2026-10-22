package com.sammwy.retrocraft.server.block;

import com.mojang.serialization.MapCodec;
import com.sammwy.retrocraft.Retrocraft;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

/**
 * A television a docked console can be bound to. The screen is a dynamic texture drawn by the client renderer,
 * so one block entity covers the whole set even when the cabinet spans several blocks visually.
 */
public final class TvBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;

    /** Cabinet footprint (extra blocks sideways and upwards) and screen glass rectangle in model units, front = north. */
    public enum Size {
        SMALL("tv_small", 0, 0, 1, 15, 13, 3, 13, 2.5f, 13.5f, 3, 11.25f, 2.7f),
        MEDIUM("tv_medium", 1, 1, -8, 24, 24, 2, 14, -5, 21, 3.25f, 22.75f, 1.8f),
        LARGE("tv_large", 1, 1, -16, 32, 32, 0, 16, -7, 23, 4.5f, 27, -.2f);
        public final String id; public final int sideways, upwards;
        public final float x0, x1, height, z0, z1, screenX0, screenX1, screenY0, screenY1, screenZ;
        Size(String id, int sideways, int upwards, float x0, float x1, float height, float z0, float z1, float sx0, float sx1, float sy0, float sy1, float sz) {
            this.id = id; this.sideways = sideways; this.upwards = upwards; this.x0 = x0; this.x1 = x1; this.height = height; this.z0 = z0; this.z1 = z1;
            screenX0 = sx0; screenX1 = sx1; screenY0 = sy0; screenY1 = sy1; screenZ = sz;
        }
    }

    public final Size size;
    private BlockEntityType<TvBlockEntity> entityType;
    public TvBlock(Size size, Settings settings) {
        super(settings); this.size = size;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false));
    }
    public BlockEntityType<TvBlockEntity> entityType() { return entityType; }
    public void entityType(BlockEntityType<TvBlockEntity> value) { if (entityType != null) throw new IllegalStateException("Already registered"); entityType = value; }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return MapCodec.unit(this); }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new TvBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, entityType, TvBlockEntity::tick);
    }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // Mirrored like the model: viewer's left is +x when facing north.
        double x0 = 16 - size.x1, x1 = 16 - size.x0;
        return switch (state.get(FACING)) {
            case SOUTH -> Block.createCuboidShape(16 - x1, 0, 16 - size.z1, 16 - x0, size.height, 16 - size.z0);
            case EAST -> Block.createCuboidShape(16 - size.z1, 0, x0, 16 - size.z0, size.height, x1);
            case WEST -> Block.createCuboidShape(size.z0, 0, 16 - x1, size.z1, size.height, 16 - x0);
            default -> Block.createCuboidShape(x0, 0, size.z0, x1, size.height, size.z1);
        };
    }

    /** Multi-block cabinets need the neighbouring columns they overlap to be free. */
    @Override protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        for (int side = -size.sideways; side <= size.sideways; side++)
            for (int up = 0; up <= size.upwards; up++) {
                BlockPos target = pos.offset(facing.rotateYClockwise(), side).up(up);
                if (!target.equals(pos) && !world.getBlockState(target).isReplaceable()) return false;
            }
        return true;
    }

    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient && world.getBlockEntity(pos) instanceof TvBlockEntity tv && Retrocraft.instances() != null)
            Retrocraft.instances().televisionRemoved(tv);
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
