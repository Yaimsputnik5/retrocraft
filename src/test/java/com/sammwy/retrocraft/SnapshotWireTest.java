package com.sammwy.retrocraft;

import com.sammwy.retrocraft.cores.transfer.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotWireTest {
    @Test void largeEmptyRamCompressesWithoutChangingAnyStateByte() throws Exception {
        byte[] state = new byte[8 * 1024 * 1024]; state[20] = 42; state[state.length - 1] = 7;
        byte[] wire = SnapshotWire.pack(state);
        assertTrue(wire.length < state.length / 100);
        assertArrayEquals(state, SnapshotWire.unpack(wire));
    }
    @Test void incompressibleStateUsesRawPayloadAndSmallStatesStillWork() throws Exception {
        byte[] noise = new byte[8192]; new Random(3).nextBytes(noise);
        assertEquals(noise.length + 9, SnapshotWire.pack(noise).length);
        assertArrayEquals(noise, SnapshotWire.unpack(SnapshotWire.pack(noise)));
        assertArrayEquals(new byte[]{42}, SnapshotWire.unpack(SnapshotWire.pack(new byte[]{42})));
    }
    @Test void corruptTruncatedTrailingAndOversizedStreamsAreRejected() throws Exception {
        byte[] state = new byte[1024 * 1024], wire = SnapshotWire.pack(state);
        assertThrows(IOException.class, () -> SnapshotWire.unpack(Arrays.copyOf(wire, wire.length - 1)));
        assertThrows(IOException.class, () -> SnapshotWire.unpack(Arrays.copyOf(wire, wire.length + 1)));
        byte[] tooSmall = wire.clone(); ByteBuffer.wrap(tooSmall).putInt(4, 100);
        assertThrows(IOException.class, () -> SnapshotWire.unpack(tooSmall));
        byte[] tooLarge = wire.clone(); ByteBuffer.wrap(tooLarge).putInt(4, BlobTransfer.MAX_SNAPSHOT + 1);
        assertThrows(IOException.class, () -> SnapshotWire.unpack(tooLarge));
    }
}
