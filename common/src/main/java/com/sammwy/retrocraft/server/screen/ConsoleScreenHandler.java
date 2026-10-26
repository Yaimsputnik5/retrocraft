package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.*;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ConsoleScreenHandler extends AbstractContainerMenu {
    public static final int POWER = 0, EJECT = 1;
    private final ConsoleDefinition definition;
    private final ConsoleHost host;
    private final Container cartridge;
    private final ContainerData properties;
    private final int lockedSlot;

    public ConsoleScreenHandler(int syncId, Inventory inventory, ConsoleOpenData data) {
        this(syncId, inventory, ConsoleRegistry.get(data.console()), data.lockedSlot());
    }

    private ConsoleScreenHandler(
            int syncId, Inventory inventory, ConsoleDefinition definition, int lockedSlot) {
        this(
                syncId,
                inventory,
                definition,
                null,
                new SimpleContainer(
                        1 + (definition == null ? 0 : definition.componentSlots().size())),
                new SimpleContainerData(1),
                lockedSlot);
    }

    public ConsoleScreenHandler(int syncId, Inventory inventory, ConsoleHost host, int lockedSlot) {
        this(
                syncId,
                inventory,
                host.definition(),
                host,
                new ConsoleInventory(host),
                new ContainerData() {
                    public int getCount() {
                        return 1;
                    }

                    public int get(int index) {
                        return host.instance() == null ? 0 : 1;
                    }

                    public void set(int index, int value) {}
                },
                lockedSlot);
    }

    private ConsoleScreenHandler(
            int syncId,
            Inventory inventory,
            ConsoleDefinition definition,
            ConsoleHost host,
            Container cartridge,
            ContainerData properties,
            int lockedSlot) {
        super(Retrocraft.CONSOLE_SCREEN.get(), syncId);
        this.definition = definition;
        this.host = host;
        this.cartridge = cartridge;
        this.properties = properties;
        this.lockedSlot = lockedSlot;
        if (definition == null) throw new IllegalArgumentException("Unknown console");
        addDataSlots(properties);
        var componentSlots = definition.componentSlots();
        addSlot(
                new Slot(cartridge, 0, slotX(componentSlots.size(), 0), 35) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !definition.internalRom()
                                && !powered()
                                && stack.is(definition.cartridge());
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return !definition.internalRom() && !powered();
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
        for (int i = 0; i < componentSlots.size(); i++) {
            ComponentSlot port = componentSlots.get(i);
            addSlot(
                    new Slot(cartridge, 1 + i, slotX(componentSlots.size(), 1 + i), 35) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return !powered() && port.accepts(ComponentStorage.component(stack));
                        }

                        @Override
                        public boolean mayPickup(Player player) {
                            return !powered();
                        }

                        @Override
                        public int getMaxStackSize() {
                            return 1;
                        }
                    });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addPlayerSlot(inventory, col + row * 9 + 9, 8 + col * 18, 94 + row * 18);
        for (int col = 0; col < 9; col++) addPlayerSlot(inventory, col, 8 + col * 18, 152);
    }

    /** Cartridge bay then component ports, centred in the console panel. */
    private static int slotX(int components, int index) {
        if (components == 0) return 80;
        int start = 88 - (16 + 20 + components * 16 + (components - 1) * 6) / 2;
        return index == 0 ? start : start + 36 + (index - 1) * 22;
    }

    /** Machine slots (cartridge + components) precede the player inventory. */
    public int machineSlots() {
        return 1 + definition.componentSlots().size();
    }

    private void addPlayerSlot(Inventory inventory, int index, int x, int y) {
        addSlot(
                new Slot(inventory, index, x, y) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return index != lockedSlot;
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return index != lockedSlot;
                    }
                });
    }

    public ConsoleDefinition definition() {
        return definition;
    }

    public ConsoleHost host() {
        return host;
    }

    public boolean powered() {
        return properties.get(0) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return host == null
                || player instanceof ServerPlayer serverPlayer && host.canAccess(serverPlayer);
    }

    @Override
    public boolean clickMenuButton(Player user, int id) {
        if (!(user instanceof ServerPlayer player)
                || host == null
                || !stillValid(player)
                || Retrocraft.instances() == null) return false;
        var manager = Retrocraft.instances();
        switch (id) {
            case POWER -> {
                if (powered()) manager.powerOff(host);
                else {
                    manager.powerOn(host, player);
                    if (host.instance() != null) {
                        ConsoleMenus.open(player, host);
                        return true;
                    }
                }
            }
            case EJECT -> {
                if (host.hasInternalRom()) return false;
                if (powered()) manager.powerOff(host);
                ItemStack ejected = cartridge.removeItemNoUpdate(0);
                cartridge.setChanged();
                if (!player.getInventory().add(ejected)) player.drop(ejected, false);
            }
            default -> {
                return false;
            }
        }
        broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer && Retrocraft.instances() != null)
            Retrocraft.instances().releaseInput(serverPlayer.getUUID());
    }

    @Override
    public void clicked(int slot, int button, ClickType action, Player player) {
        if (slot == 0 && definition.internalRom()) return;
        if (action == ClickType.SWAP && button == lockedSlot) return;
        if (slot > 0
                && slot < slots.size()
                && slots.get(slot).container == player.getInventory()
                && slots.get(slot).getContainerSlot() == lockedSlot) return;
        super.clicked(slot, button, action, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original = slot.getItem(), copy = original.copy();
        int machine = machineSlots();
        if (index < machine) {
            if (!moveItemStackTo(original, machine, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < machine && !moved; i++)
                moved =
                        !slots.get(i).hasItem()
                                && slots.get(i).mayPlace(original)
                                && moveItemStackTo(original, i, i + 1, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
