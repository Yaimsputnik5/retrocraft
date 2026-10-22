package com.sammwy.retrocraft.cores.stream;

import java.io.*;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** RGB888, 8x8 changed tiles, zlib. A delta always names its exact predecessor. */
public final class FrameCodec {
    public static final int MAX_WIDTH = 512, MAX_HEIGHT = 480;
    public static final int MAX_PACKET = 900_000;
    public static final int TILE = 8;

    public record Frame(int width, int height, byte[] rgb) {
        public Frame {
            checkSize(width, height);
            if (rgb.length != width * height * 3) throw new IllegalArgumentException("Invalid RGB frame");
        }
    }

    public record Encoded(long sequence, long base, boolean keyframe, byte[] data) {}

    public static final class Encoder {
        private Frame previous;
        private long sequence;

        public Encoded encode(Frame frame, boolean forceKeyframe) throws IOException {
            boolean key = forceKeyframe || previous == null || previous.width != frame.width || previous.height != frame.height;
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(raw)) {
                out.writeShort(frame.width);
                out.writeShort(frame.height);
                if (key) out.write(frame.rgb);
                else {
                    int columns = (frame.width + TILE - 1) / TILE;
                    for (int y = 0; y < frame.height; y += TILE) {
                        for (int x = 0; x < frame.width; x += TILE) {
                            int w = Math.min(TILE, frame.width - x), h = Math.min(TILE, frame.height - y);
                            boolean changed = false;
                            for (int row = 0; row < h && !changed; row++) {
                                int start = ((y + row) * frame.width + x) * 3;
                                changed = !Arrays.equals(frame.rgb, start, start + w * 3, previous.rgb, start, start + w * 3);
                            }
                            if (!changed) continue;
                            out.writeShort((y / TILE) * columns + x / TILE);
                            for (int row = 0; row < h; row++) out.write(frame.rgb, ((y + row) * frame.width + x) * 3, w * 3);
                        }
                    }
                }
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            try (DeflaterOutputStream out = new DeflaterOutputStream(compressed, deflater)) { raw.writeTo(out); }
            finally { deflater.end(); }
            long base = key ? -1 : sequence;
            previous = frame;
            return new Encoded(++sequence, base, key, compressed.toByteArray());
        }

        public void clear() { previous = null; }
    }

    public static final class Decoder {
        private Frame frame;
        private long sequence = -1;
        public long sequence() { return sequence; }

        public Frame decode(Encoded packet) throws IOException {
            if (packet.data.length > MAX_PACKET) throw new IOException("Oversized frame packet");
            if (packet.sequence <= sequence) return null;
            if (!packet.keyframe && (frame == null || packet.base != sequence)) return null;
            // Bounded decompression prevents compressed packets from allocating arbitrary memory.
            byte[] raw;
            int limit = MAX_WIDTH * MAX_HEIGHT * 3 + (MAX_WIDTH / TILE) * (MAX_HEIGHT / TILE) * 2 + 4;
            try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(packet.data))) {
                raw = in.readNBytes(limit + 1);
                if (raw.length > limit) throw new IOException("Frame expands beyond limit");
            }
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
                int width = in.readUnsignedShort(), height = in.readUnsignedShort();
                try { checkSize(width, height); } catch (IllegalArgumentException ex) { throw new IOException(ex); }
                if (!packet.keyframe && (width != frame.width || height != frame.height)) throw new IOException("Delta geometry changed");
                byte[] rgb = packet.keyframe ? new byte[width * height * 3] : frame.rgb.clone();
                if (packet.keyframe) in.readFully(rgb);
                else {
                    int columns = (width + TILE - 1) / TILE, rows = (height + TILE - 1) / TILE;
                    int last = -1;
                    while (in.available() > 0) {
                        int tile = in.readUnsignedShort();
                        if (tile <= last || tile >= columns * rows) throw new IOException("Invalid delta tile");
                        last = tile;
                        int x = tile % columns * TILE, y = tile / columns * TILE;
                        for (int row = 0; row < Math.min(TILE, height - y); row++) {
                            in.readFully(rgb, ((y + row) * width + x) * 3, Math.min(TILE, width - x) * 3);
                        }
                    }
                }
                if (in.available() != 0) throw new IOException("Trailing frame data");
                frame = new Frame(width, height, rgb);
                sequence = packet.sequence;
                return frame;
            }
        }
    }

    private static void checkSize(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT) throw new IllegalArgumentException("Unsupported NES frame size");
    }
    private FrameCodec() {}
}
