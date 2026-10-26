package com.sammwy.retrocraft.roms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoized file digests. An entry is reused only while the file keeps its identity, size and
 * modification time; any change forces a fresh read.
 */
public final class FileHashes {
    private record Stamp(Object key, long size, FileTime modified) {}

    private record Entry(Stamp stamp, String digest) {}

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    public static String sha256(Path file) throws IOException {
        return cached(file, "sha256", stamp -> InstanceStore.sha256(file));
    }

    /** {@link RomScanner#hash}: the full SHA-256 up to {@code limit}, a sized prefix beyond. */
    public static String romId(Path file, long limit) throws IOException {
        return cached(file, "rom:" + limit, stamp -> RomScanner.hash(file, stamp.size(), limit));
    }

    private interface Digest {
        String compute(Stamp stamp) throws IOException;
    }

    private static String cached(Path file, String kind, Digest digest) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class);
        var stamp =
                new Stamp(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
        String key = kind + "\0" + file.toAbsolutePath().normalize();
        Entry entry = CACHE.get(key);
        if (entry != null && entry.stamp.equals(stamp)) return entry.digest;
        String value = digest.compute(stamp);
        CACHE.put(key, new Entry(stamp, value));
        return value;
    }

    private FileHashes() {}
}
