package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.console.*;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ConsoleMenus {
    public static void open(ServerPlayerEntity player, ConsoleHost host) {
        if (!host.canAccess(player)) return;
        if (host.instance() != null && Retrocraft.instances() != null) {
            player.closeHandledScreen();
            if (Retrocraft.instances().needsReset(host)) inventory(player, host, true);
            else Retrocraft.instances().watchVideo(player, host);
            return;
        }
        inventory(player, host);
    }

    public static void inventory(ServerPlayerEntity player, ConsoleHost host) {
        inventory(player, host, false);
    }

    private static void inventory(ServerPlayerEntity player, ConsoleHost host, boolean powered) {
        if (!host.canAccess(player) || !powered && host.instance() != null) return;
        int locked = host instanceof HandheldHost handheld ? handheld.inventorySlot() : -1;
        player.openHandledScreen(
                new ExtendedScreenHandlerFactory<ConsoleOpenData>() {
                    @Override
                    public ConsoleOpenData getScreenOpeningData(ServerPlayerEntity p) {
                        return new ConsoleOpenData(host.definition().id(), locked);
                    }

                    @Override
                    public Text getDisplayName() {
                        return host.definition().displayName();
                    }

                    @Override
                    public ScreenHandler createMenu(
                            int syncId, PlayerInventory inventory, PlayerEntity user) {
                        return new ConsoleScreenHandler(syncId, inventory, host, locked);
                    }
                });
    }

    private ConsoleMenus() {}
}
