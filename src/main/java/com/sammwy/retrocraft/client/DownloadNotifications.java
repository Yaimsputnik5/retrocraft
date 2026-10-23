package com.sammwy.retrocraft.client;

import java.util.*;

import com.sammwy.retrocraft.cores.transfer.Downloads;

/** Stable notification slots: an outgoing card leaves before its replacement enters. */
final class DownloadNotifications {
    private static final long ROTATE_NANOS = 4_000_000_000L;
    private final LinkedHashMap<UUID, Downloads.Entry> pending = new LinkedHashMap<>();
    private final List<Card> cards = new ArrayList<>();
    private long rotatedAt = -1;

    static final class Card {
        Downloads.Entry entry;
        final int slot;
        final UiMotion motion = new UiMotion();

        Card(Downloads.Entry entry, int slot) {
            this.entry = entry;
            this.slot = slot;
        }
    }

    List<Card> update(List<Downloads.Entry> entries, int limit, long now) {
        Set<UUID> live = new HashSet<>();
        for (var entry : entries) {
            live.add(entry.id());
            pending.put(entry.id(), entry);
        }
        pending.keySet().retainAll(live);
        if (rotatedAt < 0 || pending.size() <= limit) rotatedAt = now;
        if (now - rotatedAt >= ROTATE_NANOS) {
            var rotate = pending.values().stream().limit(limit).toList();
            for (var entry : rotate) {
                pending.remove(entry.id());
                pending.put(entry.id(), entry);
            }
            rotatedAt = now;
        }
        var desired = pending.values().stream().limit(limit).toList();
        for (var card : cards) {
            var fresh = pending.get(card.entry.id());
            if (fresh != null) card.entry = fresh;
            boolean keep =
                    card.slot < desired.size()
                            && desired.get(card.slot).id().equals(card.entry.id());
            // Once leaving, finish before another card claims the slot.
            if (!keep) card.motion.show(false, now);
            card.motion.value(now);
        }
        cards.removeIf(card -> !card.motion.shown() && card.motion.settled(now));
        for (int i = 0; i < desired.size(); i++) {
            int slot = i;
            var entry = desired.get(i);
            if (cards.stream()
                    .noneMatch(card -> card.slot == slot || card.entry.id().equals(entry.id()))) {
                var card = new Card(entry, slot);
                card.motion.value(now);
                cards.add(card);
            }
        }
        return List.copyOf(cards);
    }
}
