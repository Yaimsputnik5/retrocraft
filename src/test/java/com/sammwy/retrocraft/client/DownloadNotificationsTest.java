package com.sammwy.retrocraft.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sammwy.retrocraft.cores.transfer.Downloads;

class DownloadNotificationsTest {
    private Downloads.Entry entry(int id, Downloads.State state) {
        return new Downloads.Entry(new UUID(0, id), "ROM", "game", 50, 100, state, 0);
    }

    @Test
    void removedDownloadsStayVisibleUntilTheyHaveSlidOut() {
        var queue = new DownloadNotifications();
        var entry = entry(1, Downloads.State.DOWNLOADING);
        queue.update(List.of(entry), 1, 0);
        queue.update(List.of(entry), 1, 300_000_000L);
        var leaving = queue.update(List.of(), 1, 400_000_000L);
        assertEquals(1, leaving.size());
        assertFalse(leaving.getFirst().motion.shown());
        assertEquals(1, leaving.getFirst().motion.value(400_000_000L));
        assertTrue(queue.update(List.of(), 1, 600_000_000L).isEmpty());
    }

    @Test
    void progressAndCompletionUpdateTheSameCardDespiteSnapshotReordering() {
        var queue = new DownloadNotifications();
        var first = entry(1, Downloads.State.DOWNLOADING);
        var second = entry(2, Downloads.State.DOWNLOADING);
        var card = queue.update(List.of(first, second), 1, 0).getFirst();
        var complete = entry(1, Downloads.State.COMPLETE);
        var updated = queue.update(List.of(second, complete), 1, 300_000_000L).getFirst();
        assertSame(card, updated);
        assertEquals(Downloads.State.COMPLETE, updated.entry.state());
        assertTrue(updated.motion.shown());
    }

    @Test
    void queuedCardsWaitForExitAndRotationSurvivesChangingTheVisibleLimit() {
        var queue = new DownloadNotifications();
        var entries =
                List.of(
                        entry(1, Downloads.State.DOWNLOADING),
                        entry(2, Downloads.State.DOWNLOADING),
                        entry(3, Downloads.State.DOWNLOADING));
        queue.update(entries, 2, 0);
        queue.update(entries, 2, 300_000_000L);
        var leaving = queue.update(entries, 2, 4_000_000_000L);
        assertEquals(2, leaving.size());
        assertTrue(leaving.stream().noneMatch(card -> card.motion.shown()));
        var next = queue.update(entries, 1, 4_200_000_000L);
        assertEquals(1, next.size());
        assertEquals(entries.get(2).id(), next.getFirst().entry.id());
        assertEquals(0, next.getFirst().motion.value(4_200_000_000L));
    }
}
