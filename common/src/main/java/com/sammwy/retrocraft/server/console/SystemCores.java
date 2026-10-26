package com.sammwy.retrocraft.server.console;

import java.util.*;

/** Core choices derived from the console declarations, independent of registration. */
public final class SystemCores {
    /** System ID to default core, as declared by each console's game system. */
    public static Map<String, String> defaultMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (var system : Consoles.systems())
            mappings.put(system.id().toString(), system.defaultCore());
        return Collections.unmodifiableMap(mappings);
    }

    /** Cores that can actually run a cartridge, as declared by its game system. */
    public static List<String> compatible(String cartridge) {
        for (var system : Consoles.systems())
            if (system.id().toString().equals(cartridge)) return system.cores();
        return List.of();
    }

    public static String defaultForCartridge(String cartridge) {
        String id = defaultMappings().get(cartridge);
        if (id == null)
            throw new IllegalArgumentException("No known core for cartridge " + cartridge);
        return id;
    }

    private SystemCores() {}
}
