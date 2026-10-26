package com.sammwy.retrocraft.roms.discovery;

import java.util.*;

import com.sammwy.retrocraft.server.console.Consoles;

/**
 * Deterministic filename and directory rules; discovery never sniffs ROM headers or opens archives.
 * Each console's game system declares its own rules; this is the combined view keyed by system
 * name. Shared disc formats (.iso/.chd/.cue/...) only match inside a platform-named folder;
 * console-unique ones match anywhere.
 */
public final class RomFormats {
    public static final Map<String, DiscoveryStrategy> ALL;

    static {
        var formats = new LinkedHashMap<String, DiscoveryStrategy>();
        for (var system : Consoles.systems())
            formats.put(system.id().getPath(), system.discovery());
        ALL = Collections.unmodifiableMap(formats);
    }

    private RomFormats() {}
}
