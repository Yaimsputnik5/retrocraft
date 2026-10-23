package com.sammwy.retrocraft.cores.worker;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.Test;

class PadInputTest {
    @Test
    void sticksDoNotShareBitsWithTheDpadTriggersOrStickClicks() {
        var input =
                new PadInput(0xf0f0, (short) -32767, (short) 1234, (short) 32000, (short) -1234);
        assertEquals(-32767, input.state(5, 0, 0));
        assertEquals(1234, input.state(5, 0, 1));
        assertEquals(32000, input.state(5, 1, 0));
        assertEquals(-1234, input.state(5, 1, 1));
        assertEquals(1, input.state(1, 0, 12));
        assertEquals(1, input.state(1, 0, 13));
        assertEquals(1, input.state(1, 0, 14));
        assertEquals(1, input.state(1, 0, 15));
        assertEquals(32767, input.state(5, 2, 12));
        assertEquals((short) 0xf0f0, input.state(1, 0, 256));
        assertEquals(0, input.state(1, 1, 0));
        assertEquals(0, input.state(5, 3, 0));
        assertEquals(0, PadInput.digital(0xffff).state(5, 0, 0));
    }

    @Test
    void workerWireRoundTripPreservesAllAxesAndAllSixteenButtons() throws Exception {
        var state =
                new PadInput(0xffff, Short.MIN_VALUE, Short.MAX_VALUE, (short) 321, (short) -456);
        var bytes = new ByteArrayOutputStream();
        state.write(new DataOutputStream(bytes));
        assertEquals(10, bytes.size());
        assertEquals(
                state,
                PadInput.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals(0xffff, PadInput.digital(-1).buttons());
    }
}
