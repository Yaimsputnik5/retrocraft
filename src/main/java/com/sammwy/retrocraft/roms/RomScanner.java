package com.sammwy.retrocraft.roms;

import com.sammwy.retrocraft.roms.discovery.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

public final class RomScanner {
    public record Game(String cartridge, String id, String name, String filename, RomSource source) {}
    private final Map<String, DiscoveryStrategy> formats;
    private final long maxBytes;
    public RomScanner(Map<String, DiscoveryStrategy> formats, long maxBytes) {
        this.formats = new LinkedHashMap<>(formats); this.maxBytes = maxBytes;
    }
    public List<Game> scan(Path library, Consumer<String> warning) throws IOException {
        Files.createDirectories(library); Path root = library.toRealPath(); var games = new LinkedHashMap<String, Game>();
        try (var files = Files.walk(root)) {
            var candidatesOnDisk = files.filter(Files::isRegularFile).limit(100_001).sorted().toList();
            if (candidatesOnDisk.size() > 100_000) throw new IOException("Too many files in ROM library");
            for (Path file : candidatesOnDisk) {
                try {
                    if (!file.toRealPath().startsWith(root)) continue;
                    String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                    var found = new ArrayList<Game>(); String name = file.getFileName().toString();
                    // An arcade set is a unit, including all its chips. Never unpack it into separate games.
                    String arcade = formats.entrySet().stream().filter(entry -> DiscoveryStrategy.suffix(name).equals("zip") && entry.getValue().matchesExtension(name, new byte[0])).map(Map.Entry::getKey).findFirst().orElse(null);
                    if (arcade != null) {
                        if (Files.size(file) > maxBytes) continue;
                        RomArchives.list(file); // validate archive structure and entry budget
                        try (InputStream in = Files.newInputStream(file)) { inspect(name, in, new RomSource(relative, null, -1), false, found); }
                    } else if (RomArchives.supported(name)) {
                        List<RomArchives.Member> members = RomArchives.list(file); // names first, before selecting payloads
                        Set<Integer> candidates = new HashSet<>();
                        for (var member : members) if (member.size() <= maxBytes && formats.values().stream().anyMatch(format -> format.recognizesExtension(member.name()))) candidates.add(member.ordinal());
                        if (!candidates.isEmpty()) RomArchives.visit(file, (member, in) -> {
                            if (candidates.contains(member.ordinal())) inspect(member.name(), in, new RomSource(relative, member.name(), member.ordinal()), false, found);
                        });
                    } else if (Files.size(file) <= maxBytes) {
                        try (InputStream in = Files.newInputStream(file)) { inspect(name, in, new RomSource(relative, null, -1), true, found); }
                    }
                    for (Game game : found) games.putIfAbsent(game.cartridge + "/" + game.id, game);
                } catch (IOException | IllegalArgumentException ex) { warning.accept(file.getFileName() + ": " + ex.getMessage()); }
                if (games.size() > 100_000) throw new IOException("Too many games in ROM library");
            }
        }
        return List.copyOf(games.values());
    }
    private void inspect(String name, InputStream input, RomSource source, boolean allowMagic, List<Game> games) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] header = input.readNBytes((int) Math.min(RomFormats.HEADER_BYTES, maxBytes + 1));
            String cartridge = null; DiscoveryStrategy selected = null;
            for (var entry : formats.entrySet()) if (entry.getValue().matchesExtension(name, header)) { cartridge = entry.getKey(); selected = entry.getValue(); break; }
            boolean extension = cartridge != null;
            if (cartridge == null && allowMagic) for (var entry : formats.entrySet()) if (entry.getValue().matchesMagic(name, header)) { cartridge = entry.getKey(); selected = entry.getValue(); break; }
            if (cartridge == null || header.length == 0 || header.length > maxBytes) return;
            long size = header.length; digest.update(header);
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) { size += count; if (size > maxBytes) throw new IOException("ROM exceeds size limit"); digest.update(buffer, 0, count); }
            String base = name.substring(name.lastIndexOf('/') + 1); int dot = base.lastIndexOf('.');
            String title = dot > 0 ? base.substring(0, dot) : base;
            String suffix = extension ? DiscoveryStrategy.suffix(base) : selected.canonicalExtension();
            String filename = safeFilename(title, suffix);
            if (ArcadeSets.contains(name)) filename = filename.toLowerCase(Locale.ROOT);
            games.add(new Game(cartridge, HexFormat.of().formatHex(digest.digest()), title.substring(0, Math.min(title.length(), 200)), filename, source));
        } catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
    public static String safeFilename(String title, String suffix) {
        String clean = title.replaceAll("[^a-zA-Z0-9_-]", "_"); if (clean.isEmpty()) clean = "rom";
        return clean.substring(0, Math.min(clean.length(), 120)) + "." + suffix.toLowerCase(Locale.ROOT);
    }
}
