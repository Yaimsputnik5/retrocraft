package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.server.net.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import java.io.IOException;
import java.util.*;

/** Decodes every console video stream the server sends us, whether or not a play screen is open, into {@link ScreenTextures}. */
final class VideoStreams {
    private static final class Stream {
        final FrameCodec.Decoder decoder = new FrameCodec.Decoder();
        final ArrayDeque<Packets.Video> pending = new ArrayDeque<>();
        volatile long sequence;
        long lastSeen, lastResync;
        boolean decoding;
        FrameCodec.Frame lastLocal;
    }
    private final Map<UUID, Stream> streams = new HashMap<>();

    void accept(Packets.Video packet) {
        Stream stream = streams.computeIfAbsent(packet.instance(), ignored -> new Stream());
        stream.lastSeen = System.nanoTime();
        synchronized (stream) {
            if (stream.pending.size() >= 4) { stream.pending.clear(); resync(packet.instance(), stream); return; }
            stream.pending.addLast(packet);
            if (stream.decoding) return;
            stream.decoding = true;
        }
        Thread.ofVirtual().name("retrocraft-video-decode").start(() -> decode(packet.instance(), stream));
    }

    private void decode(UUID id, Stream stream) {
        while (true) {
            Packets.Video packet;
            synchronized (stream) {
                packet = stream.pending.pollFirst();
                if (packet == null) { stream.decoding = false; return; }
            }
            try {
                FrameCodec.Frame frame = stream.decoder.decode(packet.frame());
                stream.sequence = Math.max(0, stream.decoder.sequence());
                MinecraftClient.getInstance().execute(() -> {
                    if (streams.get(id) != stream) return;
                    if (frame == null) resync(id, stream); else ScreenTextures.upload(id, frame);
                });
            } catch (IOException | IllegalArgumentException ex) {
                Retrocraft.LOG.warn("Invalid console frame: {}", ex.toString());
                MinecraftClient.getInstance().execute(() -> { if (streams.get(id) == stream) resync(id, stream); });
            }
        }
    }

    /** Frames rendered by this client's own hybrid worker. */
    void local(UUID instance, FrameCodec.Frame frame) {
        Stream stream = streams.computeIfAbsent(instance, ignored -> new Stream());
        stream.lastSeen = System.nanoTime();
        if (stream.lastLocal == frame) return;
        stream.lastLocal = frame;
        ScreenTextures.upload(instance, frame);
    }

    long sequence(UUID instance) { Stream stream = streams.get(instance); return stream == null ? 0 : stream.sequence; }

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

    void stop(UUID id) {
        Stream stream = streams.remove(id);
        if (stream != null) { synchronized (stream) { stream.pending.clear(); } ScreenTextures.freeze(id); }
    }
    void close() { for (UUID id : List.copyOf(streams.keySet())) { streams.remove(id); ScreenTextures.release(id); } }
}
