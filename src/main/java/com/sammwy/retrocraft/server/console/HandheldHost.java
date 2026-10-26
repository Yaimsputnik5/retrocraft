package com.sammwy.retrocraft.server.console;

import java.util.*;

import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class HandheldHost implements ConsoleHost {
    private final ServerPlayerEntity holder;
    private final ItemEntity dropped;
    private final ItemStack stack;
    private final HandheldConsoleItem console;

    public HandheldHost(ServerPlayerEntity holder, ItemStack stack) {
        this.holder = holder;
        this.stack = stack;
        dropped = null;
        console = (HandheldConsoleItem) stack.getItem();
    }

    public HandheldHost(ItemEntity dropped) {
        this.dropped = dropped;
        stack = dropped.getStack();
        holder = null;
        console = (HandheldConsoleItem) stack.getItem();
    }

    public ItemStack stack() {
        return stack;
    }

    /** The entity carrying the stack: its holder, or the dropped item entity on the ground. */
    public Entity carrier() {
        return holder != null ? holder : dropped;
    }

    public int inventorySlot() {
        if (holder == null) return -1;
        for (int i = 0; i < holder.getInventory().size(); i++)
            if (holder.getInventory().getStack(i) == stack) return i;
        return -1;
    }

    @Override
    public ConsoleDefinition definition() {
        return console.definition();
    }

    @Override
    public ItemStack cartridge() {
        return stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).stream()
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public void cartridge(ItemStack cartridge) {
        stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(cartridge)));
        dirty();
    }

    @Override
    public ItemStack component(int slot) {
        var slots = definition().componentSlots();
        if (slot < 0 || slot >= slots.size()) return ItemStack.EMPTY;
        return ComponentStorage.read(HandheldConsoleItem.components(stack), slots.get(slot));
    }

    @Override
    public void component(int slot, ItemStack component) {
        var slots = definition().componentSlots();
        if (slot < 0 || slot >= slots.size()) return;
        NbtCompound components = HandheldConsoleItem.components(stack);
        ComponentStorage.write(components, slots.get(slot), component);
        HandheldConsoleItem.components(stack, components);
        dirty();
    }

    @Override
    public UUID instance() {
        return HandheldConsoleItem.instance(stack);
    }

    @Override
    public void instance(UUID id) {
        HandheldConsoleItem.instance(stack, id);
        dirty();
    }

    @Override
    public ServerWorld world() {
        return holder != null ? holder.getServerWorld() : (ServerWorld) dropped.getWorld();
    }

    @Override
    public Vec3d position() {
        return holder != null ? holder.getPos() : dropped.getPos();
    }

    @Override
    public boolean valid() {
        return !stack.isEmpty()
                && (holder != null
                        ? !holder.isDisconnected() && inventorySlot() >= 0
                        : !dropped.isRemoved() && dropped.getStack() == stack);
    }

    @Override
    public boolean canAccess(ServerPlayerEntity player) {
        return holder == player && valid();
    }

    private void dirty() {
        if (holder != null) holder.getInventory().markDirty();
        else dropped.setStack(stack);
    }
}
