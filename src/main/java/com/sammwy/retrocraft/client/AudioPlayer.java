package com.sammwy.retrocraft.client;

import java.util.concurrent.*;

import javax.sound.sampled.*;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.stream.AudioSamples;

/** PCM playback stays off the render thread and never builds an unbounded queue. */
final class AudioPlayer implements AutoCloseable {
    private record Chunk(int rate, byte[] pcm, float volume) {}

    private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(2);
    private volatile boolean closed;
    private volatile SourceDataLine line;
    private final Thread thread = Thread.ofVirtual().name("retrocraft-audio").start(this::run);

    void offer(int rate, byte[] pcm, float volume) {
        if (closed || rate < 8000 || rate > 192000 || pcm.length % 4 != 0) return;
        if (!Float.isFinite(volume) || pcm.length == 0) return;
        Chunk chunk = new Chunk(rate, pcm, Math.max(0, Math.min(1, volume)));
        if (!queue.offer(chunk)) {
            queue.poll();
            queue.offer(chunk);
        }
    }

    private void run() {
        int rate = 0;
        try {
            while (!closed) {
                Chunk chunk = queue.take();
                if (chunk.volume == 0) continue;
                if (rate != chunk.rate) {
                    releaseLine();
                    AudioFormat format = new AudioFormat(chunk.rate, 16, 2, true, false);
                    SourceDataLine next = AudioSystem.getSourceDataLine(format);
                    next.open(format, Math.max(4096, (chunk.rate * 4 / 20 + 3) & ~3));
                    if (closed) {
                        next.close();
                        break;
                    }
                    line = next;
                    line.start();
                    rate = chunk.rate;
                }
                SourceDataLine current = line;
                if (current != null) {
                    // Drop stale backlog before scaling; all sample work stays off the game thread.
                    int length = Math.min(chunk.pcm.length, (chunk.rate * 4 / 10) & ~3);
                    byte[] adjusted =
                            AudioSamples.scale(
                                    chunk.pcm, chunk.pcm.length - length, length, chunk.volume);
                    current.write(adjusted, 0, adjusted.length);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            Retrocraft.LOG.warn("Console audio device unavailable: {}", ex.toString());
        } finally {
            closed = true;
            queue.clear();
            releaseLine();
        }
    }

    private void releaseLine() {
        SourceDataLine current = line;
        line = null;
        if (current != null) {
            current.stop();
            current.flush();
            current.close();
        }
    }

    @Override
    public void close() {
        closed = true;
        queue.clear();
        thread.interrupt();
        releaseLine();
    }
}
