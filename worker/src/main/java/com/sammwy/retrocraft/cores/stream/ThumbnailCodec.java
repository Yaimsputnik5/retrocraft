package com.sammwy.retrocraft.cores.stream;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

/** Downscales an emulator frame into the tiny PNG used for in-hand handheld previews. */
public final class ThumbnailCodec {
    public static final int MAX_EDGE = 64;

    /** Nearest-neighbour downscale keeps pixel-art frames crisp at icon size. */
    public static byte[] encode(FrameCodec.Frame frame) throws IOException {
        int scale = Math.max(1, Math.ceilDiv(Math.max(frame.width(), frame.height()), MAX_EDGE));
        int w = Math.max(1, frame.width() / scale), h = Math.max(1, frame.height() / scale);
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        byte[] rgb = frame.rgb();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((y * scale) * frame.width() + x * scale) * 3;
                image.setRGB(
                        x, y, (rgb[i] & 255) << 16 | (rgb[i + 1] & 255) << 8 | (rgb[i + 2] & 255));
            }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** Reject oversized image geometry before a native PNG decoder allocates its output buffer. */
    public static boolean valid(byte[] png) {
        if (png.length < 33 || png.length > 65_536) return false;
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10};
        for (int i = 0; i < signature.length; i++) if (png[i] != signature[i]) return false;
        var header = ByteBuffer.wrap(png);
        if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452) return false;
        int width = header.getInt(16), height = header.getInt(20);
        return width > 0 && height > 0 && width <= MAX_EDGE && height <= MAX_EDGE;
    }

    private ThumbnailCodec() {}
}
