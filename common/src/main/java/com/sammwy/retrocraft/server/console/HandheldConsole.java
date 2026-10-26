package com.sammwy.retrocraft.server.console;

import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.console.component.ComponentSlot;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** A console item; cartridge and components live in the stack's data. */
public class HandheldConsole extends AbstractConsole {
    protected HandheldConsole(String path, String name, GameSystem system, ComponentSlot... slots) {
        super(path, name, system, slots);
    }

    @Override
    public ConsoleType type() {
        return ConsoleType.HANDHELD;
    }

    @Override
    protected void registerHardware() {
        Platform.register(BuiltInRegistries.ITEM, id(), this::createItem);
    }

    protected Item createItem() {
        return new HandheldConsoleItem(this);
    }
}
