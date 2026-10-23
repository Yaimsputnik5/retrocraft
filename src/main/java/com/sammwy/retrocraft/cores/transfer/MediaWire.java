package com.sammwy.retrocraft.cores.transfer;

import java.io.*;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;

public final class MediaWire {
    public record Audio(int rate, byte[] pcm) {}

    public static byte[] video(FrameCodec.Encoded frame) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(frame.data().length + 21);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeLong(frame.sequence());
            out.writeLong(frame.base());
            out.writeBoolean(frame.keyframe());
            out.writeInt(frame.data().length);
            out.write(frame.data());
        }
        return bytes.toByteArray();
    }

    public static FrameCodec.Encoded video(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            long sequence = in.readLong(), base = in.readLong();
            boolean key = in.readBoolean();
            int length = in.readInt();
            if (length < 1 || length > FrameCodec.MAX_PACKET || length != in.available())
                throw new IOException("Invalid video payload");
            return new FrameCodec.Encoded(sequence, base, key, in.readNBytes(length));
        }
    }

    public static byte[] audio(int rate, byte[] pcm) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(pcm.length + 4);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(rate);
            out.write(pcm);
        }
        return bytes.toByteArray();
    }

    public static Audio audio(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int rate = in.readInt(), length = in.available();
            if (rate < 8000
                    || rate > 192000
                    || length < 1
                    || length > WorkerProtocol.MAX_AUDIO
                    || length % 4 != 0) throw new IOException("Invalid audio payload");
            return new Audio(rate, in.readNBytes(length));
        }
    }

    private MediaWire() {}
}
