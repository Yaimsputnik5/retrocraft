package com.sammwy.retrocraft.gametest;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.compat.TagData;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.Components;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ConsoleComponentsTest implements GameTestPlatform {
    private static ItemStack stack(ResourceLocation id) {
        return new ItemStack(ConsoleRegistry.component(id));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dockedComponentsAreSlotCheckedAndSurviveSaving(GameTestHelper context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(1, 2, 1));
        world.setBlockAndUpdate(
                pos,
                ContentProperties.get(BuiltInRegistries.BLOCK, Ids.of("retrocraft", "n64"))
                        .defaultBlockState());
        var console = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
        var inventory = new ConsoleInventory(console);
        context.assertTrue(
                inventory.getContainerSize() == 3, "N64 exposes cartridge + two component slots");
        inventory.setItem(1, stack(Components.N64_RUMBLE_PAK.id()));
        context.assertTrue(
                inventory.getItem(1).isEmpty(), "The memory bay must reject a Rumble Pak");
        inventory.setItem(1, stack(Components.N64_EXPANSION_PAK.id()));
        inventory.setItem(2, stack(Components.N64_RUMBLE_PAK.id()));
        context.assertTrue(
                console.installedComponents()
                        .equals(
                                java.util.List.of(
                                        Components.N64_EXPANSION_PAK, Components.N64_RUMBLE_PAK)),
                "Installed components must be reported per slot");
        var saved = console.saveWithFullMetadata(world.registryAccess());
        var restored =
                (DockedConsoleBlockEntity)
                        BlockEntity.loadStatic(
                                pos, console.getBlockState(), saved, world.registryAccess());
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
                                .removeItemNoUpdate(2)
                                .is(ConsoleRegistry.component(Components.N64_RUMBLE_PAK.id()))
                        && console.component(1).isEmpty(),
                "Removing a component must free its slot");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void handheldComponentsLiveInTheItemAndLockWhilePowered(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        var gb =
                new ItemStack(
                        ContentProperties.get(BuiltInRegistries.ITEM, Ids.of("retrocraft", "gb")));
        player.setItemInHand(InteractionHand.MAIN_HAND, gb);
        var host = new HandheldHost(player, player.getItemInHand(InteractionHand.MAIN_HAND));
        var menu =
                new ConsoleScreenHandler(
                        1, player.getInventory(), host, player.getInventory().selected);
        var cable = stack(Components.GB_LINK_CABLE.id());
        context.assertTrue(
                menu.machineSlots() == 2
                        && menu.getSlot(1).mayPlace(cable)
                        && !menu.getSlot(1).mayPlace(stack(Components.NES_ZAPPER.id())),
                "The link port must take only the link cable");
        menu.getSlot(1).setByPlayer(cable);
        context.assertTrue(
                TagData.string(HandheldConsoleItem.components(host.stack()), "gb_link_port")
                        .equals("retrocraft:gb_link_cable"),
                "Handheld components are stored in the item's NBT");
        context.assertTrue(
                host.definition().links(host.installedComponents()),
                "A plugged cable makes the handheld linkable");
        host.instance(java.util.UUID.randomUUID());
        context.assertTrue(
                !menu.getSlot(1).mayPickup(player) && !menu.getSlot(1).mayPlace(cable),
                "Components are locked while the console runs");
        host.instance(null);
        context.succeed();
    }
}
