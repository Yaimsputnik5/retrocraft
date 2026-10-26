package com.sammwy.retrocraft.server.console;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.server.block.DockedConsoleBlock;
import com.sammwy.retrocraft.server.console.component.ComponentSlot;
import com.sammwy.retrocraft.server.item.ArcadeItem;

import net.minecraft.world.item.Item;

/** Three-block cabinet with its own CRT. Internal-ROM cabinets drop their game with the block. */
public class ArcadeConsole extends DockedConsole {
    public static final Bounds CABINET = new Bounds(-4.05, -3.15, 20.05, 19.05, 48);
    private final boolean listed, internalRom;

    protected ArcadeConsole(
            String path,
            String name,
            GameSystem system,
            boolean listed,
            boolean internalRom,
            ComponentSlot... slots) {
        super(path, name, system, CABINET, slots);
        this.listed = listed;
        this.internalRom = internalRom;
    }

    @Override
    public ConsoleType type() {
        return ConsoleType.ARCADE;
    }

    @Override
    public boolean listed() {
        return listed;
    }

    @Override
    public boolean internalRom() {
        return internalRom;
    }

    @Override
    protected Item createItem(DockedConsoleBlock block) {
        return internalRom
                ? new ArcadeItem(block, ContentProperties.item(id(), new Item.Properties()))
                : super.createItem(block);
    }
}
