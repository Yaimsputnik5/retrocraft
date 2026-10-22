package com.sammwy.retrocraft.roms;

import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import java.io.IOException;
import java.nio.file.*;

public final class RomCache {
    public static Path accept(Path root, String gameId, String extension, byte[] bytes) throws IOException {
        if (!gameId.matches("[0-9a-f]{64}") || !extension.matches("[a-z0-9]+") || bytes.length > BlobTransfer.MAX_ROM || !BlobTransfer.hash(bytes).equals(gameId))
            throw new IOException("ROM does not match GameID");
        return acceptNamed(root, gameId, "rom." + extension, bytes);
    }
    public static boolean safeFilename(String name) { return name != null && name.matches("[a-zA-Z0-9_-]{1,120}\\.[a-z0-9]{1,12}"); }
    /** Reuse content by hash even if another server gave this ROM a different filename. */
    public static Path find(Path root, String gameId, String filename) throws IOException {
        if (gameId == null || !gameId.matches("[0-9a-f]{64}") || !safeFilename(filename)) throw new IOException("Invalid ROM cache key");
        Path directory = root.resolve(gameId), target = directory.resolve(filename);
        if (valid(target, gameId)) return target;
        if (!Files.isDirectory(directory)) return null;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> safeFilename(path.getFileName().toString())).limit(256).toList()) {
                if (file.equals(target) || !valid(file, gameId)) continue;
                Path staged = Files.createTempFile(directory, "alias-", ".tmp");
                try {
                    Files.copy(file, staged, StandardCopyOption.REPLACE_EXISTING);
                    try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException ex) { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(staged); }
                return target;
            }
        }
        return null;
    }
    private static boolean valid(Path path, String gameId) throws IOException {
        return !Files.isSymbolicLink(path) && Files.isRegularFile(path) && Files.size(path) > 0 && Files.size(path) <= BlobTransfer.MAX_ROM && InstanceStore.sha256(path).equals(gameId);
    }
    public static Path acceptNamed(Path root, String gameId, String filename, byte[] bytes) throws IOException {
        if (gameId == null || !gameId.matches("[0-9a-f]{64}") || !safeFilename(filename) || bytes.length < 1 || bytes.length > BlobTransfer.MAX_ROM || !BlobTransfer.hash(bytes).equals(gameId)) throw new IOException("ROM does not match GameID or filename");
        Path path = root.resolve(gameId).resolve(filename);
        if (!valid(path, gameId)) SaveStates.writeAtomic(path, bytes);
        return path;
    }
    private RomCache() {}
}
