package com.sammwy.retrocraft.platform;

import java.util.function.BiConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Receivers are always called on the client thread. Kept separate for dedicated servers. */
public final class ClientNetwork {
    public record Context(Minecraft client) {}

    public interface Backend {
        <T extends CustomPacketPayload> void receive(
                CustomPacketPayload.Type<T> type, BiConsumer<T, Context> receiver);

        void send(CustomPacketPayload payload);

        boolean canSend(CustomPacketPayload.Type<?> type);
    }

    private static Backend backend;

    public static void install(Backend implementation) {
        backend = implementation;
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, BiConsumer<T, Context> receiver) {
        backend.receive(type, receiver);
    }

    public static void send(CustomPacketPayload payload) {
        backend.send(payload);
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return backend.canSend(type);
    }

    private ClientNetwork() {}
}
