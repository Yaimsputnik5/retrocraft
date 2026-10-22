package com.sammwy.retrocraft.server.item;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.screen.ConsoleMenus;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.UUID;

public final class HandheldConsoleItem extends Item {
    private final ConsoleDefinition definition;
    public HandheldConsoleItem(ConsoleDefinition definition) { super(new Settings().maxCount(1)); this.definition = definition; }
    public ConsoleDefinition definition() { return definition; }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && Retrocraft.instances() != null)
            ConsoleMenus.open(serverPlayer, new HandheldHost(serverPlayer, stack));
        return TypedActionResult.success(stack, world.isClient);
    }
    public static UUID instance(ItemStack stack) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return nbt.containsUuid("InstanceID") ? nbt.getUuid("InstanceID") : null;
    }
    public static void instance(ItemStack stack, UUID id) {
        NbtCompound data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (id == null) data.remove("InstanceID"); else data.putUuid("InstanceID", id);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }
}
