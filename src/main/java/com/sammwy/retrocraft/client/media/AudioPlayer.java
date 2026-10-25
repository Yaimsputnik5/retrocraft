package com.sammwy.retrocraft.client.media;

import java.util.ArrayDeque;

import javax.sound.sampled.*;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.stream.AudioSamples;

/** PCM playback stays off the render thread and never builds an unbounded queue. */
public final class AudioPlayer implements AutoCloseable {
    private record Chunk(int rate, byte[] pcm, float volume) {}

    private final ArrayDeque<Chunk> queue = new ArrayDeque<>();
    private int queuedBytes, queuedRate;
    private volatile boolean closed;
    private volatile SourceDataLine line;
    private final Thread thread = Thread.ofVirtual().name("retrocraft-audio").start(this::run);

    public void offer(int rate, byte[] pcm, float volume) {
        if (closed || rate < 8000 || rate > 192000 || pcm.length % 4 != 0) return;
        if (!Float.isFinite(volume) || pcm.length == 0) return;
        Chunk chunk = new Chunk(rate, pcm, Math.max(0, Math.min(1, volume)));
        synchronized (queue) {
            if (queuedRate != rate) {
                queue.clear();
                queuedBytes = 0;
                queuedRate = rate;
            }
            // Bound by audio duration, so both per-frame local audio and tick batches fit.
            int budget = rate * 4 / 8;
            while (!queue.isEmpty() && queuedBytes + pcm.length > budget)
                queuedBytes -= queue.removeFirst().pcm.length;
            queue.addLast(chunk);
            queuedBytes += pcm.length;
            queue.notifyAll();
        }
    }

    private void run() {
        int rate = 0, primed = 0;
        boolean playing = false;
        try {
            while (!closed) {
                Chunk chunk;
                synchronized (queue) {
                    while (queue.isEmpty() && !closed) queue.wait();
                    if (closed) break;
                    chunk = queue.removeFirst();
                    queuedBytes -= chunk.pcm.length;
                }
                if (chunk.volume == 0) continue;
                if (rate != chunk.rate) {
                    releaseLine();
                    AudioFormat format = new AudioFormat(chunk.rate, 16, 2, true, false);
                    SourceDataLine next = AudioSystem.getSourceDataLine(format);
                    next.open(format, Math.max(4096, (chunk.rate * 4 / 10 + 3) & ~3));
                    if (closed) {
                        next.close();
                        break;
                    }
                    line = next;
                    primed = 0;
                    playing = false;
                    rate = chunk.rate;
                }
                SourceDataLine current = line;
                if (current != null) {
                    // Drop stale backlog before scaling; all sample work stays off the game thread.
                    int length = Math.min(chunk.pcm.length, (chunk.rate * 4 / 10) & ~3);
                    byte[] adjusted =
                            AudioSamples.scale(
                                    chunk.pcm, chunk.pcm.length - length, length, chunk.volume);
                    if (!playing && adjusted.length > current.available()) {
                        current.start();
                        playing = true;
                    }
                    current.write(adjusted, 0, adjusted.length);
                    if (!playing) {
                        primed += adjusted.length;
                        if (primed >= chunk.rate * 4 / 16) {
                            current.start();
                            playing = true;
                        }
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            Retrocraft.LOG.warn("Console audio device unavailable: {}", ex.toString());
        } finally {
            closed = true;
            synchronized (queue) {
                queue.clear();
                queuedBytes = 0;
            }
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
        synchronized (queue) {
            queue.clear();
            queuedBytes = 0;
            queue.notifyAll();
        }
        thread.interrupt();
        releaseLine();
    }
}
