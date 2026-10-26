package com.sammwy.retrocraft.server.net;

import java.util.*;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class LinkPackets {
    public static final int LIST = 1,
            INVITE = 2,
            ACCEPT = 3,
            DECLINE = 4,
            DISCONNECT = 5,
            CANCEL = 6;
    public static final int CANDIDATES = 1,
            OFFER = 2,
            CONNECTED = 3,
            CLOSED = 4,
            WAITING = 5,
            DECLINED = 6,
            EXPIRED = 7,
            UNAVAILABLE = 8;
    public static final UUID NONE = new UUID(0, 0);

    /** Target is a console ID for INVITE, or the single-use invitation token for replies. */
    public record Request(int action, UUID instance, UUID target) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("retrocraft", "link_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC =
                new PacketCodec<>() {
                    public Request decode(RegistryByteBuf b) {
                        return new Request(b.readVarInt(), b.readUuid(), b.readUuid());
                    }

                    public void encode(RegistryByteBuf b, Request p) {
                        b.writeVarInt(p.action);
                        b.writeUuid(p.instance);
                        b.writeUuid(p.target);
                    }
                };

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Entry(UUID instance, String name) {}

    public record State(int mode, UUID instance, UUID token, List<Entry> entries)
            implements CustomPayload {
        public static final Id<State> ID = new Id<>(Identifier.of("retrocraft", "link_state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC =
                new PacketCodec<>() {
                    public State decode(RegistryByteBuf b) {
                        int mode = b.readVarInt();
                        UUID instance = b.readUuid(), token = b.readUuid();
                        int size = b.readVarInt();
                        if (size < 0 || size > 32)
                            throw new IllegalArgumentException("Oversized link list");
                        List<Entry> entries = new ArrayList<>();
                        for (int i = 0; i < size; i++)
                            entries.add(new Entry(b.readUuid(), b.readString(200)));
                        return new State(mode, instance, token, List.copyOf(entries));
                    }

                    public void encode(RegistryByteBuf b, State p) {
                        b.writeVarInt(p.mode);
                        b.writeUuid(p.instance);
                        b.writeUuid(p.token);
                        b.writeVarInt(p.entries.size());
                        for (Entry e : p.entries) {
                            b.writeUuid(e.instance);
                            b.writeString(e.name, 200);
                        }
                    }
                };

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID, State.CODEC);
    }

    private LinkPackets() {}
}
