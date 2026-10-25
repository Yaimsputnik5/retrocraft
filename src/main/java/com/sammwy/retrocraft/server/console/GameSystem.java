package com.sammwy.retrocraft.server.console;

import java.util.Objects;

import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;

import net.minecraft.util.Identifier;

/**
 * A ROM family. Its ID keys core mappings, the ROM catalog and the wire protocol; the media item
 * that carries its games is registered separately as {@code <system>_<media>}.
 */
public record GameSystem(Identifier id, MediaKind media, DiscoveryStrategy discovery) {
    public GameSystem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(media);
        Objects.requireNonNull(discovery);
    }

    public Identifier mediaId() {
        return Identifier.of(id.getNamespace(), id.getPath() + "_" + media.suffix());
    }

    /** Arcade boards are full romsets that the core reads in place from the library. */
    public boolean arcade() {
        return media == MediaKind.BOARD;
    }
}
