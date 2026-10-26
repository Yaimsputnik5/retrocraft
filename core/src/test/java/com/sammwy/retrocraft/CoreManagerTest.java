package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.cores.*;

class CoreManagerTest {
    @TempDir Path root;

    @Test
    void networkCannotTurnAnArbitraryIdentifierIntoADownloadUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> KnownCores.uri("https://example.com/evil.so", KnownCores.Platform.current()));
        assertThrows(IllegalArgumentException.class, () -> KnownCores.get("../evil"));
        assertEquals("https", KnownCores.uri("fceumm", KnownCores.Platform.current()).getScheme());
        assertEquals(
                "buildbot.libretro.com",
                KnownCores.uri("fceumm", KnownCores.Platform.current()).getHost());
    }

    @Test
    void samePlatformMustMatchExactBuildAndCrossPlatformMustMatchNativeVersion() {
        var one = new CoreIdentity("fceumm", "FCEUmm", "v1", "linux-x86_64", "a");
        assertTrue(one.compatible(one));
        assertFalse(
                one.compatible(new CoreIdentity("fceumm", "FCEUmm", "v1", "linux-x86_64", "b")));
        assertTrue(
                one.compatible(new CoreIdentity("fceumm", "FCEUmm", "v1", "linux-aarch64", "b")));
        assertFalse(
                one.compatible(new CoreIdentity("fceumm", "FCEUmm", "v2", "linux-aarch64", "b")));
    }

    @Test
    void officialDownloadIsCachedProbedAndRejectsLocalCorruption() throws Exception {
        assumeTrue("true".equals(System.getProperty("retrocraft.testDownload")));
        var manager =
                new CoreManager(
                        root.resolve("cores"), Path.of(System.getProperty("retrocraft.workerJar")));
        var core = manager.prepare("fceumm");
        assertTrue(Files.size(core.path()) > 100_000);
        assertFalse(core.identity().version().isEmpty());
        for (String id : KnownCores.ids()) {
            var available = manager.prepare(id);
            assertEquals(id, available.identity().id());
            assertFalse(available.identity().name().isBlank());
            System.out.println(
                    "Verified official core "
                            + id
                            + ": "
                            + available.identity().name()
                            + " "
                            + available.identity().version());
        }
        assertEquals(core.identity(), manager.prepare("fceumm").identity());
        Files.write(core.path(), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> manager.prepare("fceumm"));
    }
}
