package com.sammwy.retrocraft.client.ui.hud;

import java.util.*;

import com.sammwy.retrocraft.server.console.*;

import net.minecraft.util.Identifier;

/** Client-thread registry. Register hardware in ConsoleRegistry before registering its HUD. */
public final class ConsoleHudRegistry {
    private static final Map<Identifier, ConsoleHudRenderer> RENDERERS = new LinkedHashMap<>();

    /** Register or replace a handheld renderer; replacement applies the next time its HUD opens. */
    public static void register(Identifier console, ConsoleHudRenderer renderer) {
        var definition = ConsoleRegistry.get(Objects.requireNonNull(console));
        if (definition == null || definition.type() != ConsoleType.HANDHELD)
            throw new IllegalArgumentException("Register a handheld console first: " + console);
        RENDERERS.put(console, Objects.requireNonNull(renderer));
    }

    public static ConsoleHudRenderer get(Identifier console) {
        return RENDERERS.get(console);
    }

    public static ConsoleHudRenderer require(Identifier console) {
        var renderer = get(console);
        if (renderer == null)
            throw new IllegalStateException("Missing handheld HUD renderer: " + console);
        return renderer;
    }

    static void registerDefault(Identifier console, ConsoleHudRenderer renderer) {
        if (!RENDERERS.containsKey(console)) register(console, renderer);
    }

    private ConsoleHudRegistry() {}
}
