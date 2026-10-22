package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.*;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ConsoleScreenHandler extends ScreenHandler {
    public static final int POWER = 0, EJECT = 1;
    private final ConsoleDefinition definition;
    private final ConsoleHost host;
    private final Inventory cartridge;
    private final PropertyDelegate properties;
    private final int lockedSlot;

    public ConsoleScreenHandler(int syncId, PlayerInventory inventory, ConsoleOpenData data) {
        this(syncId, inventory, ConsoleRegistry.get(data.console()), null, new SimpleInventory(1), new ArrayPropertyDelegate(1), data.lockedSlot());
    }
    public ConsoleScreenHandler(int syncId, PlayerInventory inventory, ConsoleHost host, int lockedSlot) {
        this(syncId, inventory, host.definition(), host, new HostInventory(host), new PropertyDelegate() {
            public int size() { return 1; }
            public int get(int index) { return host.instance() == null ? 0 : 1; }
            public void set(int index, int value) { }
        }, lockedSlot);
    }
    private ConsoleScreenHandler(int syncId, PlayerInventory inventory, ConsoleDefinition definition, ConsoleHost host, Inventory cartridge, PropertyDelegate properties, int lockedSlot) {
        super(Retrocraft.CONSOLE_SCREEN, syncId);
        this.definition = definition; this.host = host; this.cartridge = cartridge; this.properties = properties; this.lockedSlot = lockedSlot;
        if (definition == null) throw new IllegalArgumentException("Unknown console");
        addProperties(properties);
        addSlot(new Slot(cartridge, 0, 80, 35) {
            @Override public boolean canInsert(ItemStack stack) { return !powered() && stack.isOf(definition.cartridge()); }
            @Override public boolean canTakeItems(PlayerEntity player) { return !powered(); }
            @Override public int getMaxItemCount() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addPlayerSlot(inventory, col + row * 9 + 9, 8 + col * 18, 94 + row * 18);
        for (int col = 0; col < 9; col++) addPlayerSlot(inventory, col, 8 + col * 18, 152);
    }
    private void addPlayerSlot(PlayerInventory inventory, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean canTakeItems(PlayerEntity player) { return index != lockedSlot; }
            @Override public boolean canInsert(ItemStack stack) { return index != lockedSlot; }
        });
    }
    public ConsoleDefinition definition() { return definition; }
    public ConsoleHost host() { return host; }
    public boolean powered() { return properties.get(0) != 0; }
    @Override public boolean canUse(PlayerEntity player) { return host == null || player instanceof ServerPlayerEntity serverPlayer && host.canAccess(serverPlayer); }

    @Override public boolean onButtonClick(PlayerEntity user, int id) {
        if (!(user instanceof ServerPlayerEntity player) || host == null || !canUse(player) || Retrocraft.instances() == null) return false;
        var manager = Retrocraft.instances();
        switch (id) {
            case POWER -> {
                if (powered()) manager.powerOff(host);
                else {
                    manager.powerOn(host, player);
                    if (host.instance() != null) { ConsoleMenus.open(player, host); return true; }
                }
            }
            case EJECT -> {
                if (powered()) manager.powerOff(host);
                ItemStack ejected = cartridge.removeStack(0);
                cartridge.markDirty();
                if (!player.getInventory().insertStack(ejected)) player.dropItem(ejected, false);
            }
            default -> { return false; }
        }
        sendContentUpdates();
        return true;
    }

    @Override public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player instanceof ServerPlayerEntity serverPlayer && Retrocraft.instances() != null) Retrocraft.instances().releaseInput(serverPlayer.getUuid());
    }

    @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (action == SlotActionType.SWAP && button == lockedSlot) return;
        if (slot > 0 && slot < slots.size() && slots.get(slot).inventory == player.getInventory() && slots.get(slot).getIndex() == lockedSlot) return;
        super.onSlotClick(slot, button, action, player);
    }

    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack() || !slot.canTakeItems(player)) return ItemStack.EMPTY;
        ItemStack original = slot.getStack(), copy = original.copy();
        if (index == 0) { if (!insertItem(original, 1, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!slots.getFirst().canInsert(original) || !insertItem(original, 0, 1, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        return copy;
    }

    private static final class HostInventory extends SimpleInventory {
        private final ConsoleHost host;
        HostInventory(ConsoleHost host) { super(1); this.host = host; setStack(0, host.cartridge()); }
        @Override public void markDirty() { if (host != null) host.cartridge(getStack(0)); super.markDirty(); }
    }
}
