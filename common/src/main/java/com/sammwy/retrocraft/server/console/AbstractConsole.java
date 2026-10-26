package com.sammwy.retrocraft.server.console;

import java.util.*;

import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.console.component.ComponentSlot;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared registration: the system's media item and every accepted component are registered once,
 * however many consoles share them, then the subclass registers its own hardware.
 */
public abstract class AbstractConsole implements ConsoleDefinition {
    private final ResourceLocation id;
    private final String name;
    private final GameSystem system;
    private final List<ComponentSlot> slots;

    protected AbstractConsole(String path, String name, GameSystem system, ComponentSlot... slots) {
        this.id = Ids.of("retrocraft", path);
        this.name = Objects.requireNonNull(name);
        this.system = Objects.requireNonNull(system);
        this.slots = List.of(slots);
        if (name.isBlank()) throw new IllegalArgumentException("Console " + id + " has no name");
        if (this.slots.stream().map(ComponentSlot::id).distinct().count() != this.slots.size())
            throw new IllegalArgumentException("Duplicate component slot on " + id);
    }

    @Override
    public final ResourceLocation id() {
        return id;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final GameSystem system() {
        return system;
    }

    @Override
    public final List<ComponentSlot> componentSlots() {
        return slots;
    }

    @Override
    public final void register() {
        ConsoleRegistry.registerSystem(system);
        for (ComponentSlot slot : slots)
            for (var component : slot.accepts()) ConsoleRegistry.registerComponent(component);
        registerHardware();
    }

    /** The console's own item, block and block entity type. */
    protected abstract void registerHardware();

    @Override
    public String toString() {
        return id.toString();
    }
}
