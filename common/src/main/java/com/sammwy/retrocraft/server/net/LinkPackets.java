package com.sammwy.retrocraft.server.net;

import java.util.*;

import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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
    public record Request(int action, UUID instance, UUID target) implements CustomPacketPayload {
        public static final Type<Request> ID = new Type<>(Ids.of("retrocraft", "link_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                new StreamCodec<>() {
                    public Request decode(RegistryFriendlyByteBuf b) {
                        return new Request(b.readVarInt(), b.readUUID(), b.readUUID());
                    }

                    public void encode(RegistryFriendlyByteBuf b, Request p) {
                        b.writeVarInt(p.action);
                        b.writeUUID(p.instance);
                        b.writeUUID(p.target);
                    }
                };

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Entry(UUID instance, String name) {}

    public record State(int mode, UUID instance, UUID token, List<Entry> entries)
            implements CustomPacketPayload {
        public static final Type<State> ID = new Type<>(Ids.of("retrocraft", "link_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
                new StreamCodec<>() {
                    public State decode(RegistryFriendlyByteBuf b) {
                        int mode = b.readVarInt();
                        UUID instance = b.readUUID(), token = b.readUUID();
                        int size = b.readVarInt();
                        if (size < 0 || size > 32)
                            throw new IllegalArgumentException("Oversized link list");
                        List<Entry> entries = new ArrayList<>();
                        for (int i = 0; i < size; i++)
                            entries.add(new Entry(b.readUUID(), b.readUtf(200)));
                        return new State(mode, instance, token, List.copyOf(entries));
                    }

                    public void encode(RegistryFriendlyByteBuf b, State p) {
                        b.writeVarInt(p.mode);
                        b.writeUUID(p.instance);
                        b.writeUUID(p.token);
                        b.writeVarInt(p.entries.size());
                        for (Entry e : p.entries) {
                            b.writeUUID(e.instance);
                            b.writeUtf(e.name, 200);
                        }
                    }
                };

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void register() {
        Network.register(Network.Direction.TO_SERVER, Request.ID, Request.CODEC);
        Network.register(Network.Direction.TO_CLIENT, State.ID, State.CODEC);
    }

    private LinkPackets() {}
}
