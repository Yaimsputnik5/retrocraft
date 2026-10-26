package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.server.console.ConsoleHost;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Every open menu observes the same cartridge and components; per-menu copies allow concurrent
 * viewers to duplicate them. Slot 0 is the cartridge, slots 1.. the component slots.
 */
public final class ConsoleInventory implements Container {
    private final ConsoleHost host;

    public ConsoleInventory(ConsoleHost host) {
        this.host = host;
    }

    @Override
    public int getContainerSize() {
        return 1 + host.componentCount();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++)
            if (!getItem(slot).isEmpty()) return false;
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == 0) return host.cartridge();
        return slot > 0 && slot < getContainerSize() ? host.component(slot - 1) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        if (slot > 0) return removeItemNoUpdate(slot);
        if (host.hasInternalRom() || slot != 0) return ItemStack.EMPTY;
        ItemStack stack = host.cartridge();
        ItemStack removed = stack.split(amount);
        host.cartridge(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot > 0 && slot < getContainerSize()) {
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
    public void setItem(int slot, ItemStack stack) {
        if (slot > 0 && slot < getContainerSize()) host.component(slot - 1, stack);
        else if (!host.hasInternalRom() && slot == 0) host.cartridge(stack);
    }

    @Override
    public void setChanged() {
        host.cartridge(host.cartridge());
    }

    @Override
    public boolean stillValid(Player player) {
        return player instanceof ServerPlayer serverPlayer && host.canAccess(serverPlayer);
    }

    @Override
    public void clearContent() {
        host.cartridge(ItemStack.EMPTY);
    }
}
