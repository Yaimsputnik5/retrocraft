package com.sammwy.retrocraft.cores.transfer;

import java.io.*;
import java.security.*;
import java.util.*;

/** Ordered, bounded chunks fit Minecraft's 32 KiB C2S payload limit. */
public final class BlobTransfer {
    public static final int CHUNK = 24_000, MAX_SNAPSHOT = 64 * 1024 * 1024 + 12, MAX_ROM = 64 * 1024 * 1024;
    /** Account for the bounded tick upload/download budget, including scheduling headroom. */
    public static long timeoutSeconds(long minimum, long bytes, int chunksPerTick) {
        if (minimum < 1 || bytes < 0 || bytes > (long) MAX_ROM + MAX_SNAPSHOT + 9 || chunksPerTick < 1) throw new IllegalArgumentException("Invalid transfer budget");
        long bytesPerSecond = (long) CHUNK * chunksPerTick * 20;
        return Math.max(minimum, 10 + 2 * ((bytes + bytesPerSecond - 1) / bytesPerSecond));
    }
    public enum Kind { ROM, SNAPSHOT, FINAL_SNAPSHOT, VIDEO, AUDIO }
    public record Chunk(UUID instance, long epoch, Kind kind, long sequence, int offset, int total, String hash, byte[] bytes) {}
    public static int limit(Kind kind) {
        return switch (kind) { case ROM -> MAX_ROM; case SNAPSHOT, FINAL_SNAPSHOT -> MAX_SNAPSHOT + 9; case VIDEO -> 900_128; case AUDIO -> 262_152; };
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
    public static final class Sender {
        private final UUID instance;
        private final long epoch, sequence;
        private final Kind kind;
        private final byte[] bytes;
        private final String hash;
        private int offset;
        public Sender(UUID instance, long epoch, Kind kind, long sequence, byte[] bytes) {
            if (bytes.length < 1 || bytes.length > limit(kind)) throw new IllegalArgumentException("Blob outside bounds");
            this.instance = instance; this.epoch = epoch; this.kind = kind; this.sequence = sequence; this.bytes = bytes; hash = hash(bytes);
        }
        public boolean done() { return offset == bytes.length; }
        public boolean belongsTo(UUID id, long lease) { return instance.equals(id) && epoch == lease; }
        public Kind kind() { return kind; }
        public Chunk next() {
            if (done()) throw new IllegalStateException("Transfer finished");
            int end = Math.min(bytes.length, offset + CHUNK);
            Chunk chunk = new Chunk(instance, epoch, kind, sequence, offset, bytes.length, hash, Arrays.copyOfRange(bytes, offset, end));
            offset = end; return chunk;
        }
    }
    public static final class Receiver {
        private volatile Chunk first;
        private byte[] bytes;
        private int received;
        private long lastActivity;
        public int reserved() { return first == null ? 0 : first.total; }
        public boolean expired(long now) { return first != null && now - lastActivity > 15_000_000_000L; }
        public byte[] accept(Chunk chunk, long now) throws IOException {
            if (chunk.total < 1 || chunk.total > limit(chunk.kind) || chunk.bytes.length < 1 || chunk.bytes.length > CHUNK
                    || chunk.offset < 0 || chunk.offset > chunk.total - chunk.bytes.length || !chunk.hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid chunk bounds");
            if (first == null) {
                if (chunk.offset != 0) throw new IOException("Missing first chunk");
                bytes = new byte[chunk.total]; received = 0; first = chunk;
            }
            if (!first.instance.equals(chunk.instance) || first.epoch != chunk.epoch || first.kind != chunk.kind
                    || first.sequence != chunk.sequence || first.total != chunk.total || !first.hash.equals(chunk.hash) || chunk.offset != received)
                throw new IOException("Mixed, replayed or out-of-order transfer");
            System.arraycopy(chunk.bytes, 0, bytes, received, chunk.bytes.length);
            received += chunk.bytes.length; lastActivity = now;
            if (received != first.total) return null;
            byte[] result = bytes;
            if (!hash(result).equals(first.hash)) throw new IOException("Blob checksum mismatch");
            first = null; bytes = null; return result;
        }
    }
    private BlobTransfer() {}
}
