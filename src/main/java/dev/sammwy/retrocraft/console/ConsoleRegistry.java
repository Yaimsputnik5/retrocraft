package dev.sammwy.retrocraft.console;

import dev.sammwy.retrocraft.block.DockedConsoleBlock;
import dev.sammwy.retrocraft.block.DockedConsoleBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import dev.sammwy.retrocraft.item.*;
import net.minecraft.block.AbstractBlock;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import java.util.*;

public final class ConsoleRegistry {
    private static final Map<Identifier, CartridgeItem> CARTRIDGES = new LinkedHashMap<>();
    private static final Map<Identifier, ConsoleDefinition> CONSOLES = new LinkedHashMap<>();

    public static CartridgeItem registerCartridge(Identifier id, Set<String> extensions) {
        Objects.requireNonNull(id); Objects.requireNonNull(extensions);
        if (CARTRIDGES.containsKey(id) || CONSOLES.containsKey(id) || extensions.isEmpty()
                || extensions.stream().anyMatch(extension -> !extension.matches("[a-z0-9]+"))) throw new IllegalArgumentException("Duplicate/invalid cartridge " + id);
        CartridgeItem item = Registry.register(Registries.ITEM, id, new CartridgeItem(id, extensions));
        CARTRIDGES.put(id, item);
        return item;
    }

    public static ConsoleDefinition registerConsole(Identifier id, String name, ConsoleType type, CartridgeItem cartridge) {
        Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(type); Objects.requireNonNull(cartridge);
        if (name.isBlank() || CONSOLES.containsKey(id) || CARTRIDGES.containsKey(id) || !CARTRIDGES.containsValue(cartridge)) throw new IllegalArgumentException("Register a unique console and its cartridge first");
        ConsoleDefinition definition = new ConsoleDefinition(id, name, type, cartridge);
        if (type == ConsoleType.HANDHELD) Registry.register(Registries.ITEM, id, new HandheldConsoleItem(definition));
        else {
            DockedConsoleBlock block = Registry.register(Registries.BLOCK, id, new DockedConsoleBlock(definition, AbstractBlock.Settings.create().strength(2)));
            block.entityType(Registry.register(Registries.BLOCK_ENTITY_TYPE, id,
                    FabricBlockEntityTypeBuilder.create(DockedConsoleBlockEntity::new, block).build()));
            Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        }
        CONSOLES.put(id, definition);
        return definition;
    }
    public static Collection<CartridgeItem> cartridges() { return Collections.unmodifiableCollection(CARTRIDGES.values()); }
    public static Collection<ConsoleDefinition> consoles() { return Collections.unmodifiableCollection(CONSOLES.values()); }
    public static ConsoleDefinition get(Identifier id) { return CONSOLES.get(id); }
    public static CartridgeItem cartridge(Identifier id) { return CARTRIDGES.get(id); }
    private ConsoleRegistry() {}
}
