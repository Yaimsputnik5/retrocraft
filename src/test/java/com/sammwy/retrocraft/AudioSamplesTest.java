package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sammwy.retrocraft.cores.stream.AudioSamples;

class AudioSamplesTest {
    @Test
    void stereoGainPreservesSignAndLeavesTheSharedSourceUntouched() {
        byte[] source = {0, (byte) 0x80, (byte) 0xff, 0x7f, 100, 0, (byte) 156, (byte) 255};
        assertArrayEquals(
                new byte[] {0, (byte) 0xc0, 0, 0x40, 50, 0, (byte) 206, (byte) 255},
                AudioSamples.scale(source, 0, 8, .5f));
        assertArrayEquals(new byte[8], AudioSamples.scale(source, 0, 8, 0));
        assertSame(source, AudioSamples.scale(source, 0, 8, 1));
        assertArrayEquals(
                new byte[] {100, 0, (byte) 156, (byte) 255}, AudioSamples.scale(source, 4, 4, 1));
        assertEquals((byte) 0x80, source[1]);
    }

    @Test
    void invalidGeometryOrGainCannotCorruptPlayback() {
        assertThrows(
                IllegalArgumentException.class, () -> AudioSamples.scale(new byte[8], 0, 7, 1));
        assertThrows(
                IllegalArgumentException.class, () -> AudioSamples.scale(new byte[8], 4, 8, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> AudioSamples.scale(new byte[8], 0, 8, Float.NaN));
        assertThrows(
                IllegalArgumentException.class, () -> AudioSamples.scale(new byte[8], 0, 8, 2));
    }
}
