package com.sammwy.retrocraft.cores.stream;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** RGB888, 8x8 changed tiles, zlib. A delta always names its exact predecessor. */
public final class FrameCodec {
    public static final int MAX_WIDTH = 512, MAX_HEIGHT = 480;
    public static final int MAX_PACKET = 900_000;
    public static final int TILE = 8;

    public record Frame(int width, int height, byte[] rgb) {
        public Frame {
            checkSize(width, height);
            if (rgb.length != width * height * 3)
                throw new IllegalArgumentException("Invalid RGB frame");
        }
    }

    public record Encoded(long sequence, long base, boolean keyframe, byte[] data) {}

    /** Not thread-safe: one encode at a time. Scratch buffers and the deflater are reused. */
    public static final class Encoder {
        private final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        private byte[] raw = new byte[0], compressed = new byte[0];
        private Frame previous;
        private volatile long sequence;

        public Encoder() {}

        private Encoder(long sequence) {
            this.sequence = sequence;
        }

        public Encoder restart() {
            return new Encoder(sequence);
        }

        public Encoded encode(Frame frame, boolean forceKeyframe) {
            boolean key =
                    forceKeyframe
                            || previous == null
                            || previous.width != frame.width
                            || previous.height != frame.height;
            int columns = (frame.width + TILE - 1) / TILE, rows = (frame.height + TILE - 1) / TILE;
            int capacity = 4 + frame.rgb.length + columns * rows * 2;
            if (raw.length < capacity) raw = new byte[capacity];
            raw[0] = (byte) (frame.width >>> 8);
            raw[1] = (byte) frame.width;
            raw[2] = (byte) (frame.height >>> 8);
            raw[3] = (byte) frame.height;
            int length = key ? 0 : delta(frame, columns);
            // Dense tile changes cost more than a full RGB frame.
            if (key || length >= frame.rgb.length + 4) {
                key = true;
                System.arraycopy(frame.rgb, 0, raw, 4, frame.rgb.length);
                length = 4 + frame.rgb.length;
            }
            long base = key ? -1 : sequence;
            previous = frame;
            return new Encoded(++sequence, base, key, deflate(length));
        }

        private int delta(Frame frame, int columns) {
            int position = 4;
            for (int y = 0; y < frame.height; y += TILE) {
                int h = Math.min(TILE, frame.height - y);
                for (int x = 0; x < frame.width; x += TILE) {
                    int span = Math.min(TILE, frame.width - x) * 3;
                    boolean changed = false;
                    for (int row = 0; row < h && !changed; row++) {
                        int start = ((y + row) * frame.width + x) * 3;
                        changed =
                                Arrays.mismatch(
                                                frame.rgb,
                                                start,
                                                start + span,
                                                previous.rgb,
                                                start,
                                                start + span)
                                        >= 0;
                    }
                    if (!changed) continue;
                    int tile = (y / TILE) * columns + x / TILE;
                    raw[position++] = (byte) (tile >>> 8);
                    raw[position++] = (byte) tile;
                    for (int row = 0; row < h; row++) {
                        System.arraycopy(
                                frame.rgb, ((y + row) * frame.width + x) * 3, raw, position, span);
                        position += span;
                    }
                }
            }
            return position;
        }

        private byte[] deflate(int length) {
            deflater.reset();
            deflater.setInput(raw, 0, length);
            deflater.finish();
            if (compressed.length < 1024) compressed = new byte[Math.max(1024, length / 4)];
            int size = 0;
            while (!deflater.finished()) {
                if (size == compressed.length)
                    compressed = Arrays.copyOf(compressed, compressed.length * 2);
                size += deflater.deflate(compressed, size, compressed.length - size);
            }
            return Arrays.copyOf(compressed, size);
        }

        public void clear() {
            previous = null;
        }
    }

    /** Not thread-safe: decode one stream in order. */
    public static final class Decoder {
        private static final int LIMIT =
                MAX_WIDTH * MAX_HEIGHT * 3 + (MAX_WIDTH / TILE) * (MAX_HEIGHT / TILE) * 2 + 4;
        private final Inflater inflater = new Inflater();
        private byte[] raw = new byte[64 * 1024];
        private Frame frame;
        private long sequence = -1;

        public long sequence() {
            return sequence;
        }

        public Frame decode(Encoded packet) throws IOException {
            if (packet.data.length > MAX_PACKET) throw new IOException("Oversized frame packet");
            if (packet.sequence <= sequence) return null;
            if (!packet.keyframe && (frame == null || packet.base != sequence)) return null;
            int length = inflate(packet.data);
            if (length < 4) throw new EOFException("Truncated frame header");
            int width = (raw[0] & 255) << 8 | raw[1] & 255,
                    height = (raw[2] & 255) << 8 | raw[3] & 255;
            try {
                checkSize(width, height);
            } catch (IllegalArgumentException ex) {
                throw new IOException(ex);
            }
            if (!packet.keyframe && (width != frame.width || height != frame.height))
                throw new IOException("Delta geometry changed");
            if (!packet.keyframe && length == 4) {
                sequence = packet.sequence;
                return frame; // Empty delta: keep the immutable picture, no clone or GPU upload.
            }
            byte[] rgb;
            if (packet.keyframe) {
                if (length != 4 + width * height * 3)
                    throw new IOException("Keyframe size does not match its geometry");
                rgb = Arrays.copyOfRange(raw, 4, length);
            } else rgb = patch(frame.rgb.clone(), width, height, length);
            frame = new Frame(width, height, rgb);
            sequence = packet.sequence;
            return frame;
        }

        /** Bounded decompression prevents compressed packets from allocating arbitrary memory. */
        private int inflate(byte[] data) throws IOException {
            inflater.reset();
            inflater.setInput(data);
            int length = 0;
            try {
                while (!inflater.finished()) {
                    if (length == raw.length) {
                        if (raw.length > LIMIT) throw new IOException("Frame expands beyond limit");
                        raw = Arrays.copyOf(raw, Math.min(LIMIT + 1, raw.length * 2));
                    }
                    int read = inflater.inflate(raw, length, raw.length - length);
                    if (read == 0 && length < raw.length)
                        throw new IOException("Incomplete compressed frame");
                    length += read;
                }
            } catch (DataFormatException ex) {
                throw new IOException("Corrupt compressed frame", ex);
            }
            if (length > LIMIT) throw new IOException("Frame expands beyond limit");
            if (inflater.getRemaining() != 0)
                throw new IOException("Trailing compressed frame data");
            return length;
        }

        private byte[] patch(byte[] rgb, int width, int height, int length) throws IOException {
            int columns = (width + TILE - 1) / TILE, rows = (height + TILE - 1) / TILE;
            int position = 4, last = -1;
            while (position < length) {
                if (position + 2 > length) throw new EOFException("Truncated delta tile");
                int tile = (raw[position] & 255) << 8 | raw[position + 1] & 255;
                position += 2;
                if (tile <= last || tile >= columns * rows)
                    throw new IOException("Invalid delta tile");
                last = tile;
                int x = tile % columns * TILE, y = tile / columns * TILE;
                int span = Math.min(TILE, width - x) * 3, h = Math.min(TILE, height - y);
                if (position + span * h > length) throw new EOFException("Truncated delta tile");
                for (int row = 0; row < h; row++) {
                    System.arraycopy(raw, position, rgb, ((y + row) * width + x) * 3, span);
                    position += span;
                }
            }
            return rgb;
        }
    }

    private static void checkSize(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT)
            throw new IllegalArgumentException("Unsupported frame size");
    }

    private FrameCodec() {}
}
