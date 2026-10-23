package com.sammwy.retrocraft.cores.worker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkerVideoTest {
    @Test void n64WideViFrameIsResampledToFourByThree() {
        assertArrayEquals(new int[]{256, 192}, WorkerMain.displaySize(512, 192, 4.0 / 3.0));
        assertArrayEquals(new int[]{320, 240}, WorkerMain.displaySize(320, 240, 4.0 / 3.0));
    }

    @Test void otherAspectRatiosAndUnknownGeometry() {
        assertArrayEquals(new int[]{256, 192}, WorkerMain.displaySize(256, 480, 4.0 / 3.0));
        assertArrayEquals(new int[]{256, 192}, WorkerMain.displaySize(256, 192, 0));
        assertArrayEquals(new int[]{480, 272}, WorkerMain.displaySize(480, 272, 16.0 / 9.0));
    }
}
