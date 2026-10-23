package com.sammwy.retrocraft.roms;

import java.io.IOException;
import java.nio.file.*;

public final class BiosCache {
    public static boolean matches(Path file, long size, String sha256) {
        try {
            return size > 0 && sha256.matches("[0-9a-f]{64}") && Files.isRegularFile(file) && Files.size(file) == size
                    && InstanceStore.sha256(file).equals(sha256);
        } catch (IOException ex) { return false; }
    }
    private BiosCache() {}
}
