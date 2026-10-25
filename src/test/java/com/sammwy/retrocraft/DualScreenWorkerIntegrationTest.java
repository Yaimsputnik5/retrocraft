package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.cores.WorkerProcess;
import com.sammwy.retrocraft.cores.stream.DualScreen;
import com.sammwy.retrocraft.cores.worker.PadInput;

/** Optional native smoke: point the two environment variables at a core and homebrew ROM. */
class DualScreenWorkerIntegrationTest {
    @TempDir Path root;

    @Test
    void desmumeOutputsBothScreensAndAcceptsStylusInput() throws Exception {
        verify("NDS", DualScreen.NDS);
    }

    @Test
    void citraRendersThroughTheWorkersPrivateOpenGlContext() throws Exception {
        verify("3DS", DualScreen.THREEDS);
    }

    private void verify(String name, DualScreen system) throws Exception {
        String core = System.getenv("RETROCRAFT_TEST_" + name + "_CORE"),
                rom = System.getenv("RETROCRAFT_TEST_" + name + "_ROM");
        assumeTrue(core != null && rom != null, "Set RETROCRAFT_TEST_" + name + "_CORE and _ROM");
        try (var worker =
                new WorkerProcess(
                        Path.of(System.getProperty("retrocraft.workerJar")),
                        Path.of(core),
                        Path.of(rom),
                        root.resolve(name + "/snapshot.bin"),
                        root.resolve(name + "/bios"),
                        root.resolve(name + ".log"))) {
            assertTrue(worker.fps() > 49);
            WorkerProcess.Output output = null;
            for (int i = 0; i < 180; i++) output = worker.advance(0);
            assertNotNull(output.frame());
            assertEquals(system.width, output.frame().width());
            assertEquals(system.screenHeight * 2, output.frame().height());
            assertFalse(
                    Arrays.equals(new byte[output.frame().rgb().length], output.frame().rgb()),
                    "Homebrew must produce visible pixels, including on a headless host");
            for (double position : new double[] {.1, .5, .9}) {
                var touch =
                        PadInput.IDLE.withTouch(
                                system.pointerX(position), system.pointerY(position), true);
                assertNotNull(worker.advance(new PadInput[] {touch}, true, false).frame());
            }
            assertNotNull(worker.advance(new PadInput[] {PadInput.IDLE}, true, false).frame());
        }
    }
}
