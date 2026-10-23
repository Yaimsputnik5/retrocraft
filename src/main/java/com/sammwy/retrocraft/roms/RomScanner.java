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
    public List<Game> scan(Path library, Consumer<String> warning) throws IOException { return scan(library, warning, Set.of()); }
    /** {@code skip}: relative paths already indexed — their files are not even opened. */
    public List<Game> scan(Path library, Consumer<String> warning, Set<String> skip) throws IOException {
        Files.createDirectories(library); Path root = library.toRealPath(); var games = new LinkedHashMap<String, Game>();
        try (var files = Files.walk(root)) {
            var candidatesOnDisk = files.filter(Files::isRegularFile).limit(100_001).sorted().toList();
            if (candidatesOnDisk.size() > 100_000) throw new IOException("Too many files in ROM library");
            for (Path file : candidatesOnDisk) {
                try {
                    if (!file.toRealPath().startsWith(root)) continue;
                    String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                    if (skip.contains(relative)) continue;
                    // Extension-only matching on the relative path — inDir gates see parent folders, no archive spelunking or magic reads.
                    String cartridge = null;
                    for (var entry : formats.entrySet()) if (entry.getValue().matches(relative)) { cartridge = entry.getKey(); break; }
                    if (cartridge == null) continue;
                    String name = file.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    String title = dot > 0 ? name.substring(0, dot) : name;
                    String filename = safeFilename(title, DiscoveryStrategy.suffix(name));
                    if (ArcadeSets.contains(name)) filename = filename.toLowerCase(Locale.ROOT);
                    String hash = hash(file, Files.size(file));
                    games.putIfAbsent(cartridge + "/" + hash, new Game(cartridge, hash, title.substring(0, Math.min(title.length(), 200)), filename, new RomSource(relative, null, -1)));
                } catch (IOException | IllegalArgumentException ex) { warning.accept(file.getFileName() + ": " + ex.getMessage()); }
                if (games.size() > 100_000) throw new IOException("Too many games in ROM library");
            }
        }
        return List.copyOf(games.values());
    }
    private String hash(Path file, long size) throws IOException { return hash(file, size, maxBytes); }
    /** Content hash: full file when it fits the budget; first 16 MiB + size marker for disc-sized files. */
    public static String hash(Path file, long size, long maxBytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long budget = size <= maxBytes ? size : Math.min(size, 16L * 1024 * 1024);
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536]; long remaining = budget; int count;
                while (remaining > 0 && (count = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) >= 0) {
                    remaining -= count; digest.update(buffer, 0, count);
                }
            }
            if (size > maxBytes) digest.update(longBytes(size));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
    private static byte[] longBytes(long value) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) { out[i] = (byte) value; value >>>= 8; }
        return out;
    }
    public static String safeFilename(String title, String suffix) {
        String clean = title.replaceAll("[^a-zA-Z0-9_-]", "_"); if (clean.isEmpty()) clean = "rom";
        return clean.substring(0, Math.min(clean.length(), 120)) + "." + suffix.toLowerCase(Locale.ROOT);
    }
}
