package com.sammwy.retrocraft.roms;

import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.item.CartridgeItem;
import com.sammwy.retrocraft.roms.discovery.DiscoveryStrategy;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class RomIndex {
    public record Game(CartridgeItem cartridge, String id, String name, String filename, RomSource source, Path root, Path cache, long maxBytes) {
        public RomCatalog.Entry catalogEntry() { return new RomCatalog.Entry(cartridge.id().toString(), id, name); }
        public Path materialize() throws IOException { return source.materialize(root, cache, id, filename, maxBytes); }
    }
    private final Map<String, Game> games;
    private RomIndex(Map<String, Game> games) { this.games = Collections.unmodifiableMap(games); }
    public static RomIndex load(Path root, Path cache, long maxBytes, boolean forceScan, boolean onlyNew, List<RomScanner.Game> previous, Consumer<String> warning) throws IOException {
        Map<String, DiscoveryStrategy> formats = new LinkedHashMap<>();
        for (CartridgeItem cartridge : ConsoleRegistry.cartridges()) formats.put(cartridge.id().toString(), cartridge.discovery());
        var repository = new RomRepository(root, cache, formats, maxBytes);
        var found = onlyNew && previous != null ? repository.scanNew(warning, previous)
                : forceScan ? repository.scan(warning) : repository.initialize(warning);
        Map<String, Game> games = new LinkedHashMap<>();
        for (var game : found) {
            var cartridge = ConsoleRegistry.cartridge(Identifier.of(game.cartridge()));
            games.putIfAbsent(key(cartridge.id(), game.id()), new Game(cartridge, game.id(), game.name(), game.filename(), game.source(), root, cache.resolve("roms"), maxBytes));
        }
        return new RomIndex(games);
    }
    public Game find(CartridgeItem cartridge, String hash) { return games.get(key(cartridge.id(), hash)); }
    public Collection<Game> games() { return games.values(); }
    public List<RomCatalog.Entry> catalog() { return games.values().stream().map(Game::catalogEntry).toList(); }
    private static String key(Identifier cartridge, String hash) { return cartridge + "/" + hash; }
}
