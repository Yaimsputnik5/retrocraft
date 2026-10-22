package com.sammwy.retrocraft;

import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.roms.SaveStates;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferTest {
    @Test void largeRomAndSnapshotDeadlinesCoverTheActualChunkBudget() {
        long outboundBytes = (long) BlobTransfer.MAX_ROM + BlobTransfer.MAX_SNAPSHOT;
        long duration = BlobTransfer.timeoutSeconds(30, outboundBytes, 4);
        assertTrue(duration > outboundBytes / (BlobTransfer.CHUNK * 4L * 20) + 10);
        assertTrue(BlobTransfer.timeoutSeconds(11, 32 * 1024 * 1024, 2) > 60);
        assertEquals(30, BlobTransfer.timeoutSeconds(30, 16_000, 4));
        assertThrows(IllegalArgumentException.class, () -> BlobTransfer.timeoutSeconds(30, Long.MAX_VALUE, 4));
    }
    @Test void snapshotsAreReleasedOnlyAfterEveryBoundedChunkAndChecksumMatch() throws Exception {
        byte[] data = new byte[200_000]; new Random(123).nextBytes(data);
        var sender = new BlobTransfer.Sender(UUID.randomUUID(), 9, BlobTransfer.Kind.SNAPSHOT, 7, data);
        var receiver = new BlobTransfer.Receiver();
        while (!sender.done()) {
            var chunk = sender.next(); assertTrue(chunk.bytes().length <= 24_000);
            byte[] result = receiver.accept(chunk, 0);
            if (sender.done()) assertArrayEquals(data, result); else assertNull(result);
        }
    }
    @Test void truncatedMixedCorruptAndOversizedTransfersAreRejected() throws Exception {
        UUID id = UUID.randomUUID(); byte[] data = new byte[30_000];
        var sender = new BlobTransfer.Sender(id, 1, BlobTransfer.Kind.SNAPSHOT, 1, data);
        var first = sender.next(); var second = sender.next();
        assertThrows(IOException.class, () -> new BlobTransfer.Receiver().accept(second, 0));
        var receiver = new BlobTransfer.Receiver(); assertNull(receiver.accept(first, 0));
        assertThrows(IOException.class, () -> receiver.accept(first, 0));
        var mixed = new BlobTransfer.Chunk(id, 2, second.kind(), second.sequence(), second.offset(), second.total(), second.hash(), second.bytes());
        assertThrows(IOException.class, () -> receiver.accept(mixed, 0));
        byte[] corrupted = second.bytes().clone(); corrupted[0] ^= 1;
        var changed = new BlobTransfer.Chunk(id, 1, second.kind(), second.sequence(), second.offset(), second.total(), second.hash(), corrupted);
        assertThrows(IOException.class, () -> receiver.accept(changed, 0));
        var oversized = new BlobTransfer.Chunk(id, 1, BlobTransfer.Kind.AUDIO, 1, 0, Integer.MAX_VALUE, first.hash(), new byte[]{1});
        assertThrows(IOException.class, () -> new BlobTransfer.Receiver().accept(oversized, 0));
    }
    @Test void snapshotEnvelopeChecksBothStateAndSramLengths() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { out.writeInt(WorkerProtocol.MAGIC); out.writeInt(4); out.writeInt(123); out.writeInt(2); out.writeShort(7); }
        byte[] valid = bytes.toByteArray(); SaveStates.validate(valid);
        assertThrows(IOException.class, () -> SaveStates.validate(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IOException.class, () -> SaveStates.validate(Arrays.copyOf(valid, valid.length + 1)));
        assertThrows(IOException.class, () -> SaveStates.validate(new byte[20]));
    }
}
