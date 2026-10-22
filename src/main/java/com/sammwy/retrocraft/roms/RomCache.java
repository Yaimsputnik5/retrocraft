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
    public static Path acceptNamed(Path root, String gameId, String filename, byte[] bytes) throws IOException {
        if (gameId == null || !gameId.matches("[0-9a-f]{64}") || !safeFilename(filename) || bytes.length < 1 || bytes.length > BlobTransfer.MAX_ROM || !BlobTransfer.hash(bytes).equals(gameId)) throw new IOException("ROM does not match GameID or filename");
        Path path = root.resolve(gameId).resolve(filename);
        if (!Files.exists(path) || !InstanceStore.sha256(path).equals(gameId)) SaveStates.writeAtomic(path, bytes);
        return path;
    }
    private RomCache() {}
}
