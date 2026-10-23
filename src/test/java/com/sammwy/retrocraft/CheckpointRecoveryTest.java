package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.roms.InstanceStore;
import com.sammwy.retrocraft.roms.SaveStates;

class CheckpointRecoveryTest {
    @TempDir Path root;

    @Test
    void interruptedRotationRecoversThePreviousCompleteSnapshot() throws Exception {
        var store = new InstanceStore(root);
        UUID id = UUID.randomUUID();
        byte[] state = state(7);
        SaveStates.writeAtomic(store.snapshot(id), state);
        assertThrows(IOException.class, () -> store.promote(id, root.resolve("missing.bin")));
        assertFalse(Files.exists(store.snapshot(id)));
        store.recover(id);
        assertArrayEquals(state, Files.readAllBytes(store.snapshot(id)));
        SaveStates.writeAtomic(store.snapshot(id), state(8));
        store.recover(id);
        assertArrayEquals(
                state(8),
                Files.readAllBytes(store.snapshot(id)),
                "A valid current snapshot must never be replaced by an older backup");
    }

    @Test
    void corruptBackupCannotBecomeTheActiveSnapshot() throws Exception {
        var store = new InstanceStore(root);
        UUID id = UUID.randomUUID();
        SaveStates.writeAtomic(store.backup(id), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> store.recover(id));
        assertFalse(Files.exists(store.snapshot(id)));
    }

    private static byte[] state(int value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(WorkerProtocol.MAGIC);
            out.writeInt(4);
            out.writeInt(value);
            out.writeInt(0);
        }
        return bytes.toByteArray();
    }
}
