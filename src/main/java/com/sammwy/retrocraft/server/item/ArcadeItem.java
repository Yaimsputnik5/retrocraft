package com.sammwy.retrocraft.server.item;

import com.sammwy.retrocraft.server.block.ArcadeSkin;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Catalog variants share a registered item; their internal board travels with the cabinet. */
public final class ArcadeItem extends BlockItem {
    public ArcadeItem(Block block, Settings settings) {
        super(block, settings.maxCount(1));
    }

    public ItemStack game(String hash, String name) {
        if (!hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("GameID must be SHA-256");
        ItemStack stack = new ItemStack(this);
        NbtCompound data = new NbtCompound();
        data.putString("GameID", hash);
        data.putString("GameName", name);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        stack.set(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                new CustomModelDataComponent(ArcadeSkin.of(name).ordinal()));
        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.retrocraft.arcade_game", name));
        return stack;
    }

    public static String gameId(ItemStack stack) {
        String hash = data(stack).getString("GameID");
        return hash.matches("[0-9a-f]{64}") ? hash : "";
    }

    public static String gameName(ItemStack stack) {
        return data(stack).getString("GameName");
    }

    private static NbtCompound data(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    @Override
    protected boolean postPlacement(
            BlockPos pos, World world, PlayerEntity player, ItemStack stack, BlockState state) {
        boolean result = super.postPlacement(pos, world, player, stack, state);
        if (world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && !gameId(stack).isEmpty())
            console.installInternalRom(
                    console.definition().cartridge().game(gameId(stack), gameName(stack)));
        return result;
    }

    @Override
    protected boolean canPlace(ItemPlacementContext context, BlockState state) {
        return !gameId(context.getStack()).isEmpty() && super.canPlace(context, state);
    }
}
