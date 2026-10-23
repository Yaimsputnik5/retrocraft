package com.sammwy.retrocraft.cores;

import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.roms.SaveStates;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntUnaryOperator;

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
    private volatile WorkerProcess process;
    private final Thread thread;

    /** {@code input} maps a controller port (0-3) to its current 16-bit RetroPad mask. */
    public ClientEmulator(Path jar, Path core, Path rom, Path snapshot, int checkpointSeconds, IntUnaryOperator input, Listener listener) {
        thread = Thread.ofVirtual().name("retrocraft-client-emulator").unstarted(() -> {
            try (WorkerProcess worker = new WorkerProcess(jar, core, rom, snapshot, snapshot.resolveSibling("worker.log"))) {
                process = worker;
                listener.prepared();
                while (!committed && !returning && !closed) LockSupport.parkNanos(10_000_000L);
                long next = System.nanoTime(), nextSave = next + checkpointSeconds * 1_000_000_000L;
                long period = Math.round(1_000_000_000d / worker.fps());
                while (committed && !returning && !closed) {
                    int[] masks = new int[WorkerProtocol.PORTS];
                    for (int port = 0; port < masks.length; port++) masks[port] = input.applyAsInt(port);
                    listener.output(worker.advance(masks, true, true), worker.sampleRate());
                    long now = System.nanoTime();
                    if ((now >= nextSave || checkpointRequested.getAndSet(false)) && awaitingCheckpoint.compareAndSet(false, true)) {
                        worker.save(); byte[] bytes = Files.readAllBytes(snapshot); SaveStates.validate(bytes); listener.checkpoint(bytes, false);
                        nextSave = now + checkpointSeconds * 1_000_000_000L;
                    }
                    next += period; if (next < now - period * 4) next = now;
                    if (next > now) LockSupport.parkNanos(next - now);
                }
                if (returning && !closed) {
                    worker.save(); byte[] bytes = Files.readAllBytes(snapshot); SaveStates.validate(bytes); listener.checkpoint(bytes, true);
                }
            } catch (Throwable ex) { if (!closed) listener.failed(ex); }
            finally { process = null; }
        });
        thread.start();
    }
    public void commit() { committed = true; LockSupport.unpark(thread); }
    public void acknowledgeCheckpoint() { awaitingCheckpoint.set(false); }
    public void requestCheckpoint() { checkpointRequested.set(true); }
    public void returnToServer() { returning = true; LockSupport.unpark(thread); }
    @Override public void close() { closed = true; LockSupport.unpark(thread); WorkerProcess worker = process; if (worker != null) worker.abort(); }
}
