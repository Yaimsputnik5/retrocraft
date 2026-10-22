package com.sammwy.retrocraft.roms;

import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import java.io.IOException;
import java.nio.file.*;

public final class RomCache {
    public static Path accept(Path root, String gameId, String extension, byte[] bytes) throws IOException {
        if (!gameId.matches("[0-9a-f]{64}") || !extension.matches("[a-z0-9]+") || bytes.length > BlobTransfer.MAX_ROM || !BlobTransfer.hash(bytes).equals(gameId))
            throw new IOException("ROM does not match GameID");
        Path path = root.resolve(gameId + "." + extension);
        if (!Files.exists(path) || !InstanceStore.sha256(path).equals(gameId)) SaveStates.writeAtomic(path, bytes);
        return path;
    }
    private RomCache() {}
}
