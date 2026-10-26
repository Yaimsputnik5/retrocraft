package com.sammwy.retrocraft.server;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** A console may participate in one pending request; only its intended player can answer. */
public final class LinkInvitations {
    public record Offer(
            UUID token, UUID source, UUID target, UUID sender, UUID recipient, long expires) {}

    private final Map<UUID, Offer> pending = new HashMap<>();

    public Offer invite(UUID source, UUID target, UUID sender, UUID recipient, long now) {
        if (source.equals(target) || sender.equals(recipient) || busy(source) || busy(target))
            return null;
        Offer offer =
                new Offer(
                        UUID.randomUUID(),
                        source,
                        target,
                        sender,
                        recipient,
                        now + TimeUnit.SECONDS.toNanos(30));
        pending.put(offer.token(), offer);
        return offer;
    }

    public boolean busy(UUID instance) {
        return pending.values().stream()
                .anyMatch(o -> o.source().equals(instance) || o.target().equals(instance));
    }

    public Offer reply(UUID token, UUID target, UUID player, long now) {
        Offer offer = pending.get(token);
        if (offer == null || !offer.target().equals(target) || !offer.recipient().equals(player))
            return null;
        pending.remove(token);
        return now < offer.expires() ? offer : null;
    }

    public List<Offer> offers() {
        return List.copyOf(pending.values());
    }

    public Offer cancel(UUID token, UUID source, UUID player) {
        Offer offer = pending.get(token);
        if (offer == null || !offer.source().equals(source) || !offer.sender().equals(player))
            return null;
        pending.remove(token);
        return offer;
    }

    public void remove(Offer offer) {
        pending.remove(offer.token());
    }
}
