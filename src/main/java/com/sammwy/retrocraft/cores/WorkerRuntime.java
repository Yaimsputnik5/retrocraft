package com.sammwy.retrocraft.cores;

import com.sammwy.retrocraft.roms.SaveStates;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import java.io.*;
import java.nio.file.*;

public final class WorkerRuntime {
    public static Path extract(Path root) throws IOException {
        byte[] bytes;
        try (InputStream resource = WorkerRuntime.class.getResourceAsStream("/retrocraft/retrocraft-worker.jar")) {
            if (resource == null) throw new IOException("Bundled worker missing");
            bytes = resource.readAllBytes();
        }
        Path jar = root.resolve("worker-" + BlobTransfer.hash(bytes).substring(0, 16) + ".jar");
        synchronized (WorkerRuntime.class) { if (!Files.exists(jar)) SaveStates.writeAtomic(jar, bytes); }
        return jar;
    }
    private WorkerRuntime() {}
}
