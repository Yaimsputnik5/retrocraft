package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.cores.WorkerProcess;
import com.sammwy.retrocraft.roms.SaveStates;

class WorkerIntegrationTest {
    @TempDir Path root;

    @Test
    void realCoreProducesAudioAndVideoAndRestoresAfterProcessExit() throws Exception {
        String configured = System.getProperty("retrocraft.testCore");
        assumeTrue(
                configured != null,
                "Set RETROCRAFT_TEST_CORE to run against a real libretro NES core");
        Path core = Path.of(configured), jar = Path.of(System.getProperty("retrocraft.workerJar"));
        Path rom = Files.write(root.resolve("homebrew.nes"), HomebrewRom.bytes());
        Path state = root.resolve("instance/snapshot.bin");
        List<byte[]> expected = new ArrayList<>();
        byte[] initial;
        int totalAudio = 0;
        boolean nonSilent = false;
        try (WorkerProcess worker =
                new WorkerProcess(jar, core, rom, state, root.resolve("first.log"))) {
            assertTrue(worker.fps() > 49);
            assertTrue(worker.sampleRate() >= 8000);
            initial = worker.advance(0).frame().rgb();
            for (int i = 0; i < 90; i++) {
                byte[] pcm = worker.advance(0).audio();
                totalAudio += pcm.length;
                for (byte sample : pcm) if (sample != 0) nonSilent = true;
            }
            assertTrue(totalAudio > 0);
            assertTrue(nonSilent, "Homebrew pulse must be audible, not just empty PCM samples");
            worker.save();
            assertTrue(Files.size(state) > 1000);
            for (int i = 0; i < 12; i++) expected.add(worker.advance(0).frame().rgb());
            assertTrue(
                    expected.stream().anyMatch(frame -> !Arrays.equals(initial, frame)),
                    "Homebrew must animate");
        }
        try (WorkerProcess restored =
                new WorkerProcess(jar, core, rom, state, root.resolve("second.log"))) {
            for (byte[] frame : expected)
                assertArrayEquals(
                        frame,
                        restored.advance(0).frame().rgb(),
                        "Snapshot must preserve exact next video frames");
        }
    }

    @Test
    void simultaneousProcessesKeepNativeCoreGlobalsIndependent() throws Exception {
        String configured = System.getProperty("retrocraft.testCore");
        assumeTrue(configured != null);
        Path core = Path.of(configured), jar = Path.of(System.getProperty("retrocraft.workerJar"));
        Path rom = Files.write(root.resolve("homebrew.nes"), HomebrewRom.bytes());
        try (WorkerProcess one =
                        new WorkerProcess(
                                jar,
                                core,
                                rom,
                                root.resolve("one/snapshot.bin"),
                                root.resolve("one.log"));
                WorkerProcess two =
                        new WorkerProcess(
                                jar,
                                core,
                                rom,
                                root.resolve("two/snapshot.bin"),
                                root.resolve("two.log"))) {
            for (int i = 0; i < 45; i++)
                assertArrayEquals(one.advance(0).frame().rgb(), two.advance(0).frame().rgb());
            one.save();
            two.save();
            assertArrayEquals(
                    Files.readAllBytes(root.resolve("one/snapshot.bin")),
                    Files.readAllBytes(root.resolve("two/snapshot.bin")));
            for (int i = 0; i < 10; i++) one.advance(0);
            one.save();
            two.save();
            assertFalse(
                    Arrays.equals(
                            Files.readAllBytes(root.resolve("one/snapshot.bin")),
                            Files.readAllBytes(root.resolve("two/snapshot.bin"))));
        }
    }

    @Test
    void audioAndVideoCanBeRequestedIndependently() throws Exception {
        String configured = System.getProperty("retrocraft.testCore");
        assumeTrue(configured != null);
        Path rom = Files.write(root.resolve("homebrew.nes"), HomebrewRom.bytes());
        try (WorkerProcess worker =
                new WorkerProcess(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(configured),
                        rom,
                        root.resolve("instance/snapshot.bin"),
                        root.resolve("worker.log"))) {
            for (int i = 0; i < 60; i++) worker.advance(0);
            var audioOnly = worker.advance(0, false, true);
            assertNull(audioOnly.frame());
            assertTrue(audioOnly.audio().length > 0);
            var videoOnly = worker.advance(0, true, false);
            assertNotNull(videoOnly.frame());
            assertEquals(0, videoOnly.audio().length);
            var neither = worker.advance(0, false, false);
            assertNull(neither.frame());
            assertEquals(0, neither.audio().length);
        }
    }

