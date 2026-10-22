package dev.sammwy.retrocraft.server;

import dev.sammwy.retrocraft.console.ConsoleRegistry;
import dev.sammwy.retrocraft.item.CartridgeItem;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class RomIndex {
    public record Game(CartridgeItem cartridge, String id, String name, Path path) {
        public RomCatalog.Entry catalogEntry() { return new RomCatalog.Entry(cartridge.id().toString(), id, name); }
    }
    private Map<String, Game> games = Map.of();
    public void scan(Path root, long maxBytes) throws IOException {
        Map<String, Game> scanned = new LinkedHashMap<>();
        for (CartridgeItem cartridge : ConsoleRegistry.cartridges()) {
            Path directory = root.resolve(cartridge.folder());
            Files.createDirectories(directory);
            RomLibrary library = new RomLibrary(directory, maxBytes, cartridge.extensions());
            for (String file : library.list()) {
                Path path = library.resolve(file);
                String hash = InstanceStore.sha256(path);
                String name = file.substring(0, file.lastIndexOf('.'));
                scanned.putIfAbsent(key(cartridge.id(), hash), new Game(cartridge, hash, name, path));
            }
        }
        games = Collections.unmodifiableMap(scanned);
    }
    public Game find(CartridgeItem cartridge, String hash) { return games.get(key(cartridge.id(), hash)); }
    public List<RomCatalog.Entry> catalog() { return games.values().stream().map(Game::catalogEntry).toList(); }
    private static String key(Identifier cartridge, String hash) { return cartridge + "/" + hash; }
}
