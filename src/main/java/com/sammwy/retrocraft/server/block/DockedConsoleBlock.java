package com.sammwy.retrocraft.server.block;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.item.CartridgeItem;
import com.sammwy.retrocraft.server.screen.ConsoleMenus;

import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
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
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private BlockEntityType<DockedConsoleBlockEntity> entityType;

    public DockedConsoleBlock(ConsoleDefinition definition, Settings settings) {
        super(settings);
        this.definition = definition;
        var bounds =
                definition.bounds() == null
                        ? ConsoleDefinition.Bounds.DEFAULT
                        : definition.bounds();
        for (Direction facing : Direction.Type.HORIZONTAL) shapes.put(facing, bounds.shape(facing));
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
        return shapes.get(state.get(FACING));
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
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        if (definition.internalRom()
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && !CartridgeItem.gameId(console.cartridge()).isEmpty())
            return console.arcadeStack();
        return super.getPickStack(world, pos, state);
    }

    @Override
    protected List<ItemStack> getDroppedStacks(
            BlockState state, LootContextParameterSet.Builder builder) {
        var drops = super.getDroppedStacks(state, builder);
        if (definition.internalRom()
                && builder.getOptional(LootContextParameters.BLOCK_ENTITY)
                        instanceof DockedConsoleBlockEntity console
                && !CartridgeItem.gameId(console.cartridge()).isEmpty()) {
            for (int i = 0; i < drops.size(); i++)
                if (drops.get(i).isOf(asItem())) drops.set(i, console.arcadeStack());
        }
        return drops;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return definition.internalRom() ? BlockRenderType.INVISIBLE : BlockRenderType.MODEL;
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
        if (!definition.internalRom()
                && stack.isOf(definition.cartridge())
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
            if (!console.hasInternalRom())
                ItemScatterer.spawn(
                        world,
                        pos.getX() + .5,
                        pos.getY() + .5,
                        pos.getZ() + .5,
                        console.cartridge());
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
