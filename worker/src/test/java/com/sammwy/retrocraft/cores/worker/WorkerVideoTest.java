package com.sammwy.retrocraft.cores.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sun.jna.Memory;

class WorkerVideoTest {
    @Test
    void n64WideViFrameIsResampledToFourByThree() {
        assertArrayEquals(new int[] {256, 192}, VideoConverter.displaySize(512, 192, 4.0 / 3.0));
        assertArrayEquals(new int[] {320, 240}, VideoConverter.displaySize(320, 240, 4.0 / 3.0));
    }

    @Test
    void otherAspectRatiosAndUnknownGeometry() {
        assertArrayEquals(new int[] {256, 192}, VideoConverter.displaySize(256, 480, 4.0 / 3.0));
        assertArrayEquals(new int[] {256, 192}, VideoConverter.displaySize(256, 192, 0));
        assertArrayEquals(new int[] {480, 272}, VideoConverter.displaySize(480, 272, 16.0 / 9.0));
    }

    @Test
    void nativeScanlinesPreserveColorsInAllThreeFormatsAndIgnorePitchPadding() {
        var converter = new VideoConverter();
        for (int format : new int[] {0, 1, 2}) {
            int bpp = format == 1 ? 4 : 2;
            int pitch = 2 * bpp + 8;
            try (Memory pixels = new Memory(pitch * 2L)) {
                pixels.clear();
                int red = format == 1 ? 0xff0000 : format == 2 ? 0xf800 : 0x7c00;
                int green = format == 1 ? 0xff00 : format == 2 ? 0x7e0 : 0x3e0;
                if (bpp == 4) {
                    pixels.setInt(0, red);
                    pixels.setInt(pitch + bpp, green);
                } else {
                    pixels.setShort(0, (short) red);
                    pixels.setShort(pitch + bpp, (short) green);
                }
                var frame = converter.convert(pixels, 2, 2, pitch, format, 0);
                assertArrayEquals(
                        new byte[] {(byte) 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 255, 0},
                        frame.rgb());
            }
        }
    }

    @Test
    void downsamplingUsesWholeSourceAndRejectsInvalidPitch() {
        try (Memory pixels = new Memory(1024 * 4L)) {
            pixels.clear();
            pixels.setInt(1022 * 4L, 0x123456);
            var converter = new VideoConverter();
            var frame = converter.convert(pixels, 1024, 1, 4096, 1, 0);
            assertEquals(512, frame.width());
            assertEquals(0x12, frame.rgb()[frame.rgb().length - 3]);
            assertThrows(
                    IllegalStateException.class,
                    () -> converter.convert(pixels, 1024, 1, 100, 1, 0));
        }
    }
}
