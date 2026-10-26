package com.sammwy.retrocraft.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LinkInvitationsTest {
    @Test
    void cancellingRequiresTheInvitingPlayerAndRemovesTheOfferForBothConsoles() {
        var requests = new LinkInvitations();
        UUID source = UUID.randomUUID(),
                target = UUID.randomUUID(),
                sender = UUID.randomUUID(),
                recipient = UUID.randomUUID();
        var offer = requests.invite(source, target, sender, recipient, 1);
        assertNull(requests.cancel(offer.token(), source, recipient));
        assertNull(requests.cancel(offer.token(), target, sender));
        assertTrue(requests.busy(target));
        assertEquals(offer, requests.cancel(offer.token(), source, sender));
        assertFalse(requests.busy(source));
        assertFalse(requests.busy(target));
        assertNull(requests.reply(offer.token(), target, recipient, 2));
    }

    @Test
    void onlyIntendedRecipientCanConsumeAnOfferAndRepliesCannotBeReplayed() {
        var requests = new LinkInvitations();
        UUID a = UUID.randomUUID(),
                b = UUID.randomUUID(),
                sender = UUID.randomUUID(),
                recipient = UUID.randomUUID();
        var offer = requests.invite(a, b, sender, recipient, 1);
        assertNotNull(offer);
        assertNull(requests.reply(offer.token(), b, sender, 2));
        assertNull(requests.reply(offer.token(), a, recipient, 2));
        assertTrue(requests.busy(a));
        assertEquals(offer, requests.reply(offer.token(), b, recipient, 2));
        assertNull(requests.reply(offer.token(), b, recipient, 3));
        assertFalse(requests.busy(a));
        assertFalse(requests.busy(b));
    }

    @Test
    void expiredAndOverlappingOffersCannotConnectConsoles() {
        var requests = new LinkInvitations();
        UUID a = UUID.randomUUID(),
                b = UUID.randomUUID(),
                c = UUID.randomUUID(),
                sender = UUID.randomUUID(),
                recipient = UUID.randomUUID();
        var offer = requests.invite(a, b, sender, recipient, 1);
        assertNull(requests.invite(c, b, sender, recipient, 2));
        assertNull(requests.invite(b, c, recipient, sender, 2));
        assertNull(requests.reply(offer.token(), b, recipient, 1 + TimeUnit.SECONDS.toNanos(30)));
        assertFalse(requests.busy(a));
        assertNull(requests.invite(a, a, sender, recipient, 2));
        assertNull(requests.invite(a, b, sender, sender, 2));
    }
}
