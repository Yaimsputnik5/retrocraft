package com.sammwy.retrocraft.server.console;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** The same runtime can be hosted by an inventory item or a block entity. */
public interface ConsoleHost {
    ConsoleDefinition definition();

    ItemStack cartridge();

    void cartridge(ItemStack stack);

    UUID instance();

    void instance(UUID id);

    ServerWorld world();

    Vec3d position();

    /**
     * Where listeners hear the console: the bound television for docked sets, the console itself
     * otherwise.
     */
    default Vec3d soundPosition() {
        return position();
    }

    boolean valid();

    boolean canAccess(ServerPlayerEntity player);
}
