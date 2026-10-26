package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.*;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.cores.WorkerProcess;
import com.sammwy.retrocraft.cores.worker.PadInput;

class PspWorkerIntegrationTest {
    @TempDir Path root;

    @Test
    void nativePspVideoControlsMemoryStickAndShutdownWorkTogether() throws Exception {
        String core = System.getenv("RETROCRAFT_TEST_PSP_CORE"),
                rom = System.getenv("RETROCRAFT_TEST_PSP_ROM");
        assumeTrue(
                core != null && rom != null,
                "Set RETROCRAFT_TEST_PSP_CORE and _ROM (with PPSSPP system assets)");
        Path log = root.resolve("worker.log"), saves = root.resolve("durable/game.files");
        try (var worker =
                new WorkerProcess(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(core),
                        Path.of(rom),
                        root.resolve("instance/snapshot.bin"),
                        root.resolve("bios"),
                        log,
                        saves)) {
            WorkerProcess.Output output;
            byte[] blank = new byte[480 * 272 * 3];
            long deadline = System.nanoTime() + 10_000_000_000L;
            // PPSSPP boots asynchronously; an unpaced test can exhaust frames before its loader
            // runs.
            do {
                output = worker.advance(0);
                if (output.frame().width() == 480
                        && output.frame().height() == 272
                        && !Arrays.equals(blank, output.frame().rgb())) break;
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertEquals(480, output.frame().width());
            assertEquals(272, output.frame().height());
            assertFalse(Arrays.equals(new byte[480 * 272 * 3], output.frame().rgb()));
            var held =
                    new PadInput(1 | (1 << 4), (short) 32767, (short) -16000, (short) 0, (short) 0);
            assertNotNull(worker.advance(new PadInput[] {held}, true, false).frame());
            assertNotNull(worker.advance(new PadInput[] {PadInput.IDLE}, true, false).frame());
        }
        assertTrue(Files.isDirectory(saves.resolve("PSP/SAVEDATA")));
        assertFalse(Files.exists(root.resolve("instance/PSP")));
        assertFalse(
                Files.readString(log).contains("A fatal error has been detected"),
                "Native GL objects must be released before their callback owner is unloaded");
    }
}
