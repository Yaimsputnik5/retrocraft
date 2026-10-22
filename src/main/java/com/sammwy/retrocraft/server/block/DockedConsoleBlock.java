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

public final class DockedConsoleBlock extends BlockWithEntity {
    private final ConsoleDefinition definition;
    private BlockEntityType<DockedConsoleBlockEntity> entityType;
    public DockedConsoleBlock(ConsoleDefinition definition, Settings settings) { super(settings); this.definition = definition; }
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
