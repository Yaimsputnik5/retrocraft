package com.sammwy.retrocraft.cores.ownership;

import java.util.UUID;

/** A lease is authoritative only after COMMIT. All methods run on the coordinator thread. */
public final class Ownership {
    public enum Mode { SERVER, PREPARING_CLIENT, CLIENT, RETURNING_SERVER }
    private volatile Mode mode = Mode.SERVER;
    private volatile UUID owner;
    private volatile long epoch;
    private long deadline, lastCheckpoint;
    public Mode mode() { return mode; }
    public UUID owner() { return owner; }
    public long epoch() { return epoch; }
    public long lastCheckpoint() { return lastCheckpoint; }
    public boolean serverMayRun() { return mode == Mode.SERVER; }
    public boolean matches(UUID player, long epoch) { return this.epoch == epoch && player.equals(owner); }
    public boolean clientMayRun(UUID player, long epoch) { return mode == Mode.CLIENT && matches(player, epoch); }
    public long offer(UUID player, long now, long timeout) {
        if (mode != Mode.SERVER) throw new IllegalStateException("Ownership already leased");
        owner = player; mode = Mode.PREPARING_CLIENT; deadline = now + timeout; lastCheckpoint = 0;
        return ++epoch;
    }
    public boolean preparationTime(UUID player, long epoch, long now, long timeout) {
        if (mode != Mode.PREPARING_CLIENT || !matches(player, epoch) || now > deadline) return false;
        deadline = now + timeout; return true;
    }
    public boolean commit(UUID player, long epoch, long now, long timeout) {
        if (mode != Mode.PREPARING_CLIENT || !matches(player, epoch) || now > deadline) return false;
        mode = Mode.CLIENT; deadline = now + timeout; return true;
    }
    public boolean heartbeat(UUID player, long epoch, long now, long timeout) {
        if (!clientMayRun(player, epoch)) return false;
        deadline = now + timeout; return true;
    }
    public boolean beginReturn(UUID player, long epoch, long now, long timeout) {
        if (!clientMayRun(player, epoch)) return false;
        mode = Mode.RETURNING_SERVER; deadline = now + timeout; return true;
    }
    public boolean acceptsCheckpoint(UUID player, long epoch, long sequence) {
        return (mode == Mode.CLIENT || mode == Mode.RETURNING_SERVER) && matches(player, epoch) && sequence > lastCheckpoint;
    }
    public boolean checkpoint(UUID player, long epoch, long sequence) {
        if (!acceptsCheckpoint(player, epoch, sequence)) return false;
        lastCheckpoint = sequence; return true;
    }
    public boolean expired(long now) { return mode != Mode.SERVER && now > deadline; }
    public void revoke() { mode = Mode.SERVER; owner = null; deadline = 0; ++epoch; }
}
