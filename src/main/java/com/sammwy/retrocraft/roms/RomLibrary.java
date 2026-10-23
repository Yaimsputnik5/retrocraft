package com.sammwy.retrocraft.roms;

import com.sammwy.retrocraft.roms.discovery.ArcadeSets;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Safe filesystem operations over the ROM library root ({@code config/retrocraft/roms}).
 * Used by the operator GUI: shared code with no Minecraft dependencies, so the same
 * rules apply whether the caller is the dedicated server or the local single-player backend.
 */
public final class RomLibrary {
    public record Listing(List<Entry> entries, boolean truncated) {}
    public record Entry(String name, boolean directory, long size) {}
    private static final int MAX_ENTRIES = 512;
    private final Path root;

    public RomLibrary(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }
    public Path root() { return root; }

    /** Directory contents, folders first then files by name. Exposes all files, even unrecognised ones. */
    public Listing list(String rel) throws IOException {
        Path dir = resolve(rel);
        if (!Files.isDirectory(dir)) throw new IOException("Not a folder");
        var entries = new ArrayList<Entry>();
        try (var files = Files.list(dir)) {
            List<Path> found = files.filter(path -> !Files.isSymbolicLink(path)).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).limit(MAX_ENTRIES + 1L).toList();
            for (Path path : found) {
                boolean directory = Files.isDirectory(path);
                long size = directory ? 0 : size(path);
                entries.add(new Entry(path.getFileName().toString(), directory, size));
            }
        }
        entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(e -> e.name.toLowerCase(Locale.ROOT)));
        return new Listing(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries, entries.size() > MAX_ENTRIES);
    }

    /** Deletes a file or a whole folder. Recursive deletes only ever happen inside the library. */
    public void delete(String rel) throws IOException {
        Path path = resolveExisting(rel);
        if (path.equals(realRoot())) throw new IOException("Cannot delete the library root");
        if (!Files.isDirectory(path) || Files.isSymbolicLink(path)) { Files.delete(path); return; }
        try (var files = Files.walk(path)) {
            for (Path entry : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(entry);
        }
    }

    public void rename(String rel, String newName) throws IOException {
        Path source = resolveExisting(rel);
        if (!safeName(newName)) throw new IOException("Invalid name");
        Path target = source.resolveSibling(newName);
        if (Files.exists(target)) throw new IOException("A file with that name already exists");
        Files.move(source, target);
    }

    public void move(String rel, String destDir) throws IOException {
        Path source = resolveExisting(rel);
        Path dir = resolve(destDir);
        if (!Files.isDirectory(dir)) throw new IOException("Destination is not a folder");
        if (Files.isDirectory(source) && dir.startsWith(source)) throw new IOException("Cannot move a folder into itself");
        Path target = dir.resolve(source.getFileName().toString());
        if (Files.exists(target)) throw new IOException("A file with that name already exists there");
        Files.move(source, target);
    }

    /** Resolves an existing library file for reading (uploads use {@link #destination}, which rejects collisions). */
    public Path read(String rel) throws IOException { return resolveExisting(rel); }

    /**
     * Writes an upload into the library. With {@code unpack} on, archive containers (zip/tar packs)
     * are unpacked flat — except names that are valid MAME romsets, since those zips are the BIOS itself.
     * Returns how many files landed.
     */
    public int install(String dir, String name, byte[] bytes, boolean unpack) throws IOException {
        Path target = destination(dir, name);
        SaveStates.writeAtomic(target, bytes);
        if (!unpack || !RomArchives.supported(name) || ArcadeSets.contains(name)) return 1;
        int[] count = {0}, visited = {0};
        long[] expanded = {0};
        try {
            RomArchives.visit(target, (member, in) -> {
                if (++visited[0] > MAX_ENTRIES) throw new IOException("Too many files in BIOS archive");
                String base = member.name().replace('\\', '/');
                base = base.substring(base.lastIndexOf('/') + 1);
                if (!safeName(base) || member.size() > 16L * 1024 * 1024 || member.size() == 0) return;
                byte[] content = RomArchives.bounded(in, 16L * 1024 * 1024);
                expanded[0] += content.length;
                if (expanded[0] > 64L * 1024 * 1024) throw new IOException("BIOS archive expands beyond limit");
                try { SaveStates.writeAtomic(destination(dir, base), content); count[0]++; }
                catch (IOException ignored) { }
            });
        } catch (IOException | RuntimeException ex) { return 1; } // not a real archive — keep the file
        if (count[0] == 0) return 1;
        Files.delete(target);
        return count[0];
    }

    public void mkdir(String rel) throws IOException {
        Path path = resolve(rel);
        if (!safeName(path.getFileName().toString())) throw new IOException("Invalid folder name");
        if (Files.exists(path)) throw new IOException("Already exists");
        Path parent = path.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Parent folder does not exist");
        Files.createDirectory(path);
    }

    /** Validates and returns the destination for an upload; never overwrites an existing file. */
    public Path destination(String dir, String name) throws IOException {
        if (!safeName(name)) throw new IOException("Invalid file name");
        Path parent = resolve(dir);
        if (!Files.exists(parent)) {
            // Folders named by the caller (e.g. bios/<coreId>) are created on first upload.
            for (Path segment : realRoot().relativize(parent))
                if (!safeName(segment.toString())) throw new IOException("Invalid folder name");
            Files.createDirectories(parent);
        }
        if (!Files.isDirectory(parent)) throw new IOException("Destination folder does not exist");
        Path target = parent.resolve(name).normalize();
        if (!target.startsWith(realRoot())) throw new IOException("Path escaped library");
        if (Files.exists(target)) throw new IOException("A file with that name already exists");
        return target;
    }

    /** Single file or folder name: one segment, no traversal, no host-reserved characters. */
    public static boolean safeName(String name) {
        if (name == null || name.isBlank() || name.length() > 160 || name.contains("/") || name.contains("\\")
                || name.contains(":") || name.equals(".") || name.equals("..") || name.endsWith(".") || name.endsWith(" ")
                || name.chars().anyMatch(c -> c < 32 || c == 127)) return false;
        return true;
    }

    private Path realRoot() throws IOException { return root.toRealPath(); }

    private Path resolve(String rel) throws IOException {
        Path base = realRoot();
        Path path = rel == null || rel.isEmpty() ? base : base.resolve(rel).normalize();
        if (!path.startsWith(base)) throw new IOException("Path escaped library");
        // Existing paths must not escape through a symlinked parent either.
        Path existing = path;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null || !existing.toRealPath().startsWith(base)) throw new IOException("Path escaped library");
        return path;
    }
    private Path resolveExisting(String rel) throws IOException {
        if (rel == null || rel.isEmpty() || !RomArchives.safeMember(rel)) throw new IOException("Unsafe path");
        Path path = resolve(rel);
        if (path.equals(realRoot())) throw new IOException("Cannot modify the library root");
        if (!Files.exists(path)) throw new IOException("No such file");
        return path;
    }

    private static long size(Path path) {
        try { return Files.size(path); } catch (IOException ex) { return 0; }
    }
}
