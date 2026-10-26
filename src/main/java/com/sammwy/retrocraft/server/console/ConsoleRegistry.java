package com.sammwy.retrocraft.server.console;

import java.util.*;

import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.console.component.ConsoleComponent;
import com.sammwy.retrocraft.server.item.*;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;

public final class ConsoleRegistry {
    private static final Map<Identifier, CartridgeItem> MEDIA = new LinkedHashMap<>();
    private static final Map<Identifier, ComponentItem> COMPONENTS = new LinkedHashMap<>();
    private static final Map<Identifier, ConsoleDefinition> CONSOLES = new LinkedHashMap<>();
    private static final List<TvBlock> TELEVISIONS = new ArrayList<>();

    /** Registers a console; the console itself registers its system, components and hardware. */
    public static ConsoleDefinition register(ConsoleDefinition console) {
        Objects.requireNonNull(console);
        if (console.name().isBlank() || CONSOLES.containsKey(console.id()))
            throw new IllegalArgumentException("Duplicate or unnamed console " + console.id());
        console.register();
        if (console.cartridge() == null || console.cartridge().system() != console.system())
            throw new IllegalStateException(console.id() + " did not register its system");
        CONSOLES.put(console.id(), console);
        return console;
    }

    /** Media item of a system; shared systems register once and must be the same declaration. */
    public static CartridgeItem registerSystem(GameSystem system) {
        Objects.requireNonNull(system);
        CartridgeItem existing = MEDIA.get(system.id());
        if (existing != null) {
            if (existing.system() != system)
                throw new IllegalArgumentException("Conflicting system " + system.id());
            return existing;
        }
        system.cores().forEach(KnownCores::get);
        CartridgeItem item =
                Registry.register(Registries.ITEM, system.mediaId(), new CartridgeItem(system));
        MEDIA.put(system.id(), item);
        return item;
    }

    public static ComponentItem registerComponent(ConsoleComponent component) {
        Objects.requireNonNull(component);
        ComponentItem existing = COMPONENTS.get(component.id());
        if (existing != null) {
            if (!existing.component().equals(component))
                throw new IllegalArgumentException("Conflicting component " + component.id());
            return existing;
        }
        ComponentItem item =
                Registry.register(Registries.ITEM, component.id(), new ComponentItem(component));
        COMPONENTS.put(component.id(), item);
        return item;
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

    public static Collection<ComponentItem> components() {
        return Collections.unmodifiableCollection(COMPONENTS.values());
    }

    public static ComponentItem component(Identifier id) {
        return id == null ? null : COMPONENTS.get(id);
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
