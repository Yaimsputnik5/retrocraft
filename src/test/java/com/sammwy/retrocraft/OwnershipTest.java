package com.sammwy.retrocraft;

import com.sammwy.retrocraft.cores.ownership.Ownership;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OwnershipTest {
    @Test void prepareBarrierNeverGrantsBothSidesPermissionToAdvance() {
        var state = new Ownership(); UUID player = UUID.randomUUID();
        assertTrue(state.serverMayRun());
        long epoch = state.offer(player, 0, 100);
        assertFalse(state.serverMayRun()); assertFalse(state.clientMayRun(player, epoch));
        assertTrue(state.commit(player, epoch, 50, 100));
        assertFalse(state.serverMayRun()); assertTrue(state.clientMayRun(player, epoch));
        assertTrue(state.beginReturn(player, epoch, 60, 100));
        assertFalse(state.serverMayRun()); assertFalse(state.clientMayRun(player, epoch));
        assertTrue(state.checkpoint(player, epoch, 1));
        state.revoke(); assertTrue(state.serverMayRun());
    }
    @Test void staleEpochsWrongOwnersAndReplayedSnapshotsAreRejected() {
        var state = new Ownership(); UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        long old = state.offer(one, 0, 100);
        assertFalse(state.commit(two, old, 1, 100));
        assertTrue(state.commit(one, old, 1, 100));
        assertTrue(state.checkpoint(one, old, 2));
        assertFalse(state.checkpoint(one, old, 2)); assertFalse(state.checkpoint(one, old, 1));
        state.revoke(); long epoch = state.offer(two, 2, 100);
        assertTrue(epoch > old);
        assertFalse(state.commit(one, old, 3, 100)); assertFalse(state.acceptsCheckpoint(one, old, 999));
        assertFalse(state.heartbeat(one, old, 3, 100));
    }
    @Test void timeoutDuringPreparationAndReturnLeavesServerRecoveryAvailable() {
        var state = new Ownership(); UUID player = UUID.randomUUID();
        long epoch = state.offer(player, 0, 10);
        assertTrue(state.expired(11)); assertFalse(state.commit(player, epoch, 11, 10));
        state.revoke(); epoch = state.offer(player, 20, 10); state.commit(player, epoch, 21, 10);
        assertTrue(state.heartbeat(player, epoch, 29, 10)); assertFalse(state.expired(38));
        state.beginReturn(player, epoch, 30, 20);
        assertFalse(state.heartbeat(player, epoch, 49, 1000)); assertTrue(state.expired(51));
        state.revoke(); assertTrue(state.serverMayRun()); assertFalse(state.acceptsCheckpoint(player, epoch, 1));
    }
}
