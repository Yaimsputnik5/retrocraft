package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.cores.*;
import com.sammwy.retrocraft.roms.SaveStates;

class HybridIntegrationTest {
    @TempDir Path root;

    @Test
    void prepareCommitFinalSaveAndServerResumePreserveExactVideo() throws Exception {
        roundtrip(false);
    }

    @Test
    void serverCanRecoverPeriodicCheckpointAfterAbruptClientTermination() throws Exception {
        roundtrip(true);
    }

    private void roundtrip(boolean crash) throws Exception {
        String configured = System.getProperty("retrocraft.testCore");
        assumeTrue(configured != null);
        Path core = Path.of(configured), jar = Path.of(System.getProperty("retrocraft.workerJar"));
        Path rom = Files.write(root.resolve("homebrew.nes"), HomebrewRom.bytes()),
                state = root.resolve("server/snapshot.bin");
        try (WorkerProcess server =
                new WorkerProcess(jar, core, rom, state, root.resolve("server.log"))) {
            for (int i = 0; i < 90; i++) server.advance(0);
            server.save();
        }
        List<byte[]> expected = new ArrayList<>();
        byte[] expectedNext;
        try (WorkerProcess reference =
                new WorkerProcess(jar, core, rom, state, root.resolve("reference.log"))) {
            for (int i = 0; i < 12; i++) expected.add(reference.advance(0).frame().rgb());
            expectedNext = reference.advance(0).frame().rgb();
        }
        Path local = root.resolve("client/snapshot.bin");
        SaveStates.writeAtomic(local, Files.readAllBytes(state));
        CountDownLatch prepared = new CountDownLatch(1),
                frameArrived = new CountDownLatch(1),
                saved = new CountDownLatch(1);
        AtomicReference<ClientEmulator> runner = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<byte[]> checkpoint = new AtomicReference<>();
        List<byte[]> actual = Collections.synchronizedList(new ArrayList<>());
        try (ClientEmulator client =
                new ClientEmulator(
                        jar,
                        core,
                        rom,
                        local,
                        30,
                        port -> 0,
                        new ClientEmulator.Listener() {
                            public void prepared() {
                                prepared.countDown();
                            }

                            public void output(WorkerProcess.Output output, int sampleRate) {
                                actual.add(output.frame().rgb());
                                frameArrived.countDown();
                                if (actual.size() == 12) {
                                    if (crash) runner.get().requestCheckpoint();
                                    else runner.get().returnToServer();
                                }
                            }

                            public void checkpoint(byte[] bytes, boolean last) {
                                checkpoint.set(bytes);
                                if (crash) runner.get().close();
                                saved.countDown();
                            }

                            public void failed(Throwable ex) {
                                failure.set(ex);
                                prepared.countDown();
                                saved.countDown();
                            }
                        })) {
            runner.set(client);
            assertTrue(prepared.await(10, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertFalse(
                    frameArrived.await(100, TimeUnit.MILLISECONDS),
                    "Client must not advance before COMMIT");
            client.commit();
            assertTrue(saved.await(10, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals(12, actual.size());
            for (int i = 0; i < expected.size(); i++)
                assertArrayEquals(expected.get(i), actual.get(i));
            SaveStates.validate(checkpoint.get());
            SaveStates.writeAtomic(state, checkpoint.get());
        }
        try (WorkerProcess resumed =
                new WorkerProcess(jar, core, rom, state, root.resolve("resumed.log"))) {
            assertArrayEquals(expectedNext, resumed.advance(0).frame().rgb());
        }
    }

    @Test
    void changingCheckpointIntervalAppliesToAnActiveEmulator() throws Exception {
        String configured = System.getProperty("retrocraft.testCore");
        assumeTrue(configured != null);
        Path rom = Files.write(root.resolve("homebrew.nes"), HomebrewRom.bytes());
        CountDownLatch prepared = new CountDownLatch(1),
                frame = new CountDownLatch(1),
                checkpoint = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ClientEmulator client =
                new ClientEmulator(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(configured),
                        rom,
                        root.resolve("live/snapshot.bin"),
                        30,
                        port -> 0,
                        new ClientEmulator.Listener() {
                            public void prepared() {
                                prepared.countDown();
                            }

                            public void output(WorkerProcess.Output output, int rate) {
                                frame.countDown();
                            }

                            public void checkpoint(byte[] bytes, boolean last) {
                                try {
                                    SaveStates.validate(bytes);
                                } catch (Exception ex) {
                                    failure.set(ex);
                                }
                                checkpoint.countDown();
                            }

                            public void failed(Throwable ex) {
                                failure.set(ex);
                                prepared.countDown();
                                checkpoint.countDown();
                            }
                        })) {
            assertTrue(prepared.await(10, TimeUnit.SECONDS));
            assertNull(failure.get());
            client.commit();
            assertTrue(frame.await(3, TimeUnit.SECONDS));
            client.checkpointInterval(1);
            assertTrue(
                    checkpoint.await(3, TimeUnit.SECONDS),
                    "Updated interval must not wait for the original 30-second deadline");
            assertNull(failure.get());
        }
    }
}
