package com.sammwy.retrocraft.client.media;

import java.util.*;

import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.server.net.Packets;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/** A nearby listener can hear several consoles without opening any video screen. */
public final class AudioStreams implements AutoCloseable {
    private static final class Stream {
        final AudioPlayer player = new AudioPlayer();
        long lastSeen, sequence, acknowledged;
    }

    private final Map<UUID, Stream> streams = new HashMap<>();

    public void accept(Minecraft client, Packets.Audio packet) {
        if (ClientPreferences.get().volume == 0) return;
        if (!Float.isFinite(packet.gain()) || packet.gain() < 0 || packet.gain() > 1) return;
        Stream stream = streams.computeIfAbsent(packet.instance(), ignored -> new Stream());
        if (packet.sequence() <= stream.sequence) return;
        stream.lastSeen = System.nanoTime();
        stream.sequence = packet.sequence();
        float volume =
                ClientPreferences.get().volume
                        / 100f
                        * packet.gain()
                        * client.options.getSoundSourceVolume(SoundSource.MASTER)
                        * client.options.getSoundSourceVolume(SoundSource.RECORDS);
        stream.player.offer(packet.sampleRate(), packet.pcm(), volume);
    }

    public void tick() {
        long now = System.nanoTime();
        for (UUID id : List.copyOf(streams.keySet())) {
            Stream stream = streams.get(id);
            if (now - stream.lastSeen > 2_000_000_000L) {
                stop(id);
                continue;
            }
            if (stream.sequence > stream.acknowledged
                    && ClientNetwork.canSend(Packets.Request.ID)) {
                ClientNetwork.send(new Packets.Request(Packets.AUDIO_ACK, id, 0, stream.sequence));
                stream.acknowledged = stream.sequence;
            }
        }
    }

    public void stop(UUID id) {
        Stream stream = streams.remove(id);
        if (stream != null) stream.player.close();
    }

    @Override
    public void close() {
        for (UUID id : List.copyOf(streams.keySet())) stop(id);
    }
}
