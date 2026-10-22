package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.server.net.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.io.IOException;
import java.util.*;

/** Decodes every console video stream the server sends us, whether or not a play screen is open, into {@link ScreenTextures}. */
final class VideoStreams {
    private static final class Stream { final FrameCodec.Decoder decoder = new FrameCodec.Decoder(); long lastSeen, lastResync, acknowledged = -1; }
    private final Map<UUID, Stream> streams = new HashMap<>();

    void accept(Packets.Video packet) {
        Stream stream = streams.computeIfAbsent(packet.instance(), ignored -> new Stream());
        stream.lastSeen = System.nanoTime();
        try {
            FrameCodec.Frame frame = stream.decoder.decode(packet.frame());
            if (frame == null) resync(packet.instance(), stream); else ScreenTextures.upload(packet.instance(), frame);
        } catch (IOException | IllegalArgumentException ex) {
            Retrocraft.LOG.warn("Invalid console frame: {}", ex.toString());
            resync(packet.instance(), stream);
        }
    }

    /** Frames rendered by this client's own hybrid worker. */
    void local(UUID instance, FrameCodec.Frame frame) {
        streams.computeIfAbsent(instance, ignored -> new Stream()).lastSeen = System.nanoTime();
        ScreenTextures.upload(instance, frame);
    }

    long sequence(UUID instance) { Stream stream = streams.get(instance); return stream == null ? 0 : Math.max(0, stream.decoder.sequence()); }

    private void resync(UUID instance, Stream stream) {
        if (System.nanoTime() - stream.lastResync < 1_000_000_000L || !ClientPlayNetworking.canSend(Packets.Request.ID)) return;
        ClientPlayNetworking.send(Packets.Request.of(Packets.RESYNC, instance)); stream.lastResync = System.nanoTime();
    }

    void tick() {
        long now = System.nanoTime();
        for (UUID id : List.copyOf(streams.keySet())) {
            Stream stream = streams.get(id);
            if (now - stream.lastSeen > 2_000_000_000L) { stop(id); continue; }
            if (ClientPlayNetworking.canSend(Packets.Request.ID)) ClientPlayNetworking.send(new Packets.Request(Packets.VIDEO_ACK, id, 0, sequence(id)));
        }
    }

    void stop(UUID id) { if (streams.remove(id) != null) ScreenTextures.release(id); }
    void close() { for (UUID id : List.copyOf(streams.keySet())) stop(id); }
}
