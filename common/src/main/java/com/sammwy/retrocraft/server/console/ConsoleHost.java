package com.sammwy.retrocraft.server.console;

import java.util.*;

import com.sammwy.retrocraft.server.console.component.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The same runtime can be hosted by an inventory item or a block entity. */
public interface ConsoleHost {
    ConsoleDefinition definition();

    default boolean hasInternalRom() {
        return definition() != null && definition().internalRom();
    }

    ItemStack cartridge();

    void cartridge(ItemStack stack);

    default int componentCount() {
        return definition() == null ? 0 : definition().componentSlots().size();
    }

    /**
     * The item plugged into a component slot, in {@link ConsoleDefinition#componentSlots} order.
     */
    default ItemStack component(int slot) {
        return ItemStack.EMPTY;
    }

    default void component(int slot, ItemStack stack) {}

    /** Installed component per slot, {@code null} when empty. */
    default List<ConsoleComponent> installedComponents() {
        List<ConsoleComponent> installed = new ArrayList<>();
        for (int i = 0; i < componentCount(); i++)
            installed.add(ComponentStorage.component(component(i)));
        return installed;
    }

    UUID instance();

    void instance(UUID id);

    ServerLevel world();

    Vec3 position();

    /**
     * Where listeners hear the console: the bound television for docked sets, the console itself
     * otherwise.
     */
    default Vec3 soundPosition() {
        return position();
    }

    boolean valid();

    boolean canAccess(ServerPlayer player);
}
