package com.sammwy.retrocraft.server.console;

import java.util.Objects;

import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.console.component.ComponentSlot;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.item.*;
import net.minecraft.registry.*;

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
        DockedConsoleBlock block =
                Registry.register(
                        Registries.BLOCK,
                        id(),
                        new DockedConsoleBlock(
                                this, AbstractBlock.Settings.create().strength(2).nonOpaque()));
        block.entityType(
                Registry.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        id(),
                        FabricBlockEntityTypeBuilder.create(DockedConsoleBlockEntity::new, block)
                                .build()));
        Registry.register(Registries.ITEM, id(), createItem(block));
    }

    protected Item createItem(DockedConsoleBlock block) {
        return new BlockItem(block, new Item.Settings());
    }
}
