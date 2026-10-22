package com.sammwy.retrocraft.server.console;

import com.sammwy.retrocraft.server.block.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import com.sammwy.retrocraft.server.item.*;
import net.minecraft.block.AbstractBlock;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import java.util.*;
import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;

public final class ConsoleRegistry {
    private static final Map<Identifier, CartridgeItem> CARTRIDGES = new LinkedHashMap<>();
    private static final Map<Identifier, ConsoleDefinition> CONSOLES = new LinkedHashMap<>();
    private static final List<TvBlock> TELEVISIONS = new ArrayList<>();

    public static CartridgeItem registerCartridge(Identifier id, DiscoveryStrategy discovery) {
        Objects.requireNonNull(id); Objects.requireNonNull(discovery);
        if (CARTRIDGES.containsKey(id) || CONSOLES.containsKey(id)) throw new IllegalArgumentException("Duplicate/invalid cartridge " + id);
        CartridgeItem item = Registry.register(Registries.ITEM, id, new CartridgeItem(id, discovery));
        CARTRIDGES.put(id, item);
        return item;
    }

    public static ConsoleDefinition registerConsole(Identifier id, String name, ConsoleType type, CartridgeItem cartridge) {
        Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(type); Objects.requireNonNull(cartridge);
        if (name.isBlank() || CONSOLES.containsKey(id) || CARTRIDGES.containsKey(id) || !CARTRIDGES.containsValue(cartridge)) throw new IllegalArgumentException("Register a unique console and its cartridge first");
        ConsoleDefinition definition = new ConsoleDefinition(id, name, type, cartridge);
        if (type == ConsoleType.HANDHELD) Registry.register(Registries.ITEM, id, new HandheldConsoleItem(definition));
        else {
            DockedConsoleBlock block = Registry.register(Registries.BLOCK, id, new DockedConsoleBlock(definition, AbstractBlock.Settings.create().strength(2).nonOpaque()));
            block.entityType(Registry.register(Registries.BLOCK_ENTITY_TYPE, id,
                    FabricBlockEntityTypeBuilder.create(DockedConsoleBlockEntity::new, block).build()));
            Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        }
        CONSOLES.put(id, definition);
        return definition;
    }
    public static void registerTelevisions() {
        for (TvBlock.Size size : TvBlock.Size.values()) {
            Identifier id = Identifier.of("retrocraft", size.id);
            TvBlock block = Registry.register(Registries.BLOCK, id, new TvBlock(size, AbstractBlock.Settings.create().strength(2).nonOpaque()));
            block.entityType(Registry.register(Registries.BLOCK_ENTITY_TYPE, id, FabricBlockEntityTypeBuilder.create(TvBlockEntity::new, block).build()));
            Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
            TELEVISIONS.add(block);
        }
    }
    public static List<TvBlock> televisions() { return Collections.unmodifiableList(TELEVISIONS); }
    public static Collection<CartridgeItem> cartridges() { return Collections.unmodifiableCollection(CARTRIDGES.values()); }
    public static Collection<ConsoleDefinition> consoles() { return Collections.unmodifiableCollection(CONSOLES.values()); }
    public static ConsoleDefinition get(Identifier id) { return CONSOLES.get(id); }
    public static CartridgeItem cartridge(Identifier id) { return CARTRIDGES.get(id); }
    private ConsoleRegistry() {}
}
