package com.sammwy.retrocraft.server.screen;

import java.util.UUID;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.block.DockedConsoleBlock;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ArcadeVariantsTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allSkinsUseTheSameRomBoardAndKeepTheirOwnBlocks(TestContext context) {
        var board = ConsoleRegistry.cartridge(Identifier.of("retrocraft", "mame"));
        String[] ids = {"mame_console", "mame_tetris_console", "mame_space_invaders_console"};
        for (int i = 0; i < ids.length; i++) {
            var definition = ConsoleRegistry.get(Identifier.of("retrocraft", ids[i]));
            context.assertTrue(definition != null, "Every arcade skin must be registered");
            context.assertTrue(
                    definition.type() == ConsoleType.ARCADE,
                    "Arcades must have their own physical type");
            context.assertTrue(
                    definition.type().isPlaced(),
                    "Arcades must use block video and multiplayer paths");
            context.assertTrue(
                    !definition.usesTelevision(), "Every arcade skin must use its integrated CRT");
            context.assertTrue(
                    definition.cartridge() == board,
                    "Every skin must accept the same arcade ROM board");
            var block = (DockedConsoleBlock) Registries.BLOCK.get(definition.id());
            var pos = context.getAbsolutePos(new BlockPos(i * 4, 2, 0));
            var world = context.getWorld();
            world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(pos.up(2), Blocks.AIR.getDefaultState());
            world.setBlockState(pos, block.getDefaultState());
            var console = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
            context.assertTrue(
                    console != null && console.definition() == definition,
                    "A placed skin must keep its console identity");
            var inventory = new ConsoleInventory(console);
            inventory.setStack(0, new ItemStack(board));
            context.assertTrue(
                    inventory.getStack(0).isOf(board),
                    "Each skin must accept a ROM board through the shared inventory");
            console.instance(UUID.randomUUID());
            context.assertTrue(
                    world.getBlockState(pos).get(DockedConsoleBlock.POWERED),
                    "Each skin must expose its powered blockstate");
            console.instance(null);
            context.assertTrue(
                    !world.getBlockState(pos).get(DockedConsoleBlock.POWERED),
                    "Powering off must restore the unpowered skin");
            context.assertTrue(
                    inventory.removeStack(0).isOf(board),
                    "Powering off must preserve the inserted ROM board");
            context.assertTrue(
                    inventory.isEmpty(), "Ejecting must clear the shared cartridge slot");
            world.setBlockState(pos.up(2), Blocks.STONE.getDefaultState());
            context.assertTrue(
                    !block.getDefaultState().canPlaceAt(world, pos),
                    "All three-block cabinets must reject a blocked third block");
            world.setBlockState(pos.up(2), Blocks.AIR.getDefaultState());
            context.assertTrue(
                    block.getDefaultState().canPlaceAt(world, pos),
                    "Cabinets must allow placement with free space above");
            for (Direction facing : Direction.Type.HORIZONTAL) {
                var state = block.getDefaultState().with(DockedConsoleBlock.FACING, facing);
                var bounds = state.getOutlineShape(world, pos).getBoundingBox();
                context.assertTrue(
                        Math.abs(bounds.getLengthY() - 3) < .001,
                        "Every orientation must select the full three-block height");
                context.assertTrue(
                        bounds.getLengthX() > 1 && bounds.getLengthZ() > 1,
                        "Every skin must keep its enlarged footprint after rotation");
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        context.assertTrue(
                Retrocraft.NES_DOCKED.type() == ConsoleType.DOCKED
                        && Retrocraft.NES_DOCKED.usesTelevision(),
                "Home consoles must still use external televisions");
        context.assertTrue(
                !Retrocraft.NES_HANDHELD.type().isPlaced()
                        && !Retrocraft.NES_HANDHELD.usesTelevision(),
                "Handhelds must keep their personal screen path");
        context.complete();
    }
}
