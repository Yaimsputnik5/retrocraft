package com.sammwy.retrocraft.server.net;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.roms.RomCatalog;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.*;

public final class Packets {
    public static final int CLOSE = 1, INPUT = 2, POWER_OFF = 3, RESYNC = 4, CLAIM_INPUT = 6, AUDIO_ACK = 7, EJECT = 8, VIDEO_ACK = 9;
    public static final int PLAY = 1, STOP = 2, ROLE = 3;

    public record Request(int action, UUID instance, int buttons, long acknowledgedSequence) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("retrocraft", "request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = new PacketCodec<>() {
            public Request decode(RegistryByteBuf b) { return new Request(b.readVarInt(), b.readUuid(), b.readUnsignedShort(), b.readVarLong()); }
            public void encode(RegistryByteBuf b, Request p) { b.writeVarInt(p.action); b.writeUuid(p.instance); b.writeShort(p.buttons); b.writeVarLong(p.acknowledgedSequence); }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
        public static Request of(int action, UUID id) { return new Request(action, id, 0, 0); }
    }

    /** {@code inWorld}: the picture is shown on a block (arcade cabinet or bound television), so the client draws no overlay. */
    public record Ui(int mode, UUID instance, String console, String title, boolean controller, boolean canManage, boolean inWorld) implements CustomPayload {
        public static final Id<Ui> ID = new Id<>(Identifier.of("retrocraft", "ui"));
        public static final PacketCodec<RegistryByteBuf, Ui> CODEC = new PacketCodec<>() {
            public Ui decode(RegistryByteBuf b) { return new Ui(b.readVarInt(), b.readUuid(), b.readString(64), b.readString(200), b.readBoolean(), b.readBoolean(), b.readBoolean()); }
            public void encode(RegistryByteBuf b, Ui p) { b.writeVarInt(p.mode); b.writeUuid(p.instance); b.writeString(p.console, 64); b.writeString(p.title, 200); b.writeBoolean(p.controller); b.writeBoolean(p.canManage); b.writeBoolean(p.inWorld); }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Video(UUID instance, FrameCodec.Encoded frame) implements CustomPayload {
        public static final Id<Video> ID = new Id<>(Identifier.of("retrocraft", "video"));
        public static final PacketCodec<RegistryByteBuf, Video> CODEC = new PacketCodec<>() {
            public Video decode(RegistryByteBuf b) { return new Video(b.readUuid(), new FrameCodec.Encoded(b.readVarLong(), b.readVarLong(), b.readBoolean(), b.readByteArray(FrameCodec.MAX_PACKET))); }
            public void encode(RegistryByteBuf b, Video p) { b.writeUuid(p.instance); b.writeVarLong(p.frame.sequence()); b.writeVarLong(p.frame.base()); b.writeBoolean(p.frame.keyframe()); b.writeByteArray(p.frame.data()); }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Audio(UUID instance, long sequence, int sampleRate, float gain, byte[] pcm) implements CustomPayload {
        public static final Id<Audio> ID = new Id<>(Identifier.of("retrocraft", "audio"));
        public static final PacketCodec<RegistryByteBuf, Audio> CODEC = new PacketCodec<>() {
            public Audio decode(RegistryByteBuf b) { return new Audio(b.readUuid(), b.readVarLong(), b.readVarInt(), b.readFloat(), b.readByteArray(WorkerProtocol.MAX_AUDIO)); }
            public void encode(RegistryByteBuf b, Audio p) { b.writeUuid(p.instance); b.writeVarLong(p.sequence); b.writeVarInt(p.sampleRate); b.writeFloat(p.gain); b.writeByteArray(p.pcm); }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Catalog(int generation, boolean first, boolean last, List<RomCatalog.Entry> entries) implements CustomPayload {
        public static final Id<Catalog> ID = new Id<>(Identifier.of("retrocraft", "catalog"));
        public static final PacketCodec<RegistryByteBuf, Catalog> CODEC = new PacketCodec<>() {
            public Catalog decode(RegistryByteBuf b) {
                int generation = b.readVarInt(); boolean first = b.readBoolean(), last = b.readBoolean();
                int size = b.readVarInt();
                if (size < 0 || size > 128) throw new IllegalArgumentException("Oversized catalog chunk");
                List<RomCatalog.Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) entries.add(new RomCatalog.Entry(b.readString(200), b.readString(64), b.readString(200)));
                return new Catalog(generation, first, last, List.copyOf(entries));
            }
            public void encode(RegistryByteBuf b, Catalog p) {
                b.writeVarInt(p.generation); b.writeBoolean(p.first); b.writeBoolean(p.last); b.writeVarInt(p.entries.size());
                for (var entry : p.entries) { b.writeString(entry.cartridge(), 200); b.writeString(entry.gameId(), 64); b.writeString(entry.name(), 200); }
            }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AudioStop(UUID instance) implements CustomPayload {
        public static final Id<AudioStop> ID = new Id<>(Identifier.of("retrocraft", "audio_stop"));
        public static final PacketCodec<RegistryByteBuf, AudioStop> CODEC = new PacketCodec<>() {
            public AudioStop decode(RegistryByteBuf b) { return new AudioStop(b.readUuid()); }
            public void encode(RegistryByteBuf b, AudioStop p) { b.writeUuid(p.instance); }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Ui.ID, Ui.CODEC);
        PayloadTypeRegistry.playS2C().register(Video.ID, Video.CODEC);
        PayloadTypeRegistry.playS2C().register(Audio.ID, Audio.CODEC);
        PayloadTypeRegistry.playS2C().register(AudioStop.ID, AudioStop.CODEC);
        PayloadTypeRegistry.playS2C().register(Catalog.ID, Catalog.CODEC);
    }
    private Packets() {}
}
