package com.sammwy.retrocraft.server.console;

import java.util.Objects;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.console.component.ComponentSlot;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/** A placed console block; cartridge and components live in its block entity. */
public class DockedConsole extends AbstractConsole {
    private final Bounds bounds;

    protected DockedConsole(
            String path, String name, GameSystem system, Bounds bounds, ComponentSlot... slots) {
        super(path, name, system, slots);
        this.bounds = Objects.requireNonNull(bounds);
    }

    @Override
    public ConsoleType type() {
        return ConsoleType.DOCKED;
    }

    @Override
    public final Bounds bounds() {
        return bounds;
    }

    @Override
    protected void registerHardware() {
        ConsoleRegistry.registerDocked(this, this::createItem);
    }

    protected Item createItem(DockedConsoleBlock block) {
        return new BlockItem(block, ContentProperties.item(id(), new Item.Properties()));
    }
}
