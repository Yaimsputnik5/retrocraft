package com.sammwy.retrocraft.cores;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.cores.worker.PadInput;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.roms.GameSaves;
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
        this(
                jar,
                core,
                rom,
                snapshot,
                snapshot.getParent(),
                checkpointSeconds,
                port -> PadInput.digital(input.applyAsInt(port)),
                listener);
    }

    public ClientEmulator(
            Path jar,
            Path core,
            Path rom,
            Path snapshot,
            Path bios,
            int checkpointSeconds,
            IntFunction<PadInput> input,
            Listener listener) {
        this(
                jar,
                core,
                rom,
                snapshot,
                bios,
                HardwareProfile.NONE,
                checkpointSeconds,
                input,
                listener);
    }

    public ClientEmulator(
            Path jar,
            Path core,
            Path rom,
            Path snapshot,
            Path bios,
            HardwareProfile hardware,
            int checkpointSeconds,
            IntFunction<PadInput> input,
            Listener listener) {
        checkpointInterval(checkpointSeconds);
        thread =
                Thread.ofVirtual()
                        .name("retrocraft-client-emulator")
                        .unstarted(
                                () -> {
                                    try (var checkpoints =
                                                    Executors.newSingleThreadExecutor(
                                                            Thread.ofVirtual()
                                                                    .name(
                                                                            "retrocraft-client-checkpoint")
                                                                    .factory());
                                            WorkerProcess worker =
                                                    new WorkerProcess(
                                                            jar,
                                                            core,
                                                            rom,
                                                            snapshot,
                                                            bios,
                                                            snapshot.resolveSibling("worker.log"),
                                                            null,
                                                            hardware)) {
                                        process = worker;
                                        listener.prepared();
                                        while (!committed && !returning && !closed)
                                            LockSupport.parkNanos(10_000_000L);
                                        long next = System.nanoTime(),
                                                nextSave = next + checkpointNanos;
                                        long period = Math.round(1_000_000_000d / worker.fps());
                                        PadInput[] masks = new PadInput[WorkerProtocol.PORTS];
                                        while (committed && !returning && !closed) {
                                            for (int port = 0; port < masks.length; port++)
                                                masks[port] = input.apply(port);
                                            listener.output(
                                                    worker.advance(masks, true, true),
                                                    worker.sampleRate());
                                            long now = System.nanoTime();
                                            if ((now >= nextSave
                                                            || checkpointRequested.getAndSet(false))
                                                    && awaitingCheckpoint.compareAndSet(
                                                            false, true)) {
                                                byte[] bytes = worker.capture();
                                                checkpoints.submit(
                                                        () ->
                                                                publishCheckpoint(
                                                                        snapshot, bytes, false,
                                                                        listener));
                                                nextSave = System.nanoTime() + checkpointNanos;
                                            }
                                            now = System.nanoTime();
                                            next += period;
                                            if (next < now - period * 4) next = now;
                                            if (next > now) LockSupport.parkNanos(next - now);
                                        }
                                        if (returning && !closed) {
                                            byte[] bytes = worker.capture();
                                            checkpoints.submit(
                                                    () ->
                                                            publishCheckpoint(
                                                                    snapshot, bytes, true,
                                                                    listener));
                                        }
                                    } catch (Throwable ex) {
                                        if (!closed) listener.failed(ex);
                                    } finally {
                                        process = null;
                                    }
                                });
        thread.start();
    }

    private void publishCheckpoint(Path snapshot, byte[] bytes, boolean last, Listener listener) {
        if (closed) return;
        try {
            SaveStates.validate(bytes);
            SaveStates.writeAtomic(snapshot, bytes);
            byte[] battery = GameSaves.sram(bytes);
            if (battery != null)
                SaveStates.writeAtomic(snapshot.resolveSibling("battery.sav"), battery);
            if (!closed) listener.checkpoint(bytes, last);
        } catch (Throwable ex) {
            if (!closed) listener.failed(ex);
        }
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
