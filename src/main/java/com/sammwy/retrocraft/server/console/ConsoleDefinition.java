package com.sammwy.retrocraft.server.console;

import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public record ConsoleDefinition(
        Identifier id, String name, ConsoleType type, CartridgeItem cartridge) {
    public Text displayName() {
        return Text.translatableWithFallback(
                "console." + id.getNamespace() + "." + id.getPath(), name);
    }

    /** Docked consoles without their own screen show their picture on a bound television block. */
    public boolean usesTelevision() {
        return type == ConsoleType.DOCKED;
    }

    public boolean isInternalArcade() {
        return id.getPath().equals("mame_internal_console");
    }

    public boolean isLegacyArcade() {
        return id.getPath().equals("mame_tetris_console")
                || id.getPath().equals("mame_space_invaders_console");
    }

    public boolean isArcade() {
        return type == ConsoleType.ARCADE;
    }
}
