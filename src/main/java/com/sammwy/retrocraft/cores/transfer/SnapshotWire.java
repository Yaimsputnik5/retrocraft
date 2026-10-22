package com.sammwy.retrocraft.cores.transfer;

import java.io.*;
import java.util.zip.*;

/** Stateless snapshot compression; the disk format remains the native state + SRAM envelope. */
public final class SnapshotWire {
    private static final int MAGIC = 0x5243535a;
    public static byte[] pack(byte[] state) throws IOException {
        if (state.length < 1 || state.length > BlobTransfer.MAX_SNAPSHOT) throw new IOException("Snapshot outside bounds");
        var compressed = new ByteArrayOutputStream(); Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (var zip = new DeflaterOutputStream(compressed, deflater)) { zip.write(state); }
        finally { deflater.end(); }
        boolean useCompression = compressed.size() < state.length;
        var wire = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(wire)) {
            out.writeInt(MAGIC); out.writeInt(state.length); out.writeBoolean(useCompression);
            out.write(useCompression ? compressed.toByteArray() : state);
        }
        return wire.toByteArray();
    }
    public static byte[] unpack(byte[] wire) throws IOException {
        if (wire.length < 10 || wire.length > BlobTransfer.MAX_SNAPSHOT + 9) throw new IOException("Invalid snapshot wire size");
        try (var in = new DataInputStream(new ByteArrayInputStream(wire))) {
            if (in.readInt() != MAGIC) throw new IOException("Snapshot wire format mismatch");
            int length = in.readInt(); boolean compressed = in.readBoolean();
            if (length < 1 || length > BlobTransfer.MAX_SNAPSHOT) throw new IOException("Snapshot expands beyond limit");
            if (!compressed) {
                if (in.available() != length) throw new IOException("Invalid raw snapshot length");
                return in.readNBytes(length);
            }
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(wire, 9, wire.length - 9);
                byte[] state = new byte[length]; int offset = 0;
                while (offset < length && !inflater.finished()) {
                    int count = inflater.inflate(state, offset, length - offset);
                    if (count == 0) throw new IOException("Incomplete or invalid compressed snapshot");
                    offset += count;
                }
                // One extra byte detects a stream that lied about its expanded length.
                if (offset != length || inflater.inflate(new byte[1]) != 0 || !inflater.finished() || inflater.getRemaining() != 0) throw new IOException("Snapshot decompression length mismatch");
                return state;
            } catch (DataFormatException ex) { throw new IOException("Invalid compressed snapshot", ex); }
            finally { inflater.end(); }
        }
    }
    private SnapshotWire() {}
}
