package com.sammwy.retrocraft.platform;

import java.util.Collection;
import java.util.function.BiConsumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** The shared protocol uses vanilla payloads; loaders only supply transport and registration. */
public final class Network {
    public enum Direction {
        TO_SERVER,
        TO_CLIENT
    }

    public interface Backend {
        <T extends CustomPacketPayload> void register(
                Direction direction,
                CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

        <T extends CustomPacketPayload> void receive(
                CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayer> receiver);

        void send(ServerPlayer player, CustomPacketPayload packet);

        boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

        Collection<ServerPlayer> tracking(Entity entity);
    }

    private static Backend backend;

    public static void install(Backend implementation) {
        backend = implementation;
    }

    public static <T extends CustomPacketPayload> void register(
            Direction direction,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        backend.register(direction, type, codec);
    }

    public static <T extends CustomPacketPayload> void receive(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayer> receiver) {
        backend.receive(type, receiver);
    }

    public static void send(ServerPlayer player, CustomPacketPayload packet) {
        backend.send(player, packet);
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return backend.canSend(player, type);
    }

    public static Collection<ServerPlayer> tracking(Entity entity) {
        return backend.tracking(entity);
    }

    private Network() {}
}
