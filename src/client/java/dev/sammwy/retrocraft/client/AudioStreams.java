package dev.sammwy.retrocraft.client;

import dev.sammwy.retrocraft.net.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import java.util.*;

/** A nearby listener can hear several consoles without opening any video screen. */
final class AudioStreams implements AutoCloseable {
    private static final class Stream {
        final AudioPlayer player = new AudioPlayer();
        long lastSeen, sequence, acknowledged;
    }
    private final Map<UUID, Stream> streams = new HashMap<>();
    void accept(MinecraftClient client, Packets.Audio packet) {
        if (!Float.isFinite(packet.gain()) || packet.gain() < 0 || packet.gain() > 1) return;
        Stream stream = streams.computeIfAbsent(packet.instance(), ignored -> new Stream());
        if (packet.sequence() <= stream.sequence) return;
        stream.lastSeen = System.nanoTime(); stream.sequence = packet.sequence();
        float volume = packet.gain() * client.options.getSoundVolume(SoundCategory.MASTER) * client.options.getSoundVolume(SoundCategory.RECORDS);
        stream.player.offer(packet.sampleRate(), packet.pcm(), volume);
    }
    void tick() {
        long now = System.nanoTime();
        for (UUID id : List.copyOf(streams.keySet())) {
            Stream stream = streams.get(id);
            if (now - stream.lastSeen > 2_000_000_000L) { stop(id); continue; }
            if (stream.sequence > stream.acknowledged && ClientPlayNetworking.canSend(Packets.Request.ID)) {
                ClientPlayNetworking.send(new Packets.Request(Packets.AUDIO_ACK, id, 0, stream.sequence));
                stream.acknowledged = stream.sequence;
            }
        }
    }
    void stop(UUID id) { Stream stream = streams.remove(id); if (stream != null) stream.player.close(); }
    @Override public void close() { for (UUID id : List.copyOf(streams.keySet())) stop(id); }
}
