package com.sammwy.retrocraft;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {
    @Test void keyframesDeltasAndPartialEdgeTilesRoundTrip() throws Exception {
        var encoder = new FrameCodec.Encoder();
        var decoder = new FrameCodec.Decoder();
        byte[] pixels = new byte[257 * 239 * 3];
        new Random(42).nextBytes(pixels);
        var first = encoder.encode(new FrameCodec.Frame(257, 239, pixels), false);
        assertTrue(first.keyframe());
        assertArrayEquals(pixels, decoder.decode(first).rgb());
        byte[] updated = pixels.clone();
        updated[updated.length - 1] ^= 127;
        updated[0] ^= 42;
        var delta = encoder.encode(new FrameCodec.Frame(257, 239, updated), false);
        assertFalse(delta.keyframe());
        assertEquals(first.sequence(), delta.base());
        assertTrue(delta.data().length < first.data().length / 10);
        assertArrayEquals(updated, decoder.decode(delta).rgb());
        var unchanged = encoder.encode(new FrameCodec.Frame(257, 239, updated), false);
        assertArrayEquals(updated, decoder.decode(unchanged).rgb());
    }

    @Test void droppedDeltaRequiresKeyframeAndStaleFramesCannotRollBack() throws Exception {
        var encoder = new FrameCodec.Encoder();
        var decoder = new FrameCodec.Decoder();
        var frame = new FrameCodec.Frame(256, 240, new byte[256 * 240 * 3]);
        var first = encoder.encode(frame, false);
        decoder.decode(first);
        encoder.encode(frame, false); // dropped
        assertNull(decoder.decode(encoder.encode(frame, false)));
        var key = encoder.encode(frame, true);
        assertNotNull(decoder.decode(key));
        assertNull(decoder.decode(first));
        assertEquals(key.sequence(), decoder.sequence());
    }

    @Test void restartingVideoAfterOwnershipReturnKeepsSequenceAndForcesKeyframe() throws Exception {
        var encoder = new FrameCodec.Encoder();
        var decoder = new FrameCodec.Decoder();
        var frame = new FrameCodec.Frame(256, 240, new byte[256 * 240 * 3]);
        decoder.decode(encoder.encode(frame, false));
        for (int i = 0; i < 20; i++) decoder.decode(encoder.encode(frame, false));
        encoder = encoder.restart();
        var returned = encoder.encode(frame, false);
        assertTrue(returned.keyframe());
        assertNotNull(decoder.decode(returned));
        assertEquals(22, returned.sequence());
    }

    @Test void malformedAndOversizedCompressedFramesAreRejected() throws Exception {
        byte[] raw = new byte[2_000_000];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) { out.write(raw); }
        assertThrows(IOException.class, () -> new FrameCodec.Decoder().decode(new FrameCodec.Encoded(1, -1, true, bytes.toByteArray())));
        assertThrows(IOException.class, () -> new FrameCodec.Decoder().decode(new FrameCodec.Encoded(1, -1, true, new byte[]{1, 2, 3})));
        assertThrows(IllegalArgumentException.class, () -> new FrameCodec.Frame(513, 240, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new FrameCodec.Frame(0, 0, new byte[0]));
    }
}
