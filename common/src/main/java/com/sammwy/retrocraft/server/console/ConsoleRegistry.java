package com.sammwy.retrocraft.server.console;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.console.component.ConsoleComponent;
import com.sammwy.retrocraft.server.item.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Declarations are shared; content factories run at the loader's registry event. */
public final class ConsoleRegistry {
    private static final Map<ResourceLocation, GameSystem> SYSTEMS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Supplier<CartridgeItem>> MEDIA =
            new LinkedHashMap<>();
    private static final Map<ResourceLocation, ConsoleComponent> COMPONENT_DEFINITIONS =
            new LinkedHashMap<>();
    private static final Map<ResourceLocation, Supplier<ComponentItem>> COMPONENTS =
            new LinkedHashMap<>();
    private static final Map<ResourceLocation, ConsoleDefinition> CONSOLES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Supplier<BlockEntityType<DockedConsoleBlockEntity>>>
            CONSOLE_TYPES = new LinkedHashMap<>();
    private static final Map<String, Supplier<BlockEntityType<TvBlockEntity>>> TV_TYPES =
            new LinkedHashMap<>();
    private static final List<Supplier<TvBlock>> TELEVISIONS = new ArrayList<>();

    public static ConsoleDefinition register(ConsoleDefinition console) {
        Objects.requireNonNull(console);
        if (console.name().isBlank() || CONSOLES.containsKey(console.id()))
            throw new IllegalArgumentException("Duplicate or unnamed console " + console.id());
        console.register();
        CONSOLES.put(console.id(), console);
        return console;
    }

    public static void registerSystem(GameSystem system) {
        Objects.requireNonNull(system);
        var existing = SYSTEMS.putIfAbsent(system.id(), system);
        if (existing != null) {
            if (existing != system)
                throw new IllegalArgumentException("Conflicting system " + system.id());
            return;
        }
        system.cores().forEach(KnownCores::get);
        MEDIA.put(
                system.id(),
                Platform.register(
                        BuiltInRegistries.ITEM, system.mediaId(), () -> new CartridgeItem(system)));
    }

    public static void registerComponent(ConsoleComponent component) {
        Objects.requireNonNull(component);
        var existing = COMPONENT_DEFINITIONS.putIfAbsent(component.id(), component);
        if (existing != null) {
            if (!existing.equals(component))
                throw new IllegalArgumentException("Conflicting component " + component.id());
            return;
        }
        COMPONENTS.put(
                component.id(),
                Platform.register(
                        BuiltInRegistries.ITEM,
                        component.id(),
                        () -> new ComponentItem(component)));
    }

    static void registerDocked(
            ConsoleDefinition console, Function<DockedConsoleBlock, Item> itemFactory) {
        var id = console.id();
        Supplier<DockedConsoleBlock> block =
                Platform.register(
                        BuiltInRegistries.BLOCK,
                        id,
                        () ->
                                new DockedConsoleBlock(
                                        console,
                                        ContentProperties.block(
                                                id,
                                                BlockBehaviour.Properties.of()
                                                        .strength(2)
                                                        .noOcclusion())));
        CONSOLE_TYPES.put(
                id,
                Platform.register(
                        BuiltInRegistries.BLOCK_ENTITY_TYPE,
                        id,
                        () ->
                                Platform.blockEntityType(
                                        DockedConsoleBlockEntity::new, block.get())));
        Platform.register(BuiltInRegistries.ITEM, id, () -> itemFactory.apply(block.get()));
    }

    public static void registerTelevisions() {
        for (TvBlock.Size size : TvBlock.Size.values()) {
            var id = Ids.of("retrocraft", size.id);
            Supplier<TvBlock> block =
                    Platform.register(
                            BuiltInRegistries.BLOCK,
                            id,
                            () ->
                                    new TvBlock(
                                            size,
                                            ContentProperties.block(
                                                    id,
                                                    BlockBehaviour.Properties.of()
                                                            .strength(2)
                                                            .noOcclusion())));
            TV_TYPES.put(
                    size.id,
                    Platform.register(
                            BuiltInRegistries.BLOCK_ENTITY_TYPE,
                            id,
                            () -> Platform.blockEntityType(TvBlockEntity::new, block.get())));
            Platform.register(
                    BuiltInRegistries.ITEM,
                    id,
                    () ->
                            new BlockItem(
                                    block.get(),
                                    ContentProperties.item(id, new Item.Properties())));
            TELEVISIONS.add(block);
        }
    }

    public static BlockEntityType<DockedConsoleBlockEntity> consoleEntityType(ResourceLocation id) {
        return CONSOLE_TYPES.get(id).get();
    }

    public static BlockEntityType<TvBlockEntity> televisionEntityType(String id) {
        return TV_TYPES.get(id).get();
    }

    public static List<TvBlock> televisions() {
        return TELEVISIONS.stream().map(Supplier::get).toList();
    }

    public static Collection<CartridgeItem> media() {
        return MEDIA.values().stream().map(Supplier::get).toList();
    }

    public static Collection<ComponentItem> components() {
        return COMPONENTS.values().stream().map(Supplier::get).toList();
    }

    public static ComponentItem component(ResourceLocation id) {
        var item = COMPONENTS.get(id);
        return item == null ? null : item.get();
    }

    public static Collection<ConsoleDefinition> consoles() {
        return Collections.unmodifiableCollection(CONSOLES.values());
    }

    public static ConsoleDefinition get(ResourceLocation id) {
        return CONSOLES.get(id);
    }

    /** Media is keyed by system ID, independently of the item's ID. */
    public static CartridgeItem media(ResourceLocation system) {
        var item = MEDIA.get(system);
        return item == null ? null : item.get();
    }

    private ConsoleRegistry() {}
}
