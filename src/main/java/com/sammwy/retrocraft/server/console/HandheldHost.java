package com.sammwy.retrocraft.server.console;

import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public final class HandheldHost implements ConsoleHost {
    private final ServerPlayerEntity holder;
    private final ItemEntity dropped;
    private final ItemStack stack;
    public HandheldHost(ServerPlayerEntity holder, ItemStack stack) { this.holder = holder; this.stack = stack; dropped = null; }
    public HandheldHost(ItemEntity dropped) { this.dropped = dropped; stack = dropped.getStack(); holder = null; }
    public ItemStack stack() { return stack; }
    public int inventorySlot() {
        if (holder == null) return -1;
        for (int i = 0; i < holder.getInventory().size(); i++) if (holder.getInventory().getStack(i) == stack) return i;
        return -1;
    }
    @Override public ConsoleDefinition definition() { return ((HandheldConsoleItem) stack.getItem()).definition(); }
    @Override public ItemStack cartridge() { return stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).stream().findFirst().orElse(ItemStack.EMPTY); }
    @Override public void cartridge(ItemStack cartridge) {
        stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(cartridge)));
        dirty();
    }
    @Override public UUID instance() { return HandheldConsoleItem.instance(stack); }
    @Override public void instance(UUID id) { HandheldConsoleItem.instance(stack, id); dirty(); }
    @Override public ServerWorld world() { return holder != null ? holder.getServerWorld() : (ServerWorld) dropped.getWorld(); }
    @Override public Vec3d position() { return holder != null ? holder.getPos() : dropped.getPos(); }
    @Override public boolean valid() { return !stack.isEmpty() && (holder != null ? !holder.isDisconnected() && inventorySlot() >= 0 : !dropped.isRemoved() && dropped.getStack() == stack); }
    @Override public boolean canAccess(ServerPlayerEntity player) { return holder == player && valid(); }
    private void dirty() { if (holder != null) holder.getInventory().markDirty(); else dropped.setStack(stack); }
}
