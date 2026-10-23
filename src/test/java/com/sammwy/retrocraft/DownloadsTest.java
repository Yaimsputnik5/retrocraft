package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sammwy.retrocraft.cores.transfer.Downloads;

class DownloadsTest {
    @Test
    void unknownLengthsAndSimultaneousDownloadsHaveIndependentProgress() {
        var downloads = new Downloads();
        var core = downloads.begin("Core", "fceumm", -1);
        var rom = downloads.begin("ROM", "game.nes", 4096);
        core.update(1024, -1);
        rom.update(2048, 4096);
        var entries = downloads.entries();
        var coreEntry =
                entries.stream().filter(e -> e.kind().equals("Core")).findFirst().orElseThrow();
        assertEquals(-1, coreEntry.percent());
        assertEquals("1.0 KiB", coreEntry.size());
        var romEntry =
                entries.stream().filter(e -> e.kind().equals("ROM")).findFirst().orElseThrow();
        assertEquals(50, romEntry.percent());
        assertEquals("2.0 KiB / 4.0 KiB", romEntry.size());
        rom.update(4096, 4096);
        rom.complete();
        core.fail();
        assertTrue(
                downloads.entries().stream()
                        .anyMatch(
                                e -> e.state() == Downloads.State.COMPLETE && e.percent() == 100));
        assertTrue(downloads.entries().stream().anyMatch(e -> e.state() == Downloads.State.FAILED));
    }

    @Test
    void lateIoCallbacksCannotResurrectCancelledOrDisconnectedTransfers() {
        var downloads = new Downloads();
        var rom = downloads.begin("ROM", "cancelled.nes", 4096);
        rom.cancel();
        rom.update(4096, 4096);
        rom.complete();
        assertTrue(downloads.entries().isEmpty());
        var core = downloads.begin("Core", "fceumm", -1);
        downloads.clear();
        core.update(100, 100);
        core.fail();
        assertTrue(downloads.entries().isEmpty());
    }
}
