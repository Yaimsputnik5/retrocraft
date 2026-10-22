package com.sammwy.retrocraft.roms;

import java.io.*;
import java.nio.file.*;

/** Persistable reference to an original file or one archive entry. */
public record RomSource(String file, String member, int ordinal) {
    public RomSource {
        if (!RomArchives.safeMember(file) || Path.of(file).isAbsolute() || ordinal < -1
                || ordinal == -1 && member != null || ordinal >= 0 && !RomArchives.safeMember(member)) throw new IllegalArgumentException("Unsafe ROM source");
    }
    public Path materialize(Path library, Path cache, String hash, String filename, long limit) throws IOException {
        if (!hash.matches("[0-9a-f]{64}") || !RomCache.safeFilename(filename)) throw new IOException("Invalid cached ROM reference");
        Path cached = cache.resolve(hash).resolve(filename);
        if (Files.isRegularFile(cached) && Files.size(cached) <= limit && InstanceStore.sha256(cached).equals(hash)) return cached;
        Path root = library.toRealPath(), source = root.resolve(file).normalize().toRealPath();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) throw new IOException("ROM escaped library");
        // Revalidate content when materializing a cache miss. A stale source cannot change GameID.
        byte[] bytes;
        if (ordinal < 0) try (InputStream in = Files.newInputStream(source)) { bytes = RomArchives.bounded(in, limit); }
        else bytes = RomArchives.read(source, member, ordinal, limit);
        return RomCache.acceptNamed(cache, hash, filename, bytes);
    }
}
