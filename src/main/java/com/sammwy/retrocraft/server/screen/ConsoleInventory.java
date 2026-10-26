package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.server.console.ConsoleHost;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Every open menu observes the same cartridge and components; per-menu copies allow concurrent
 * viewers to duplicate them. Slot 0 is the cartridge, slots 1.. the component slots.
 */
final class ConsoleInventory implements Inventory {
    private final ConsoleHost host;

    ConsoleInventory(ConsoleHost host) {
        this.host = host;
    }

    @Override
    public int size() {
        return 1 + host.componentCount();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < size(); slot++) if (!getStack(slot).isEmpty()) return false;
        return true;
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot == 0) return host.cartridge();
        return slot > 0 && slot < size() ? host.component(slot - 1) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        if (slot > 0) return removeStack(slot);
        if (host.hasInternalRom() || slot != 0) return ItemStack.EMPTY;
        ItemStack stack = host.cartridge();
        ItemStack removed = stack.split(amount);
        host.cartridge(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot > 0 && slot < size()) {
            ItemStack result = host.component(slot - 1);
            host.component(slot - 1, ItemStack.EMPTY);
            return result;
        }
        if (host.hasInternalRom() || slot != 0) return ItemStack.EMPTY;
        ItemStack result = host.cartridge();
        host.cartridge(ItemStack.EMPTY);
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot > 0 && slot < size()) host.component(slot - 1, stack);
        else if (!host.hasInternalRom() && slot == 0) host.cartridge(stack);
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
