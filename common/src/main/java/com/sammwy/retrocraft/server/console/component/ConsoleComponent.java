package com.sammwy.retrocraft.server.console.component;

import java.util.*;

import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.platform.Ids;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A peripheral or expansion that plugs into a console slot. {@code effects} is keyed by core ID:
 * the same accessory is wired differently by each emulator. A {@code serialLink} accessory joins
 * two running consoles; the server pairs them when they power on.
 */
public record ConsoleComponent(
        ResourceLocation id,
        String name,
        Map<String, HardwareProfile> effects,
        boolean serialLink) {
    public ConsoleComponent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        effects = Map.copyOf(effects);
    }

    public static ConsoleComponent of(String path, String name) {
        return new ConsoleComponent(Ids.of("retrocraft", path), name, Map.of(), false);
    }

    public ConsoleComponent on(String core, HardwareProfile effect) {
        Map<String, HardwareProfile> effects = new HashMap<>(this.effects);
        effects.merge(core, effect, HardwareProfile::merge);
        return new ConsoleComponent(id, name, effects, serialLink);
    }

    public ConsoleComponent link() {
        return new ConsoleComponent(id, name, effects, true);
    }

    public HardwareProfile effect(String core) {
        return effects.getOrDefault(core, HardwareProfile.NONE);
    }

    public Component displayName() {
        return Component.translatableWithFallback(
                "item." + id.getNamespace() + "." + id.getPath(), name);
    }
}
