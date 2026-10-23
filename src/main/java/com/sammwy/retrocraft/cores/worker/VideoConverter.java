package com.sammwy.retrocraft.cores.worker;

import com.sun.jna.Pointer;
import com.sammwy.retrocraft.cores.stream.FrameCodec;

/** Reuses native scanline buffers; each conversion returns an immutable-by-convention RGB frame. */
final class VideoConverter {
    private int[] row32 = new int[0];
    private short[] row16 = new short[0];

    FrameCodec.Frame convert(Pointer pixels, int width, int height, long pitchBytes, int pixelFormat, double aspectRatio) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096)
            throw new IllegalStateException("Unsupported video geometry");
        int bpp = pixelFormat == 1 ? 4 : 2;
        if (pitchBytes < (long) width * bpp || pitchBytes > 32768) throw new IllegalStateException("Invalid video pitch");
        double scale = Math.min(1, Math.min((double) FrameCodec.MAX_WIDTH / width, (double) FrameCodec.MAX_HEIGHT / height));
        int[] size = displaySize(Math.max(1, (int) Math.floor(width * scale)), Math.max(1, (int) Math.floor(height * scale)), aspectRatio);
        int outputWidth = size[0], outputHeight = size[1];
        byte[] rgb = new byte[outputWidth * outputHeight * 3];
        if (bpp == 4 && row32.length < width) row32 = new int[width];
        if (bpp == 2 && row16.length < width) row16 = new short[width];
        for (int y = 0; y < outputHeight; y++) {
            long offset = ((long) y * height / outputHeight) * pitchBytes;
            // One bulk native read per row instead of a JNA call for every pixel.
            if (bpp == 4) pixels.read(offset, row32, 0, width);
            else pixels.read(offset, row16, 0, width);
            for (int x = 0; x < outputWidth; x++) {
                int sourceX = (int) ((long) x * width / outputWidth);
                int p = bpp == 4 ? row32[sourceX] : Short.toUnsignedInt(row16[sourceX]);
                int r, g, b;
                if (pixelFormat == 1) { r = (p >>> 16) & 255; g = (p >>> 8) & 255; b = p & 255; }
                else {
                    int greenBits = pixelFormat == 2 ? 6 : 5;
                    r = ((p >>> (greenBits + 5)) & 31) * 255 / 31;
                    g = ((p >>> 5) & ((1 << greenBits) - 1)) * 255 / ((1 << greenBits) - 1);
                    b = (p & 31) * 255 / 31;
                }
                int target = (y * outputWidth + x) * 3;
                rgb[target] = (byte) r; rgb[target + 1] = (byte) g; rgb[target + 2] = (byte) b;
            }
        }
        return new FrameCodec.Frame(outputWidth, outputHeight, rgb);
    }

    // Cores report the intended display aspect in av_info; resample the whole frame so
    // surfaces never stretch it (e.g. N64 VI output vs its real 4:3 signal).
    static int[] displaySize(int width, int height, double aspectRatio) {
        if (aspectRatio > 0 && Math.abs((double) width / height / aspectRatio - 1) > 0.02) {
            if ((double) width / height > aspectRatio) width = Math.max(1, (int) Math.round(height * aspectRatio));
            else height = Math.max(1, (int) Math.round(width / aspectRatio));
        }
        return new int[]{width, height};
    }
}
