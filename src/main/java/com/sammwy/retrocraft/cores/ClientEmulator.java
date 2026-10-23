package com.sammwy.retrocraft.cores;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntUnaryOperator;

import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.roms.SaveStates;

/** Native execution backend with a prepare/commit barrier, usable without Minecraft classes. */
public final class ClientEmulator implements AutoCloseable {
    public interface Listener {
        void prepared();

        void output(WorkerProcess.Output output, int sampleRate);

        void checkpoint(byte[] bytes, boolean last);

        void failed(Throwable failure);
    }

    private volatile boolean committed, returning, closed;
    private final AtomicBoolean awaitingCheckpoint = new AtomicBoolean();
    private final AtomicBoolean checkpointRequested = new AtomicBoolean();
    private volatile long checkpointNanos;
    private volatile WorkerProcess process;
    private final Thread thread;

    /** {@code input} maps a controller port (0-3) to its current 16-bit RetroPad mask. */
    public ClientEmulator(
            Path jar,
            Path core,
            Path rom,
            Path snapshot,
            int checkpointSeconds,
            IntUnaryOperator input,
            Listener listener) {
        this(jar, core, rom, snapshot, snapshot.getParent(), checkpointSeconds, input, listener);
    }

    public ClientEmulator(
            Path jar,
            Path core,
            Path rom,
            Path snapshot,
            Path bios,
            int checkpointSeconds,
            IntUnaryOperator input,
            Listener listener) {
        checkpointInterval(checkpointSeconds);
        thread =
                Thread.ofVirtual()
                        .name("retrocraft-client-emulator")
                        .unstarted(
                                () -> {
                                    try (WorkerProcess worker =
                                            new WorkerProcess(
                                                    jar,
                                                    core,
                                                    rom,
                                                    snapshot,
                                                    bios,
                                                    snapshot.resolveSibling("worker.log"))) {
                                        process = worker;
                                        listener.prepared();
                                        while (!committed && !returning && !closed)
                                            LockSupport.parkNanos(10_000_000L);
                                        long next = System.nanoTime(),
                                                nextSave = next + checkpointNanos;
                                        long period = Math.round(1_000_000_000d / worker.fps());
                                        int[] masks = new int[WorkerProtocol.PORTS];
                                        while (committed && !returning && !closed) {
                                            for (int port = 0; port < masks.length; port++)
                                                masks[port] = input.applyAsInt(port);
                                            listener.output(
                                                    worker.advance(masks, true, true),
                                                    worker.sampleRate());
                                            long now = System.nanoTime();
                                            if ((now >= nextSave
                                                            || checkpointRequested.getAndSet(false))
                                                    && awaitingCheckpoint.compareAndSet(
                                                            false, true)) {
                                                worker.save();
                                                byte[] bytes = Files.readAllBytes(snapshot);
                                                SaveStates.validate(bytes);
                                                listener.checkpoint(bytes, false);
                                                nextSave = now + checkpointNanos;
                                            }
                                            next += period;
                                            if (next < now - period * 4) next = now;
                                            if (next > now) LockSupport.parkNanos(next - now);
                                        }
                                        if (returning && !closed) {
                                            worker.save();
                                            byte[] bytes = Files.readAllBytes(snapshot);
                                            SaveStates.validate(bytes);
                                            listener.checkpoint(bytes, true);
                                        }
                                    } catch (Throwable ex) {
                                        if (!closed) listener.failed(ex);
                                    } finally {
                                        process = null;
                                    }
                                });
        thread.start();
    }

    public void checkpointInterval(int seconds) {
        if (seconds < 1 || seconds > 30)
            throw new IllegalArgumentException("Invalid checkpoint interval");
        long interval = seconds * 1_000_000_000L;
        if (checkpointNanos != 0 && checkpointNanos != interval) checkpointRequested.set(true);
        checkpointNanos = interval;
    }

    public void commit() {
        committed = true;
        LockSupport.unpark(thread);
    }

    public void acknowledgeCheckpoint() {
        awaitingCheckpoint.set(false);
    }

    public void requestCheckpoint() {
        checkpointRequested.set(true);
    }

    public void returnToServer() {
        returning = true;
        LockSupport.unpark(thread);
    }

    @Override
    public void close() {
        closed = true;
        LockSupport.unpark(thread);
        WorkerProcess worker = process;
        if (worker != null) worker.abort();
    }
}
