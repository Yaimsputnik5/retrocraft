package dev.sammwy.retrocraft.screen;

import dev.sammwy.retrocraft.console.*;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ConsoleMenus {
    public static void open(ServerPlayerEntity player, ConsoleHost host) {
        if (!host.canAccess(player)) return;
        int locked = host instanceof HandheldHost handheld ? handheld.inventorySlot() : -1;
        player.openHandledScreen(new ExtendedScreenHandlerFactory<ConsoleOpenData>() {
            @Override public ConsoleOpenData getScreenOpeningData(ServerPlayerEntity p) { return new ConsoleOpenData(host.definition().id(), locked); }
            @Override public Text getDisplayName() { return host.definition().displayName(); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) { return new ConsoleScreenHandler(syncId, inventory, host, locked); }
        });
    }
    private ConsoleMenus() {}
}
