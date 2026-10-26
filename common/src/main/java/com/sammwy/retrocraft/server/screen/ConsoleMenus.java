package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.console.*;

import net.minecraft.server.level.ServerPlayer;

public final class ConsoleMenus {
    public static void open(ServerPlayer player, ConsoleHost host) {
        if (!host.canAccess(player)) return;
        if (host.instance() != null && Retrocraft.instances() != null) {
            player.closeContainer();
            if (Retrocraft.instances().needsReset(host)) inventory(player, host, true);
            else Retrocraft.instances().watchVideo(player, host);
            return;
        }
        inventory(player, host);
    }

    public static void inventory(ServerPlayer player, ConsoleHost host) {
        inventory(player, host, false);
    }

    private static void inventory(ServerPlayer player, ConsoleHost host, boolean powered) {
        if (!host.canAccess(player) || !powered && host.instance() != null) return;
        int locked = host instanceof HandheldHost handheld ? handheld.inventorySlot() : -1;
        Platform.openConsoleMenu(player, host, locked);
    }

    private ConsoleMenus() {}
}
