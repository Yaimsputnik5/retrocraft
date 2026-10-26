package com.sammwy.retrocraft.server.net;

import java.util.*;

import com.sammwy.retrocraft.cores.*;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class HybridPackets {
    public static final int DOWNLOADING = 0, READY = 1, FAILED = 2;

    public enum Action {
        OFFER,
        ROM_READY,
        ROM_REQUEST,
        PREPARED,
        COMMIT,
        RETURN_BEGIN,
        RETURN_REQUEST,
        REVOKE,
        HEARTBEAT,
        CHECKPOINT_ACK,
        FAILED,
        REQUEST_KEYFRAME
    }

    public record Settings(
            int generation,
            String pool,
            int checkpointSeconds,
            int streamFps,
            int thumbnailSeconds,
            boolean thumbnailUpload,
            int emulationMode,
            List<CoreIdentity> cores)
            implements CustomPacketPayload {
        public static final Type<Settings> ID = new Type<>(Ids.of("retrocraft", "core_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC =
                new StreamCodec<>() {
                    public Settings decode(RegistryFriendlyByteBuf b) {
                        return new Settings(
                                b.readVarInt(),
                                b.readUtf(64),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readBoolean(),
                                b.readVarInt(),
                                identities(b));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Settings p) {
                        b.writeVarInt(p.generation);
                        b.writeUtf(p.pool, 64);
                        b.writeVarInt(p.checkpointSeconds);
                        b.writeVarInt(p.streamFps);
                        b.writeVarInt(p.thumbnailSeconds);
                        b.writeBoolean(p.thumbnailUpload);
                        b.writeVarInt(p.emulationMode);
                        identities(b, p.cores);
                    }
                };

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Status(int generation, int phase, List<CoreIdentity> cores)
            implements CustomPacketPayload {
        public static final Type<Status> ID = new Type<>(Ids.of("retrocraft", "core_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC =
                new StreamCodec<>() {
                    public Status decode(RegistryFriendlyByteBuf b) {
                        return new Status(b.readVarInt(), b.readVarInt(), identities(b));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Status p) {
                        b.writeVarInt(p.generation);
                        b.writeVarInt(p.phase);
                        identities(b, p.cores);
                    }
                };

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Lease(
            UUID instance,
            long epoch,
            Action action,
            String core,
            String gameId,
            String title,
            String romName,
            boolean worldScreen,
            long sequence,
            String hardware)
            implements CustomPacketPayload {
        public static final Type<Lease> ID = new Type<>(Ids.of("retrocraft", "ownership"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Lease> CODEC =
                new StreamCodec<>() {
                    public Lease decode(RegistryFriendlyByteBuf b) {
                        return new Lease(
                                b.readUUID(),
                                b.readVarLong(),
                                enumValue(Action.values(), b.readVarInt()),
                                b.readUtf(64),
                                b.readUtf(64),
                                b.readUtf(200),
                                b.readUtf(140),
                                b.readBoolean(),
                                b.readVarLong(),
                                b.readUtf(HardwareProfile.MAX_ENCODED));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Lease p) {
                        b.writeUUID(p.instance);
                        b.writeVarLong(p.epoch);
                        b.writeVarInt(p.action.ordinal());
                        b.writeUtf(p.core, 64);
                        b.writeUtf(p.gameId, 64);
                        b.writeUtf(p.title, 200);
                        b.writeUtf(p.romName, 140);
                        b.writeBoolean(p.worldScreen);
                        b.writeVarLong(p.sequence);
                        b.writeUtf(p.hardware, HardwareProfile.MAX_ENCODED);
                    }
                };

        public static Lease signal(UUID id, long epoch, Action action, long sequence) {
            return new Lease(id, epoch, action, "", "", "", "", false, sequence, "");
        }

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Blob(BlobTransfer.Chunk chunk) implements CustomPacketPayload {
        public static final Type<Blob> ID = new Type<>(Ids.of("retrocraft", "blob"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Blob> CODEC =
                new StreamCodec<>() {
                    public Blob decode(RegistryFriendlyByteBuf b) {
                        return new Blob(
                                new BlobTransfer.Chunk(
                                        b.readUUID(),
                                        b.readVarLong(),
                                        enumValue(BlobTransfer.Kind.values(), b.readVarInt()),
                                        b.readVarLong(),
                                        b.readVarInt(),
                                        b.readVarInt(),
                                        b.readUtf(64),
                                        b.readByteArray(BlobTransfer.CHUNK)));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Blob p) {
                        var c = p.chunk;
                        b.writeUUID(c.instance());
                        b.writeVarLong(c.epoch());
                        b.writeVarInt(c.kind().ordinal());
                        b.writeVarLong(c.sequence());
                        b.writeVarInt(c.offset());
                        b.writeVarInt(c.total());
                        b.writeUtf(c.hash(), 64);
                        b.writeByteArray(c.bytes());
                    }
                };

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Demand(UUID instance, long epoch, boolean audio, boolean video, float gain)
            implements CustomPacketPayload {
        public static final Type<Demand> ID = new Type<>(Ids.of("retrocraft", "relay_demand"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Demand> CODEC =
                new StreamCodec<>() {
                    public Demand decode(RegistryFriendlyByteBuf b) {
                        return new Demand(
                                b.readUUID(),
                                b.readVarLong(),
                                b.readBoolean(),
                                b.readBoolean(),
                                b.readFloat());
                    }

                    public void encode(RegistryFriendlyByteBuf b, Demand p) {
                        b.writeUUID(p.instance);
                        b.writeVarLong(p.epoch);
                        b.writeBoolean(p.audio);
                        b.writeBoolean(p.video);
                        b.writeFloat(p.gain);
                    }
                };

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private static <T> T enumValue(T[] values, int index) {
        if (index < 0 || index >= values.length)
            throw new IllegalArgumentException("Unknown protocol enum");
        return values[index];
    }

    private static List<CoreIdentity> identities(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 16) throw new IllegalArgumentException("Core pool too large");
        List<CoreIdentity> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            result.add(
                    new CoreIdentity(
                            b.readUtf(64),
                            b.readUtf(128),
                            b.readUtf(128),
                            b.readUtf(32),
                            b.readUtf(64)));
        return List.copyOf(result);
    }

    private static void identities(RegistryFriendlyByteBuf b, List<CoreIdentity> cores) {
        b.writeVarInt(cores.size());
        for (var core : cores) {
            b.writeUtf(core.id(), 64);
            b.writeUtf(core.name(), 128);
            b.writeUtf(core.version(), 128);
            b.writeUtf(core.platform(), 32);
            b.writeUtf(core.sha256(), 64);
        }
    }

    public static void register() {
        Network.register(Network.Direction.TO_CLIENT, Settings.ID, Settings.CODEC);
        Network.register(Network.Direction.TO_SERVER, Status.ID, Status.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Lease.ID, Lease.CODEC);
        Network.register(Network.Direction.TO_SERVER, Lease.ID, Lease.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Blob.ID, Blob.CODEC);
        Network.register(Network.Direction.TO_SERVER, Blob.ID, Blob.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Demand.ID, Demand.CODEC);
    }

    private HybridPackets() {}
}