    @Test
    void n64StateRestoresAcrossWorkerRestarts() throws Exception {
        String configuredCore = System.getProperty("retrocraft.testN64Core");
        String configuredRom = System.getProperty("retrocraft.testN64Rom");
        assumeTrue(configuredCore != null && configuredRom != null);
        Path state = root.resolve("n64/snapshot.bin");
        for (int attempt = 0; attempt < 3; attempt++) {
            try (WorkerProcess worker =
                    new WorkerProcess(
                            Path.of(System.getProperty("retrocraft.workerJar")),
                            Path.of(configuredCore),
                            Path.of(configuredRom),
                            state,
                            root.resolve("n64/worker.log"))) {
                for (int frame = 0; frame < 90; frame++) worker.advance(0, frame % 3 == 0, false);
                worker.save();
                assertTrue(Files.size(state) > 1_000_000);
            }
        }
    }

    @Test
    void n64CanSaveBeforeTheFirstRequestedFrame() throws Exception {
        String configuredCore = System.getProperty("retrocraft.testN64Core");
        String configuredRom = System.getProperty("retrocraft.testN64Rom");
        assumeTrue(configuredCore != null && configuredRom != null);
        Path state = root.resolve("n64-before-frame/snapshot.bin");
        try (WorkerProcess worker =
                new WorkerProcess(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(configuredCore),
                        Path.of(configuredRom),
                        state,
                        root.resolve("n64-before-frame/worker.log"))) {
            worker.save();
            assertTrue(Files.size(state) > 1_000_000);
        }
    }

    @Test
    void n64QuitDoesNotCreateASnapshot() throws Exception {
        String configuredCore = System.getProperty("retrocraft.testN64Core");
        String configuredRom = System.getProperty("retrocraft.testN64Rom");
        assumeTrue(configuredCore != null && configuredRom != null);
        Path state = root.resolve("n64-power-off/snapshot.bin");
        try (WorkerProcess ignored =
                new WorkerProcess(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(configuredCore),
                        Path.of(configuredRom),
                        state,
                        root.resolve("n64-power-off/worker.log"))) {
            assertFalse(Files.exists(state));
        }
        assertFalse(Files.exists(state));
    }

    @Test
    void arcadeWithoutSavestatesKeepsItsWorkerAlive() throws Exception {
        String core = System.getProperty("retrocraft.testMameCore");
        String rom = System.getProperty("retrocraft.testMameRom");
        String bios = System.getProperty("retrocraft.testMameBios");
        assumeTrue(core != null && rom != null && bios != null);
        Path state = root.resolve("arcade/snapshot.bin");
        try (WorkerProcess worker =
                new WorkerProcess(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(core),
                        Path.of(rom),
                        state,
                        Path.of(bios),
                        root.resolve("arcade/worker.log"))) {
            assertFalse(worker.supportsSnapshots());
            for (int i = 0; i < 10; i++) worker.advance(new int[4], false, false);
            assertFalse(Files.exists(state));
        }
    }

    @Test
    void arcadeWithSavestatesCanSaveAndRestoreAtHandoff() throws Exception {
        String core = System.getProperty("retrocraft.testMameCore");
        String rom = System.getProperty("retrocraft.testMameSavableRom");
        String bios = System.getProperty("retrocraft.testMameBios");
        assumeTrue(core != null && rom != null && bios != null);
        Path state = root.resolve("arcade-savable/snapshot.bin");
        Path jar = Path.of(System.getProperty("retrocraft.workerJar"));
        try (WorkerProcess worker =
                new WorkerProcess(
                        jar,
                        Path.of(core),
                        Path.of(rom),
                        state,
                        Path.of(bios),
                        root.resolve("arcade-savable/first.log"))) {
            assertTrue(worker.supportsSnapshots());
            worker.save();
            assertTrue(Files.size(state) > 1000);
        }
        try (WorkerProcess restored =
                new WorkerProcess(
                        jar,
                        Path.of(core),
                        Path.of(rom),
                        state,
                        Path.of(bios),
                        root.resolve("arcade-savable/second.log"))) {
            assertTrue(restored.supportsSnapshots());
            restored.advance(new int[4], false, false);
        }
    }

    @Test
    void batteryOnlySavesKeepRunningWithoutCreatingSnapshots() throws Exception {
        String configured = System.getProperty("retrocraft.testCore");
        assumeTrue(configured != null);
        Path rom = Files.write(root.resolve("homebrew.nes"), HomebrewRom.bytes());
        Path state = root.resolve("battery/snapshot.bin");
        try (WorkerProcess worker =
                new WorkerProcess(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(configured),
                        rom,
                        state,
                        root.resolve("battery.log"))) {
            for (int i = 0; i < 20; i++) worker.advance(0);
            worker.saveBattery();
            assertFalse(Files.exists(state));
            assertNotNull(worker.advance(0).frame());
            worker.save();
            SaveStates.validate(Files.readAllBytes(state));
        }
    }
}
