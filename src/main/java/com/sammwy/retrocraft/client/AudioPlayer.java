package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.Retrocraft;
import javax.sound.sampled.*;
import java.util.concurrent.*;

/** PCM playback stays off the render thread and never builds an unbounded queue. */
final class AudioPlayer implements AutoCloseable {
    private record Chunk(int rate, byte[] pcm) {}
    private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(3);
    private volatile boolean closed;
    private volatile SourceDataLine line;
    private final Thread thread = Thread.ofVirtual().name("retrocraft-audio").start(this::run);

    void offer(int rate, byte[] pcm, float volume) {
        if (closed || rate < 8000 || rate > 192000 || pcm.length % 4 != 0) return;
        byte[] adjusted = pcm;
        if (volume < 0.999f) {
            adjusted = pcm.clone();
            for (int i = 0; i < adjusted.length; i += 2) {
                short sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
                int scaled = Math.round(sample * Math.max(0, volume));
                adjusted[i] = (byte) scaled; adjusted[i + 1] = (byte) (scaled >>> 8);
            }
        }
        Chunk chunk = new Chunk(rate, adjusted);
        if (!queue.offer(chunk)) { queue.poll(); queue.offer(chunk); }
    }

    private void run() {
        int rate = 0;
        try {
            while (!closed) {
                Chunk chunk = queue.take();
                if (rate != chunk.rate) {
                    releaseLine();
                    AudioFormat format = new AudioFormat(chunk.rate, 16, 2, true, false);
                    SourceDataLine next = AudioSystem.getSourceDataLine(format);
                    next.open(format, Math.max(4096, chunk.rate * 4 / 10));
                    if (closed) { next.close(); break; }
                    line = next;
                    line.start();
                    rate = chunk.rate;
                }
                SourceDataLine current = line;
                if (current != null) current.write(chunk.pcm, 0, chunk.pcm.length);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            Retrocraft.LOG.warn("NES audio device unavailable: {}", ex.toString());
        } finally { closed = true; queue.clear(); releaseLine(); }
    }

    private void releaseLine() {
        SourceDataLine current = line;
        line = null;
        if (current != null) { current.stop(); current.flush(); current.close(); }
    }

    @Override public void close() {
        closed = true;
        queue.clear();
        thread.interrupt();
        releaseLine();
    }
}
