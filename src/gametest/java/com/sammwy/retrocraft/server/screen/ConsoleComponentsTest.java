package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.Components;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ConsoleComponentsTest implements FabricGameTest {
    private static ItemStack stack(Identifier id) {
        return new ItemStack(ConsoleRegistry.component(id));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dockedComponentsAreSlotCheckedAndSurviveSaving(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
        world.setBlockState(
                pos, Registries.BLOCK.get(Identifier.of("retrocraft", "n64")).getDefaultState());
        var console = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
        var inventory = new ConsoleInventory(console);
        context.assertTrue(inventory.size() == 3, "N64 exposes cartridge + two component slots");
        inventory.setStack(1, stack(Components.N64_RUMBLE_PAK.id()));
        context.assertTrue(
                inventory.getStack(1).isEmpty(), "The memory bay must reject a Rumble Pak");
        inventory.setStack(1, stack(Components.N64_EXPANSION_PAK.id()));
        inventory.setStack(2, stack(Components.N64_RUMBLE_PAK.id()));
        context.assertTrue(
                console.installedComponents()
                        .equals(
                                java.util.List.of(
                                        Components.N64_EXPANSION_PAK, Components.N64_RUMBLE_PAK)),
                "Installed components must be reported per slot");
        var saved = console.createNbtWithIdentifyingData(world.getRegistryManager());
        var restored =
                (DockedConsoleBlockEntity)
                        BlockEntity.createFromNbt(
                                pos, console.getCachedState(), saved, world.getRegistryManager());
        context.assertTrue(
                restored != null
                        && restored.installedComponents().equals(console.installedComponents()),
                "Components must survive block entity NBT");
        context.assertTrue(
                "rumble"
                        .equals(
                                console.definition()
                                        .hardware("parallel_n64", console.installedComponents())
                                        .options()
                                        .get("parallel-n64-pak1")),
                "Installed components drive the core options");
        context.assertTrue(
                inventory
                                .removeStack(2)
                                .isOf(ConsoleRegistry.component(Components.N64_RUMBLE_PAK.id()))
                        && console.component(1).isEmpty(),
                "Removing a component must free its slot");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void handheldComponentsLiveInTheItemAndLockWhilePowered(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var gb = new ItemStack(Registries.ITEM.get(Identifier.of("retrocraft", "gb")));
        player.setStackInHand(Hand.MAIN_HAND, gb);
        var host = new HandheldHost(player, player.getStackInHand(Hand.MAIN_HAND));
        var menu =
                new ConsoleScreenHandler(
                        1, player.getInventory(), host, player.getInventory().selectedSlot);
        var cable = stack(Components.GB_LINK_CABLE.id());
        context.assertTrue(
                menu.machineSlots() == 2
                        && menu.getSlot(1).canInsert(cable)
                        && !menu.getSlot(1).canInsert(stack(Components.NES_ZAPPER.id())),
                "The link port must take only the link cable");
        menu.getSlot(1).setStack(cable);
        context.assertTrue(
                HandheldConsoleItem.components(host.stack())
                        .getString("gb_link_port")
                        .equals("retrocraft:gb_link_cable"),
                "Handheld components are stored in the item's NBT");
        context.assertTrue(
                host.definition().links(host.installedComponents()),
                "A plugged cable makes the handheld linkable");
        host.instance(java.util.UUID.randomUUID());
        context.assertTrue(
                !menu.getSlot(1).canTakeItems(player) && !menu.getSlot(1).canInsert(cable),
                "Components are locked while the console runs");
        host.instance(null);
        context.complete();
    }
}
