package com.sammwy.retrocraft.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiMotionTest {
    @Test
    void closeDuringOpeningIsContinuousAndRepeatedCloseDoesNotRestartIt() {
        var motion = new UiMotion();
        assertEquals(0, motion.value(0));
        float opening = motion.value(80_000_000L);
        assertTrue(opening > 0 && opening < 1);
        motion.show(false, 80_000_000L);
        assertEquals(opening, motion.value(80_000_000L));
        assertTrue(motion.value(160_000_000L) < opening);
        motion.show(false, 180_000_000L);
        assertEquals(0, motion.value(240_000_000L));
        assertTrue(motion.settled(240_000_000L));
    }

    @Test
    void elapsedTimeCompletesMotionEvenAfterAFrameStall() {
        var motion = new UiMotion();
        motion.value(100);
        assertEquals(1, motion.value(2_000_000_100L));
        assertTrue(motion.settled(2_000_000_100L));
        motion.show(false, 2_000_000_100L);
        assertEquals(0, motion.value(4_000_000_100L));
    }
}
