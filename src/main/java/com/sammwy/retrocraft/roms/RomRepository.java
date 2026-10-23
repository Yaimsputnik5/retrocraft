package com.sammwy.retrocraft.roms;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

import com.google.gson.*;
import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;

/** Disk catalog: startup reuses metadata without enumerating or hashing the ROM library. */
public final class RomRepository {
    private static final int VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record Catalog(
            int version,
            String root,
            long maxBytes,
            Set<String> formats,
            List<RomScanner.Game> games) {}

    private final Path root, cache;
    private final Map<String, DiscoveryStrategy> formats;
    private final long maxBytes;

    public RomRepository(
            Path root, Path cache, Map<String, DiscoveryStrategy> formats, long maxBytes) {
        this.root = root.toAbsolutePath().normalize();
        this.cache = cache;
        this.formats = formats;
        this.maxBytes = maxBytes;
    }

    public List<RomScanner.Game> initialize(Consumer<String> warning) throws IOException {
        Path index = cache.resolve("catalog.json");
        if (Files.exists(index))
            try {
                if (Files.size(index) > 32 * 1024 * 1024)
                    throw new IOException("Catalog exceeds limit");
                Catalog data = GSON.fromJson(Files.readString(index), Catalog.class);
                if (data != null
                        && data.version == VERSION
                        && root.toString().equals(data.root)
                        && data.maxBytes == maxBytes
                        && formats.keySet().equals(data.formats)
                        && data.games != null
                        && !data.games.isEmpty()
                        && data.games.size() <= 100_000) {
                    for (var game : data.games)
                        if (game == null
                                || !formats.containsKey(game.cartridge())
                                || game.id() == null
                                || !game.id().matches("[0-9a-f]{64}")
                                || game.source() == null
                                || !RomCache.safeFilename(game.filename())
                                || game.name() == null
                                || game.name().length() > 200)
                            throw new IOException("Invalid cached game");
                    return List.copyOf(data.games);
                }
            } catch (IOException | RuntimeException ex) {
                warning.accept("Catalog cache ignored: " + ex.getMessage());
            }
        return scan(warning);
    }

    public List<RomScanner.Game> scan(Consumer<String> warning) throws IOException {
        return persist(new RomScanner(formats, maxBytes).scan(root, warning));
    }

    /** Incremental scan: files whose path is already indexed are kept as-is and never reopened. */
    public List<RomScanner.Game> scanNew(Consumer<String> warning, List<RomScanner.Game> existing)
            throws IOException {
        Set<String> known = new HashSet<>();
        for (var game : existing) known.add(game.source().file());
        var games = new LinkedHashMap<String, RomScanner.Game>();
        for (var game : new RomScanner(formats, maxBytes).scan(root, warning, known))
            games.putIfAbsent(game.cartridge() + "/" + game.id(), game);
        for (var game : existing)
            if (Files.isRegularFile(root.resolve(game.source().file())))
                games.putIfAbsent(game.cartridge() + "/" + game.id(), game);
        return persist(List.copyOf(games.values()));
    }

    private List<RomScanner.Game> persist(List<RomScanner.Game> games) throws IOException {
        byte[] bytes =
                GSON.toJson(
                                new Catalog(
                                        VERSION,
                                        root.toString(),
                                        maxBytes,
                                        formats.keySet(),
                                        games))
                        .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32 * 1024 * 1024) throw new IOException("Catalog exceeds limit");
        SaveStates.writeAtomic(cache.resolve("catalog.json"), bytes);
        return games;
    }
}
