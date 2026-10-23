package com.sammwy.retrocraft.cores.stream;

/** Little-endian, signed stereo PCM16 gain, without modifying shared network/relay buffers. */
public final class AudioSamples {
    public static byte[] scale(byte[] pcm, int offset, int length, float volume) {
        if (offset < 0 || length < 0 || offset > pcm.length - length || (offset | length) % 4 != 0
                || !Float.isFinite(volume) || volume < 0 || volume > 1) throw new IllegalArgumentException("Invalid PCM samples or gain");
        if (volume == 1 && offset == 0 && length == pcm.length) return pcm;
        byte[] result = new byte[length];
        for (int i = 0; i < length; i += 2) {
            int at = offset + i;
            short sample = (short) ((pcm[at] & 255) | (pcm[at + 1] << 8));
            int scaled = Math.round(sample * volume);
            result[i] = (byte) scaled; result[i + 1] = (byte) (scaled >>> 8);
        }
        return result;
    }
    private AudioSamples() {}
}
