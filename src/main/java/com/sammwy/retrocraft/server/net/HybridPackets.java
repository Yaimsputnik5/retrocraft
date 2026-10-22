package com.sammwy.retrocraft.server.net;

import com.sammwy.retrocraft.cores.*;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.*;

public final class HybridPackets {
    public static final int DOWNLOADING = 0, READY = 1, FAILED = 2;
    public enum Action { OFFER, PREPARED, COMMIT, RETURN_BEGIN, RETURN_REQUEST, REVOKE, HEARTBEAT, CHECKPOINT_ACK, FAILED, REQUEST_KEYFRAME }
    public record Settings(int generation, String pool, int checkpointSeconds, int streamFps, List<CoreIdentity> cores) implements CustomPayload {
        public static final Id<Settings> ID = new Id<>(Identifier.of("retrocraft", "core_settings"));
        public static final PacketCodec<RegistryByteBuf, Settings> CODEC = new PacketCodec<>() {
            public Settings decode(RegistryByteBuf b) { return new Settings(b.readVarInt(), b.readString(64), b.readVarInt(), b.readVarInt(), identities(b)); }
            public void encode(RegistryByteBuf b, Settings p) { b.writeVarInt(p.generation); b.writeString(p.pool, 64); b.writeVarInt(p.checkpointSeconds); b.writeVarInt(p.streamFps); identities(b, p.cores); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Status(int generation, int phase, List<CoreIdentity> cores) implements CustomPayload {
        public static final Id<Status> ID = new Id<>(Identifier.of("retrocraft", "core_status"));
        public static final PacketCodec<RegistryByteBuf, Status> CODEC = new PacketCodec<>() {
            public Status decode(RegistryByteBuf b) { return new Status(b.readVarInt(), b.readVarInt(), identities(b)); }
            public void encode(RegistryByteBuf b, Status p) { b.writeVarInt(p.generation); b.writeVarInt(p.phase); identities(b, p.cores); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Lease(UUID instance, long epoch, Action action, String core, String gameId, String title, String romName, boolean docked, long sequence) implements CustomPayload {
        public static final Id<Lease> ID = new Id<>(Identifier.of("retrocraft", "ownership"));
        public static final PacketCodec<RegistryByteBuf, Lease> CODEC = new PacketCodec<>() {
            public Lease decode(RegistryByteBuf b) {
                return new Lease(b.readUuid(), b.readVarLong(), enumValue(Action.values(), b.readVarInt()), b.readString(64), b.readString(64), b.readString(200), b.readString(140), b.readBoolean(), b.readVarLong());
            }
            public void encode(RegistryByteBuf b, Lease p) { b.writeUuid(p.instance); b.writeVarLong(p.epoch); b.writeVarInt(p.action.ordinal()); b.writeString(p.core, 64); b.writeString(p.gameId, 64); b.writeString(p.title, 200); b.writeString(p.romName, 140); b.writeBoolean(p.docked); b.writeVarLong(p.sequence); }
        };
        public static Lease signal(UUID id, long epoch, Action action, long sequence) { return new Lease(id, epoch, action, "", "", "", "", false, sequence); }
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Blob(BlobTransfer.Chunk chunk) implements CustomPayload {
        public static final Id<Blob> ID = new Id<>(Identifier.of("retrocraft", "blob"));
        public static final PacketCodec<RegistryByteBuf, Blob> CODEC = new PacketCodec<>() {
            public Blob decode(RegistryByteBuf b) { return new Blob(new BlobTransfer.Chunk(b.readUuid(), b.readVarLong(), enumValue(BlobTransfer.Kind.values(), b.readVarInt()), b.readVarLong(), b.readVarInt(), b.readVarInt(), b.readString(64), b.readByteArray(BlobTransfer.CHUNK))); }
            public void encode(RegistryByteBuf b, Blob p) { var c = p.chunk; b.writeUuid(c.instance()); b.writeVarLong(c.epoch()); b.writeVarInt(c.kind().ordinal()); b.writeVarLong(c.sequence()); b.writeVarInt(c.offset()); b.writeVarInt(c.total()); b.writeString(c.hash(), 64); b.writeByteArray(c.bytes()); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Demand(UUID instance, long epoch, boolean audio, boolean video, float gain) implements CustomPayload {
        public static final Id<Demand> ID = new Id<>(Identifier.of("retrocraft", "relay_demand"));
        public static final PacketCodec<RegistryByteBuf, Demand> CODEC = new PacketCodec<>() {
            public Demand decode(RegistryByteBuf b) { return new Demand(b.readUuid(), b.readVarLong(), b.readBoolean(), b.readBoolean(), b.readFloat()); }
            public void encode(RegistryByteBuf b, Demand p) { b.writeUuid(p.instance); b.writeVarLong(p.epoch); b.writeBoolean(p.audio); b.writeBoolean(p.video); b.writeFloat(p.gain); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    private static <T> T enumValue(T[] values, int index) { if (index < 0 || index >= values.length) throw new IllegalArgumentException("Unknown protocol enum"); return values[index]; }
    private static List<CoreIdentity> identities(RegistryByteBuf b) {
        int count = b.readVarInt(); if (count < 0 || count > 16) throw new IllegalArgumentException("Core pool too large");
        List<CoreIdentity> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new CoreIdentity(b.readString(64), b.readString(128), b.readString(128), b.readString(32), b.readString(64)));
        return List.copyOf(result);
    }
    private static void identities(RegistryByteBuf b, List<CoreIdentity> cores) {
        b.writeVarInt(cores.size()); for (var core : cores) { b.writeString(core.id(), 64); b.writeString(core.name(), 128); b.writeString(core.version(), 128); b.writeString(core.platform(), 32); b.writeString(core.sha256(), 64); }
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Settings.ID, Settings.CODEC);
        PayloadTypeRegistry.playC2S().register(Status.ID, Status.CODEC);
        PayloadTypeRegistry.playS2C().register(Lease.ID, Lease.CODEC);
        PayloadTypeRegistry.playC2S().register(Lease.ID, Lease.CODEC);
        PayloadTypeRegistry.playS2C().register(Blob.ID, Blob.CODEC);
        PayloadTypeRegistry.playC2S().register(Blob.ID, Blob.CODEC);
        PayloadTypeRegistry.playS2C().register(Demand.ID, Demand.CODEC);
    }
    private HybridPackets() {}
}
