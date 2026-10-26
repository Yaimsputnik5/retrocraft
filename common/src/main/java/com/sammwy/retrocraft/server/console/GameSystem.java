package com.sammwy.retrocraft.server.console;

import java.util.List;
import java.util.Objects;

import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;

import net.minecraft.resources.ResourceLocation;

/**
 * A ROM family. Its ID keys core mappings, the ROM catalog and the wire protocol; the media item
 * that carries its games is registered separately as {@code <system>_<media>}. {@code cores} lists
 * every core able to run it, the first being the default.
 */
public record GameSystem(
        ResourceLocation id,
        MediaKind media,
        DiscoveryStrategy discovery,
        List<String> cores,
        BiosRequirement bios) {
    public GameSystem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(media);
        Objects.requireNonNull(discovery);
        Objects.requireNonNull(bios);
        cores = List.copyOf(cores);
        if (cores.isEmpty()) throw new IllegalArgumentException("System " + id + " has no core");
    }

    public static GameSystem of(
            String path,
            MediaKind media,
            DiscoveryStrategy discovery,
            BiosRequirement bios,
            String... cores) {
        return new GameSystem(Ids.of("retrocraft", path), media, discovery, List.of(cores), bios);
    }

    public String defaultCore() {
        return cores.getFirst();
    }

    public ResourceLocation mediaId() {
        return Ids.of(id.getNamespace(), id.getPath() + "_" + media.suffix());
    }

    /** Arcade boards are full romsets that the core reads in place from the library. */
    public boolean arcade() {
        return media == MediaKind.BOARD;
    }
}
