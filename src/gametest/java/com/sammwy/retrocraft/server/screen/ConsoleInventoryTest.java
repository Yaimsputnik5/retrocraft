package com.sammwy.retrocraft.server.screen;

import java.util.UUID;

import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.console.ConsoleHost;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

public class ConsoleInventoryTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void concurrentMenusShareTheCartridgeAndCannotWithdrawItTwice(TestContext context) {
        var host = new Host();
        var first = new ConsoleInventory(host);
        var second = new ConsoleInventory(host);
        first.setStack(0, new ItemStack(Items.PAPER));
        context.assertTrue(
                second.getStack(0).getCount() == 1,
                "The second menu must observe a newly inserted cartridge");
        context.assertTrue(
                second.removeStack(0).getCount() == 1,
                "The second menu must withdraw the cartridge");
        context.assertTrue(
                first.isEmpty(), "The first menu must immediately observe the withdrawal");
        context.assertTrue(
                first.removeStack(0).isEmpty(),
                "A second withdrawal must not duplicate the cartridge");
        second.setStack(0, new ItemStack(Items.PAPER));
        context.assertTrue(
                first.removeStack(0, 1).getCount() == 1,
                "Partial removal must return the inserted cartridge");
        context.assertTrue(
                second.getStack(0).isEmpty(), "Partial removal must update every open menu");
        context.complete();
    }

    private static final class Host implements ConsoleHost {
        private ItemStack cartridge = ItemStack.EMPTY;

        public ConsoleDefinition definition() {
            return null;
        }

        public ItemStack cartridge() {
            return cartridge;
        }

        public void cartridge(ItemStack value) {
            cartridge = value;
        }

        public UUID instance() {
            return null;
        }

        public void instance(UUID id) {}

        public ServerWorld world() {
            return null;
        }

        public Vec3d position() {
            return Vec3d.ZERO;
        }

        public boolean valid() {
            return true;
        }

        public boolean canAccess(ServerPlayerEntity player) {
            return false;
        }
    }
}
