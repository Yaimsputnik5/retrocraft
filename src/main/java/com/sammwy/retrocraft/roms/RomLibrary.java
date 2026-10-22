package com.sammwy.retrocraft.roms;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class RomLibrary {
    private final Path directory;
    private final long maxBytes;
    private final Set<String> extensions;
    public RomLibrary(Path directory, long maxBytes) { this(directory, maxBytes, Set.of("nes")); }
    public RomLibrary(Path directory, long maxBytes, Set<String> extensions) {
        this.directory = directory; this.maxBytes = maxBytes; this.extensions = Set.copyOf(extensions);
    }

    public List<String> list() throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> {
                try { resolve(name); return true; } catch (IOException ex) { return false; }
            }).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }

    public Path resolve(String name) throws IOException {
        if (name == null || name.length() > 200 || name.contains("/") || name.contains("\\") || name.contains(":")
                || name.chars().anyMatch(c -> c < 32))
            throw new IOException("Invalid ROM name");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!name.contains(".") || !extensions.contains(extension)) throw new IOException("Unsupported cartridge format");
        Path path = directory.resolve(name).normalize();
        Path realRoot = directory.toRealPath();
        if (!path.toRealPath().startsWith(realRoot) || !Files.isRegularFile(path) || Files.size(path) < (extension.equals("nes") ? 16 : 1) || Files.size(path) > maxBytes)
            throw new IOException("ROM missing, escaped library, or outside size limit");
        if (extension.equals("nes")) try (var in = Files.newInputStream(path)) {
            if (!Arrays.equals(in.readNBytes(4), new byte[]{'N', 'E', 'S', 0x1a})) throw new IOException("Not an iNES/NES 2.0 ROM");
        }
        return path.toRealPath();
    }
}
