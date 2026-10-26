package com.sammwy.retrocraft.client.media;

import java.io.IOException;
import java.util.*;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.client.render.ScreenTextures;
import com.sammwy.retrocraft.client.ui.ConsoleScreen;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.server.net.Packets;

import net.minecraft.client.Minecraft;

/** Ordered delta decoding off-thread; only the latest picture is uploaded by the render thread. */
public final class VideoStreams {
    private static final class Stream {
        final FrameCodec.Decoder decoder = new FrameCodec.Decoder();
        final ArrayDeque<Packets.Video> pending = new ArrayDeque<>();
        volatile long sequence;
        volatile FrameCodec.Frame decoded;
        volatile boolean needsResync;
        long lastSeen, lastResync, acknowledged, lastAckAt, lastUpload;
        boolean decoding, closed, local;
        FrameCodec.Frame uploaded;
    }

    private final Map<UUID, Stream> streams = new HashMap<>();

    private boolean visible(UUID id) {
        return ClientPreferences.get().renderWorldScreens
                || Minecraft.getInstance().screen instanceof ConsoleScreen screen
                        && screen.instance.equals(id);
    }

    public void accept(Packets.Video packet) {
        if (!visible(packet.instance())) return;
        Stream stream = streams.computeIfAbsent(packet.instance(), ignored -> new Stream());
        stream.local = false;
        stream.lastSeen = System.nanoTime();
        synchronized (stream) {
            if (stream.pending.size() >= 4) {
                stream.pending.clear();
                if (!packet.frame().keyframe()) {
                    stream.needsResync = true;
                    return;
                }
            }
            stream.pending.addLast(packet);
            if (stream.decoding) return;
            stream.decoding = true;
        }
        Thread.ofVirtual().name("retrocraft-video-decode").start(() -> decode(stream));
    }

    private void decode(Stream stream) {
        while (true) {
            Packets.Video packet;
            synchronized (stream) {
                packet = stream.closed ? null : stream.pending.pollFirst();
                if (packet == null) {
                    stream.decoding = false;
                    return;
                }
            }
            try {
                FrameCodec.Frame frame = stream.decoder.decode(packet.frame());
                if (frame != null) {
                    stream.decoded = frame;
                    stream.sequence = Math.max(0, stream.decoder.sequence());
                } else if (packet.frame().sequence() > stream.decoder.sequence())
                    stream.needsResync = true;
            } catch (IOException | IllegalArgumentException ex) {
                Retrocraft.LOG.warn("Invalid console frame: {}", ex.toString());
                stream.needsResync = true;
            }
        }
    }

    public void local(UUID instance, FrameCodec.Frame frame) {
        if (!visible(instance)) return;
        Stream stream = streams.computeIfAbsent(instance, ignored -> new Stream());
        stream.lastSeen = System.nanoTime();
        stream.local = true;
        stream.decoded = frame;
    }

    /**
     * Coalescing GPU uploads bounds render-thread work even when decoding catches up after a stall.
     */
    public void render() {
        long now = System.nanoTime();
        long period = 1_000_000_000L / ClientPreferences.get().maxVideoFps;
        for (var entry : streams.entrySet()) {
            Stream stream = entry.getValue();
            FrameCodec.Frame frame = stream.decoded;
            if (frame == null
                    || frame == stream.uploaded
                    || now - stream.lastUpload < period
                    || !visible(entry.getKey())) continue;
            ScreenTextures.upload(entry.getKey(), frame);
            stream.uploaded = frame;
            stream.lastUpload = now;
        }
    }

    public long sequence(UUID instance) {
        Stream stream = streams.get(instance);
        return stream == null ? 0 : stream.sequence;
    }

    private void resync(UUID instance, Stream stream) {
        if (System.nanoTime() - stream.lastResync < 1_000_000_000L
                || !ClientNetwork.canSend(Packets.Request.ID)) return;
        stream.needsResync = false;
        ClientNetwork.send(Packets.Request.of(Packets.RESYNC, instance));
        stream.lastResync = System.nanoTime();
    }

    public void preferencesChanged() {
        for (UUID id : List.copyOf(streams.keySet())) if (!visible(id)) stop(id);
        if (!ClientPreferences.get().renderWorldScreens) {
            ScreenTextures.clear();
            for (Stream stream : streams.values()) stream.uploaded = null;
        }
    }

    public void tick() {
        long now = System.nanoTime();
        for (UUID id : List.copyOf(streams.keySet())) {
            Stream stream = streams.get(id);
            if (!visible(id)) {
                stop(id);
                continue;
            }
            if (!stream.local && (stream.needsResync || now - stream.lastSeen > 1_000_000_000L))
                resync(id, stream);
            if (now - stream.lastSeen > 2_000_000_000L) {
                stop(id);
                continue;
            }
            if (stream.local || !ClientNetwork.canSend(Packets.Request.ID)) continue;
            // An open play screen already acknowledges its stream in the input heartbeat.
            if (Minecraft.getInstance().screen instanceof ConsoleScreen screen
                    && screen.instance.equals(id)) continue;
            if (stream.sequence != stream.acknowledged || now - stream.lastAckAt >= 500_000_000L) {
                ClientNetwork.send(new Packets.Request(Packets.VIDEO_ACK, id, 0, stream.sequence));
                stream.acknowledged = stream.sequence;
                stream.lastAckAt = now;
            }
        }
    }

    void stop(UUID id) {
        Stream stream = streams.remove(id);
        if (stream != null) {
            synchronized (stream) {
                stream.closed = true;
                stream.pending.clear();
            }
            ScreenTextures.freeze(id);
        }
    }

    public void close() {
        for (UUID id : List.copyOf(streams.keySet())) stop(id);
        ScreenTextures.clear();
    }
}
