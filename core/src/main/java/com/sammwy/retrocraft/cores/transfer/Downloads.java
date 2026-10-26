package com.sammwy.retrocraft.cores.transfer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Independent client/server progress stores; producers can update them from IO threads. */
public final class Downloads {
    public enum State {
        DOWNLOADING,
        COMPLETE,
        FAILED
    }

    public record Entry(
            UUID id,
            String kind,
            String name,
            long received,
            long total,
            State state,
            long updated) {
        public int percent() {
            return total > 0 ? (int) Math.min(100, received * 100 / total) : -1;
        }

        public String size() {
            return bytes(received) + (total > 0 ? " / " + bytes(total) : "");
        }
    }

    public final class Transfer {
        private final UUID id;

        private Transfer(UUID id) {
            this.id = id;
        }

        public void update(long received, long total) {
            entries.computeIfPresent(
                    id,
                    (key, old) ->
                            old.state() != State.DOWNLOADING
                                    ? old
                                    : new Entry(
                                            id,
                                            old.kind(),
                                            old.name(),
                                            Math.max(0, received),
                                            total,
                                            State.DOWNLOADING,
                                            System.nanoTime()));
        }

        public void complete() {
            finish(State.COMPLETE);
        }

        public void fail() {
            finish(State.FAILED);
        }

        public void cancel() {
            entries.remove(id);
        }

        private void finish(State state) {
            entries.computeIfPresent(
                    id,
                    (key, old) ->
                            new Entry(
                                    id,
                                    old.kind(),
                                    old.name(),
                                    old.received(),
                                    old.total(),
                                    state,
                                    System.nanoTime()));
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public Transfer begin(String kind, String name, long total) {
        UUID id = UUID.randomUUID();
        entries.put(id, new Entry(id, kind, name, 0, total, State.DOWNLOADING, System.nanoTime()));
        return new Transfer(id);
    }

    public List<Entry> entries() {
        long now = System.nanoTime();
        entries.values()
                .removeIf(
                        e -> e.state() != State.DOWNLOADING && now - e.updated() > 3_000_000_000L);
        return entries.values().stream()
                .sorted(
                        Comparator.comparing((Entry e) -> e.state() != State.DOWNLOADING)
                                .thenComparing(Entry::id))
                .toList();
    }

    public void clear() {
        entries.clear();
    }

    private static String bytes(long value) {
        if (value < 1024) return value + " B";
        if (value < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", value / 1024d);
        return String.format(Locale.ROOT, "%.1f MiB", value / (1024d * 1024));
    }
}
