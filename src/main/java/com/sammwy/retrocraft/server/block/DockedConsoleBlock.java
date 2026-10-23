package com.sammwy.retrocraft.server.block;

import com.mojang.serialization.MapCodec;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.screen.ConsoleMenus;

import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public final class DockedConsoleBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;
    private final ConsoleDefinition definition;
    private BlockEntityType<DockedConsoleBlockEntity> entityType;

    public DockedConsoleBlock(ConsoleDefinition definition, Settings settings) {
        super(settings);
        this.definition = definition;
        setDefaultState(
                getStateManager()
                        .getDefaultState()
                        .with(FACING, Direction.NORTH)
                        .with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(
            BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        double[] bounds =
                switch (definition.id().getPath()) {
                    case "nes_console" -> new double[] {1, 1.79, 15, 14, 5.85};
                    case "snes_console" -> new double[] {1, 2.43, 15, 13.5, 5.74};
                    case "genesis_console", "megadrive_console" ->
                            new double[] {1, 2.43, 15, 13.5, 4.39};
                    case "n64_console" -> new double[] {1, 2.43, 15, 13.5, 5.24};
                    case "ps1_console" -> new double[] {1, 2.42, 15, 13.5, 3.94};
                    case "ps2_console" -> new double[] {1, 2.01, 15, 13.5, 4.69};
                    case "dreamcast_console" -> new double[] {1.35, 2.42, 14.4, 13.5, 4.22};
                    case "gamecube_console" -> new double[] {2.35, 2.36, 13.65, 15.2, 9.35};
                    case "wii_console" -> new double[] {5.15, 2.1, 10.85, 14.3, 14.39};
                    case "mame_console", "mame_tetris_console", "mame_space_invaders_console" ->
                            new double[] {-4.05, -3.15, 20.05, 19.05, 48};
                    default -> new double[] {1, 2, 15, 14, 5.8};
                };
        double x0 = bounds[0], z0 = bounds[1], x1 = bounds[2], z1 = bounds[3], height = bounds[4];
        return switch (state.get(FACING)) {
            case EAST -> Block.createCuboidShape(16 - z1, 0, x0, 16 - z0, height, x1);
            case SOUTH -> Block.createCuboidShape(16 - x1, 0, 16 - z1, 16 - x0, height, 16 - z0);
            case WEST -> Block.createCuboidShape(z0, 0, 16 - x1, z1, height, 16 - x0);
            default -> Block.createCuboidShape(x0, 0, z0, x1, height, z1);
        };
    }

    /** The arcade cabinets are three blocks tall, so both blocks above must be free. */
    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return !definition.isArcade()
                || world.getBlockState(pos.up()).isReplaceable()
                        && world.getBlockState(pos.up(2)).isReplaceable();
    }

    public ConsoleDefinition definition() {
        return definition;
    }

    public BlockEntityType<DockedConsoleBlockEntity> entityType() {
        return entityType;
    }

    public void entityType(BlockEntityType<DockedConsoleBlockEntity> value) {
        if (entityType != null) throw new IllegalStateException("Already registered");
        entityType = value;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DockedConsoleBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient
                ? null
                : validateTicker(type, entityType, DockedConsoleBlockEntity::tick);
    }

    @Override
    protected ActionResult onUse(
            BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient
                && player instanceof ServerPlayerEntity serverPlayer
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console)
            ConsoleMenus.open(serverPlayer, console);
        return ActionResult.SUCCESS;
    }

    @Override
    protected ItemActionResult onUseWithItem(
            ItemStack stack,
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hit) {
        if (stack.isOf(definition.cartridge())
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && console.instance() == null
                && console.cartridge().isEmpty()) {
            if (!world.isClient) {
                console.cartridge(stack.copyWithCount(1));
                if (!player.isCreative()) stack.decrement(1);
            }
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onStateReplaced(
            BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console) {
            if (!world.isClient && Retrocraft.instances() != null)
                Retrocraft.instances().powerOff(console);
            ItemScatterer.spawn(
                    world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, console.cartridge());
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
