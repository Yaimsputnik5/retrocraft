package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.server.console.ConsoleHost;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Every open menu observes the same cartridge; per-menu copies allow concurrent viewers to
 * duplicate it.
 */
final class ConsoleInventory implements Inventory {
    private final ConsoleHost host;

    ConsoleInventory(ConsoleHost host) {
        this.host = host;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return host.cartridge().isEmpty();
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? host.cartridge() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
        ItemStack stack = host.cartridge();
        ItemStack removed = stack.split(amount);
        host.cartridge(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack result = host.cartridge();
        host.cartridge(ItemStack.EMPTY);
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) host.cartridge(stack);
    }

    @Override
    public void markDirty() {
        host.cartridge(host.cartridge());
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return player instanceof ServerPlayerEntity serverPlayer && host.canAccess(serverPlayer);
    }

    @Override
    public void clear() {
        host.cartridge(ItemStack.EMPTY);
    }
}
