package com.sammwy.retrocraft.server.block;

import com.mojang.serialization.MapCodec;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.screen.ConsoleMenus;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.BlockView;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;

public final class DockedConsoleBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;
    private final ConsoleDefinition definition;
    private BlockEntityType<DockedConsoleBlockEntity> entityType;
    public DockedConsoleBlock(ConsoleDefinition definition, Settings settings) { super(settings); this.definition = definition; setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        String id = definition.id().getPath();
        if (id.equals("mame_console")) return state.get(FACING).getAxis() == Direction.Axis.X ? Block.createCuboidShape(0, 0, -1, 16, 40, 17) : Block.createCuboidShape(-1, 0, 0, 17, 40, 16);
        if (id.equals("wii_console")) return state.get(FACING).getAxis() == Direction.Axis.X ? Block.createCuboidShape(2, 0, 5.25, 14.2, 14.6, 10.75) : Block.createCuboidShape(5.25, 0, 2, 10.75, 14.6, 14.2);
        if (id.equals("gamecube_console")) return state.get(FACING).getAxis() == Direction.Axis.X ? Block.createCuboidShape(2, 0, 2.2, 14.8, 12.1, 13.8) : Block.createCuboidShape(2.2, 0, 2, 13.8, 12.1, 14.8);
        double height = id.equals("nes_console") ? 6.5 : 5.8;
        return state.get(FACING).getAxis() == Direction.Axis.X ? Block.createCuboidShape(2, 0, 1, 14, height, 15) : Block.createCuboidShape(1, 0, 2, 15, height, 14);
    }
    /** The arcade cabinet is two and a half blocks tall, so it needs free space above it. */
    @Override protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return !definition.id().getPath().equals("mame_console") || world.getBlockState(pos.up()).isReplaceable() && world.getBlockState(pos.up(2)).isReplaceable();
    }
    public ConsoleDefinition definition() { return definition; }
    public BlockEntityType<DockedConsoleBlockEntity> entityType() { return entityType; }
    public void entityType(BlockEntityType<DockedConsoleBlockEntity> value) { if (entityType != null) throw new IllegalStateException("Already registered"); entityType = value; }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return MapCodec.unit(this); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new DockedConsoleBlockEntity(pos, state); }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, entityType, DockedConsoleBlockEntity::tick);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console)
            ConsoleMenus.open(serverPlayer, console);
        return ActionResult.SUCCESS;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isOf(definition.cartridge()) && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && console.instance() == null && console.cartridge().isEmpty()) {
            if (!world.isClient) {
                console.cartridge(stack.copyWithCount(1));
                if (!player.isCreative()) stack.decrement(1);
            }
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console) {
            if (!world.isClient && Retrocraft.instances() != null) Retrocraft.instances().powerOff(console);
            ItemScatterer.spawn(world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, console.cartridge());
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
