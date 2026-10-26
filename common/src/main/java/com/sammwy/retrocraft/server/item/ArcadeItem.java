package com.sammwy.retrocraft.server.item;

import com.sammwy.retrocraft.compat.ModelData;
import com.sammwy.retrocraft.compat.TagData;
import com.sammwy.retrocraft.server.block.ArcadeSkin;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Catalog variants share a registered item; their internal board travels with the cabinet. */
public final class ArcadeItem extends BlockItem {
    public ArcadeItem(Block block, Properties settings) {
        super(block, settings.stacksTo(1));
    }

    public ItemStack game(String hash, String name) {
        if (!hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("GameID must be SHA-256");
        ItemStack stack = new ItemStack(this);
        CompoundTag data = new CompoundTag();
        data.putString("GameID", hash);
        data.putString("GameName", name);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        ModelData.skin(stack, ArcadeSkin.of(name).ordinal());
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable("item.retrocraft.arcade_game", name));
        return stack;
    }

    public static String gameId(ItemStack stack) {
        String hash = TagData.string(data(stack), "GameID");
        return hash.matches("[0-9a-f]{64}") ? hash : "";
    }

    public static String gameName(ItemStack stack) {
        return TagData.string(data(stack), "GameName");
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Override
    protected boolean updateCustomBlockEntityTag(
            BlockPos pos, Level world, Player player, ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, world, player, stack, state);
        if (world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && !gameId(stack).isEmpty())
            console.installInternalRom(
                    console.definition().cartridge().game(gameId(stack), gameName(stack)));
        return result;
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        return !gameId(context.getItemInHand()).isEmpty() && super.canPlace(context, state);
    }
}
