package com.sammwy.retrocraft.roms;

import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import java.io.*;
import java.nio.file.*;

public final class SaveStates {
    public static void validate(byte[] bytes) throws IOException {
        if (bytes.length > BlobTransfer.MAX_SNAPSHOT) throw new IOException("Snapshot too large");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != WorkerProtocol.MAGIC) throw new IOException("Snapshot format mismatch");
            int state = in.readInt();
            if (state < 1 || state > WorkerProtocol.MAX_STATE || in.skipBytes(state) != state) throw new IOException("Invalid native snapshot");
            int ram = in.readInt();
            if (ram < 0 || ram > WorkerProtocol.MAX_STATE || in.skipBytes(ram) != ram || in.read() != -1) throw new IOException("Invalid SRAM");
        }
    }
    public static void writeAtomic(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "checkpoint-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private SaveStates() {}
}
