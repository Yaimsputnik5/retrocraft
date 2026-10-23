package com.sammwy.retrocraft;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.stream.ThumbnailCodec;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class ThumbnailCodecTest {
    @Test void onlyBoundedPngGeometryReachesNativeDecoding() throws Exception {
        byte[] png = ThumbnailCodec.encode(new FrameCodec.Frame(256, 240, new byte[256 * 240 * 3]));
        assertTrue(ThumbnailCodec.valid(png));
        assertFalse(ThumbnailCodec.valid(new byte[33]));
        assertFalse(ThumbnailCodec.valid(new byte[1_000_000]));
        ByteBuffer.wrap(png).putInt(16, 65_535);
        assertFalse(ThumbnailCodec.valid(png));
        ByteBuffer.wrap(png).putInt(16, 0);
        assertFalse(ThumbnailCodec.valid(png));
    }
}
