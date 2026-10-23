package com.sammwy.retrocraft.client;

/** Time-based, reversible motion shared by menus and notification cards. */
final class UiMotion {
    static final long OPEN_NANOS = 220_000_000L, CLOSE_NANOS = 160_000_000L;
    private long started = -1;
    private float from;
    private boolean shown = true;

    float value(long now) {
        if (started < 0) started = now;
        float t = Math.min(1f, Math.max(0f, (float) (now - started) / duration()));
        float eased = shown ? 1f - (float) Math.pow(1f - t, 3) : t * t * t;
        return from + ((shown ? 1f : 0f) - from) * eased;
    }

    void show(boolean next, long now) {
        if (shown == next) return;
        from = value(now);
        started = now;
        shown = next;
    }

    boolean shown() {
        return shown;
    }

    boolean settled(long now) {
        return started >= 0 && now - started >= duration();
    }

    private long duration() {
        return shown ? OPEN_NANOS : CLOSE_NANOS;
    }
}
