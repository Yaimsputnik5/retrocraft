package com.sammwy.retrocraft.server.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.roms.RomCatalog;
import com.sammwy.retrocraft.server.block.DockedConsoleBlock;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;
import com.sammwy.retrocraft.server.item.ArcadeItem;
import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ArcadeVariantsTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void internalRomSurvivesPlacementSavingBreakingAndReplacement(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
        world.setBlockState(pos.up(2), Blocks.AIR.getDefaultState());
        var item =
                (ArcadeItem)
                        Registries.ITEM.get(Identifier.of("retrocraft", "mame_internal_console"));
        String hash = "a".repeat(64);
        var stack = item.game(hash, "pacman");
        var player = context.createMockCreativeServerPlayerInWorld();
        player.setPosition(pos.toCenterPos().add(3, 0, 0));
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var placement =
                new ItemPlacementContext(
                        new ItemUsageContext(
                                player,
                                Hand.MAIN_HAND,
                                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)));
        context.assertTrue(
                item.place(placement).isAccepted(), "A catalog variant must be placeable");
        var console = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(
                console != null && console.hasInternalRom(), "Placed arcade must be permanent");
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(console.cartridge())),
                "Placement must install its own game");
        var inv = new ConsoleInventory(console);
        context.assertTrue(
                inv.removeStack(0, 1).isEmpty() && inv.removeStack(0).isEmpty(),
                "Both removal paths must reject internal ROMs");
        inv.clear();
        inv.setStack(0, console.definition().cartridge().game("b".repeat(64), "tetris"));
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(console.cartridge())),
                "Clear and replacement must leave the internal game intact");
        var menu = new ConsoleScreenHandler(1, player.getInventory(), console, -1);
        context.assertTrue(
                !menu.getSlot(0).canTakeItems(player)
                        && !menu.getSlot(0).canInsert(console.cartridge()),
                "Internal slot must be locked even while powered off");
        menu.onSlotClick(0, 0, SlotActionType.CLONE, player);
        context.assertTrue(
                menu.getCursorStack().isEmpty(), "Creative clone cannot extract an internal ROM");
        context.assertTrue(
                menu.quickMove(player, 0).isEmpty(), "Shift-click cannot extract an internal ROM");
        context.assertTrue(
                !menu.onButtonClick(player, ConsoleScreenHandler.EJECT),
                "Eject button must reject internal ROMs");
        var saved = console.createNbtWithIdentifyingData(world.getRegistryManager());
        var restored =
                (DockedConsoleBlockEntity)
                        BlockEntity.createFromNbt(
                                pos, console.getCachedState(), saved, world.getRegistryManager());
        context.assertTrue(
                restored != null && hash.equals(CartridgeItem.gameId(restored.cartridge())),
                "Saving and loading must preserve the internal game");
        var picked =
                console.getCachedState()
                        .getBlock()
                        .getPickStack(world, pos, console.getCachedState());
        context.assertTrue(
                hash.equals(ArcadeItem.gameId(picked)), "Pick-block must preserve the variant");
        console.instance(UUID.randomUUID());
        console.instance(null);
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(console.cartridge())),
                "Power cycling must retain the board");
        List<ItemStack> drops =
                Block.getDroppedStacks(console.getCachedState(), world, pos, console);
        context.assertTrue(
                drops.size() == 1
                        && drops.getFirst().isOf(item)
                        && hash.equals(ArcadeItem.gameId(drops.getFirst())),
                "Breaking must drop only the same arcade variant");
        world.breakBlock(pos, true);
        context.assertTrue(
                world.getEntitiesByType(
                                EntityType.ITEM,
                                new net.minecraft.util.math.Box(pos).expand(1),
                                entity -> entity.getStack().isOf(console.definition().cartridge()))
                        .isEmpty(),
                "Breaking must never scatter the internal ROM");
        player.setStackInHand(Hand.MAIN_HAND, drops.getFirst());
        context.assertTrue(
                item.place(
                                new ItemPlacementContext(
                                        new ItemUsageContext(
                                                player,
                                                Hand.MAIN_HAND,
                                                new BlockHitResult(
                                                        Vec3d.ofCenter(pos),
                                                        Direction.UP,
                                                        pos,
                                                        false))))
                        .isAccepted(),
                "Dropped arcade must be placeable again");
        var replaced = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(replaced.cartridge())),
                "Replaced arcade must keep the original game");
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeCatalogSeparatesHardwareGamesAndPermanentArcades(TestContext context) {
        var old = RomCatalog.clientEntries();
        try {
            RomCatalog.clientEntries(
                    List.of(
                            new RomCatalog.Entry("retrocraft:mame", "a".repeat(64), "pacman"),
                            new RomCatalog.Entry("retrocraft:mame", "b".repeat(64), "puckman"),
                            new RomCatalog.Entry("retrocraft:nes", "c".repeat(64), "NES Game")));
            var display =
                    new ItemGroup.DisplayContext(
                            context.getWorld().getEnabledFeatures(),
                            true,
                            context.getWorld().getRegistryManager());
            ItemGroups.updateDisplayContext(display.enabledFeatures(), true, display.lookup());
            Retrocraft.CREATIVE_GROUP.updateEntries(display);
            Retrocraft.GAMES_GROUP.updateEntries(display);
            Retrocraft.ARCADES_GROUP.updateEntries(display);
            var generic = Registries.ITEM.get(Identifier.of("retrocraft", "mame_console"));
            context.assertTrue(
                    Retrocraft.CREATIVE_GROUP.getDisplayStacks().stream()
                            .anyMatch(stack -> stack.isOf(generic)),
                    "Consoles tab must include the generic arcade");
            context.assertTrue(
                    Retrocraft.CREATIVE_GROUP.getDisplayStacks().stream()
                            .noneMatch(
                                    stack ->
                                            stack.getItem() instanceof CartridgeItem
                                                    || stack.getItem() instanceof ArcadeItem),
                    "Consoles tab must contain hardware only");
            context.assertTrue(
                    Retrocraft.GAMES_GROUP.getDisplayStacks().size() == 3,
                    "Games tab must list every ROM");
            context.assertTrue(
                    Retrocraft.ARCADES_GROUP.getDisplayStacks().size() == 2,
                    "Arcades tab must create one variant per arcade ROM, even when art is shared");
            var board = ConsoleRegistry.cartridge(Identifier.of("retrocraft", "mame"));
            List<Text> tooltip = new ArrayList<>();
            board.appendTooltip(
                    board.game("a".repeat(64), "pacman"),
                    Item.TooltipContext.DEFAULT,
                    tooltip,
                    TooltipType.BASIC);
            context.assertTrue(
                    tooltip.size() == 2
                            && tooltip.getFirst().getString().equals("Compatible with:")
                            && tooltip.get(1).getString().strip().equals("Arcade Machine"),
                    "Compatibility must have a heading and a separate console line, without internal variants");
        } finally {
            RomCatalog.clientEntries(old);
        }
        context.complete();
    }

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
