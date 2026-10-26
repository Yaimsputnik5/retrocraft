package com.sammwy.retrocraft.server.console.component;

import java.util.*;

import com.sammwy.retrocraft.cores.worker.HardwareProfile;

import net.minecraft.text.Text;

/**
 * A physical port or bay. {@code empty} is the hardware the core sees with nothing plugged in,
 * keyed by core ID; cores without an entry keep their own defaults.
 */
public record ComponentSlot(
        String id,
        String name,
        List<ConsoleComponent> accepts,
        Map<String, HardwareProfile> empty) {
    public ComponentSlot {
        if (!id.matches("[a-z0-9_]{1,32}")) throw new IllegalArgumentException("Bad slot " + id);
        Objects.requireNonNull(name);
        accepts = List.copyOf(accepts);
        empty = Map.copyOf(empty);
        if (accepts.isEmpty())
            throw new IllegalArgumentException("Slot " + id + " accepts nothing");
    }

    public static ComponentSlot of(String id, String name, ConsoleComponent... accepts) {
        return new ComponentSlot(id, name, List.of(accepts), Map.of());
    }

    public ComponentSlot whenEmpty(String core, HardwareProfile effect) {
        Map<String, HardwareProfile> empty = new HashMap<>(this.empty);
        empty.put(core, effect);
        return new ComponentSlot(id, name, accepts, empty);
    }

    public boolean accepts(ConsoleComponent component) {
        return component != null && accepts.contains(component);
    }

    public HardwareProfile hardware(String core, ConsoleComponent installed) {
        return accepts(installed)
                ? installed.effect(core)
                : empty.getOrDefault(core, HardwareProfile.NONE);
    }

    public Text displayName() {
        return Text.translatableWithFallback("component_slot.retrocraft." + id, name);
    }
}
