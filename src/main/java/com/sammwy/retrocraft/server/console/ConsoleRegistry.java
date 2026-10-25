package com.sammwy.retrocraft.server.console;

import java.util.*;

import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.item.*;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;

public final class ConsoleRegistry {
    private static final Map<Identifier, CartridgeItem> MEDIA = new LinkedHashMap<>();
    private static final Map<Identifier, ConsoleDefinition> CONSOLES = new LinkedHashMap<>();
    private static final List<TvBlock> TELEVISIONS = new ArrayList<>();

    public static CartridgeItem registerSystem(GameSystem system) {
        Objects.requireNonNull(system);
        if (MEDIA.containsKey(system.id()))
            throw new IllegalArgumentException("Duplicate system " + system.id());
        CartridgeItem item =
                Registry.register(Registries.ITEM, system.mediaId(), new CartridgeItem(system));
        MEDIA.put(system.id(), item);
        return item;
    }

    public static ConsoleDefinition registerConsole(ConsoleDefinition definition) {
        Objects.requireNonNull(definition);
        Identifier id = definition.id();
        if (definition.name().isBlank()
                || CONSOLES.containsKey(id)
                || !MEDIA.containsValue(definition.cartridge()))
            throw new IllegalArgumentException("Register a unique console and its system first");
        if (definition.type() == ConsoleType.HANDHELD)
            Registry.register(Registries.ITEM, id, new HandheldConsoleItem(definition));
        else {
            DockedConsoleBlock block =
                    Registry.register(
                            Registries.BLOCK,
                            id,
                            new DockedConsoleBlock(
                                    definition,
                                    AbstractBlock.Settings.create().strength(2).nonOpaque()));
            block.entityType(
                    Registry.register(
                            Registries.BLOCK_ENTITY_TYPE,
                            id,
                            FabricBlockEntityTypeBuilder.create(
                                            DockedConsoleBlockEntity::new, block)
                                    .build()));
            Registry.register(
                    Registries.ITEM,
                    id,
                    definition.internalRom()
                            ? new ArcadeItem(block, new Item.Settings())
                            : new BlockItem(block, new Item.Settings()));
        }
        CONSOLES.put(id, definition);
        return definition;
    }

    public static void registerTelevisions() {
        for (TvBlock.Size size : TvBlock.Size.values()) {
            Identifier id = Identifier.of("retrocraft", size.id);
            TvBlock block =
                    Registry.register(
                            Registries.BLOCK,
                            id,
                            new TvBlock(
                                    size, AbstractBlock.Settings.create().strength(2).nonOpaque()));
            block.entityType(
                    Registry.register(
                            Registries.BLOCK_ENTITY_TYPE,
                            id,
                            FabricBlockEntityTypeBuilder.create(TvBlockEntity::new, block)
                                    .build()));
            Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
            TELEVISIONS.add(block);
        }
    }

    public static List<TvBlock> televisions() {
        return Collections.unmodifiableList(TELEVISIONS);
    }

    public static Collection<CartridgeItem> media() {
        return Collections.unmodifiableCollection(MEDIA.values());
    }

    public static Collection<ConsoleDefinition> consoles() {
        return Collections.unmodifiableCollection(CONSOLES.values());
    }

    public static ConsoleDefinition get(Identifier id) {
        return CONSOLES.get(id);
    }

    /** The media item of a system, looked up by system ID (e.g. {@code retrocraft:ps2}). */
    public static CartridgeItem media(Identifier system) {
        return system == null ? null : MEDIA.get(system);
    }

    private ConsoleRegistry() {}
}
