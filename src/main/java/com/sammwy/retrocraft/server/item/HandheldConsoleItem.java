package com.sammwy.retrocraft.server.item;

import java.util.List;
import java.util.UUID;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.screen.ConsoleMenus;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

public final class HandheldConsoleItem extends Item {
    private final ConsoleDefinition definition;

    public HandheldConsoleItem(ConsoleDefinition definition) {
        super(new Settings().maxCount(1));
        this.definition = definition;
    }

    public ConsoleDefinition definition() {
        return definition;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient
                && player instanceof ServerPlayerEntity serverPlayer
                && Retrocraft.instances() != null)
            ConsoleMenus.open(serverPlayer, new HandheldHost(serverPlayer, stack));
        return TypedActionResult.success(stack, world.isClient);
    }

    /** Installed components, one line each. */
    @Override
    public void appendTooltip(
            ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound components = components(stack);
        for (var slot : definition.componentSlots()) {
            ItemStack installed = ComponentStorage.read(components, slot);
            if (!installed.isEmpty())
                tooltip.add(
                        Text.literal("+ ")
                                .append(installed.getName())
                                .formatted(Formatting.DARK_AQUA));
        }
    }

    /** Copy of the stack's {@link ComponentStorage} compound. */
    public static NbtCompound components(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt()
                .getCompound(ComponentStorage.KEY);
    }

    public static void components(ItemStack stack, NbtCompound components) {
        NbtCompound data =
                stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (components.isEmpty()) data.remove(ComponentStorage.KEY);
        else data.put(ComponentStorage.KEY, components);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }

    public static UUID instance(ItemStack stack) {
        NbtCompound nbt =
                stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return nbt.containsUuid("InstanceID") ? nbt.getUuid("InstanceID") : null;
    }

    public static void instance(ItemStack stack, UUID id) {
        NbtCompound data =
                stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (id == null) data.remove("InstanceID");
        else data.putUuid("InstanceID", id);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }
}
