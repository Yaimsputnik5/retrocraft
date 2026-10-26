package com.sammwy.retrocraft.platform.forge;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.sammwy.retrocraft.platform.Network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadProtocol;

/** Forge transport preserves the same vanilla payload codecs and checks direction per message. */
final class ForgeNetwork implements Network.Backend {
    private record Definition<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            EnumSet<Network.Direction> directions) {}

    private final Map<CustomPacketPayload.Type<?>, Definition<?>> definitions =
            new LinkedHashMap<>();
    private final Map<CustomPacketPayload.Type<?>, BiConsumer<CustomPacketPayload, ServerPlayer>>
            serverReceivers = new HashMap<>();
    private final Map<CustomPacketPayload.Type<?>, Consumer<CustomPacketPayload>> clientReceivers =
            new HashMap<>();
    private final Map<UUID, Set<ServerPlayer>> tracking = new HashMap<>();
    private Channel<CustomPacketPayload> channel;

    @Override
    public <T extends CustomPacketPayload> void register(
            Network.Direction direction,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        Definition<?> existing = definitions.get(type);
        if (existing != null) {
            existing.directions().add(direction);
            return;
        }
        StreamCodec<RegistryFriendlyByteBuf, T> exactCodec =
                new StreamCodec<>() {
                    @Override
                    public T decode(RegistryFriendlyByteBuf buffer) {
                        return codec.decode(buffer);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, T packet) {
                        codec.encode(buffer, packet);
                    }
                };
        definitions.put(type, new Definition<>(type, exactCodec, EnumSet.of(direction)));
    }

    void finish() {
        var protocol =
                ChannelBuilder.named("retrocraft:network")
                        .networkProtocolVersion(1)
                        .optional()
                        .payloadChannel()
                        .play();
        definitions.values().forEach(definition -> add(protocol, definition));
        channel = protocol.flow((PacketFlow) null).build();
    }

    private <T extends CustomPacketPayload> void add(
            PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> protocol,
            Definition<T> definition) {
        PacketFlow direction =
                definition.directions().size() == 2
                        ? null
                        : definition.directions().contains(Network.Direction.TO_SERVER)
                                ? PacketFlow.SERVERBOUND
                                : PacketFlow.CLIENTBOUND;
        protocol.flow(direction)
                .addMain(
                        definition.type(),
                        definition.codec(),
                        (packet, context) -> {
                            ServerPlayer sender = context.getSender();
                            if (sender != null) {
                                var receiver = serverReceivers.get(packet.type());
                                if (receiver != null) receiver.accept(packet, sender);
                            } else {
                                var receiver = clientReceivers.get(packet.type());
                                if (receiver != null) receiver.accept(packet);
                            }
                        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> void receive(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayer> receiver) {
        serverReceivers.put(type, (packet, player) -> receiver.accept((T) packet, player));
    }

    @SuppressWarnings("unchecked")
    <T extends CustomPacketPayload> void clientReceiver(
            CustomPacketPayload.Type<T> type, Consumer<T> receiver) {
        clientReceivers.put(type, packet -> receiver.accept((T) packet));
    }

    Channel<CustomPacketPayload> channel() {
        return channel;
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload packet) {
        channel.send(packet, player.connection.getConnection());
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return definitions.containsKey(type)
                && channel.isRemotePresent(player.connection.getConnection());
    }

    @Override
    public Collection<ServerPlayer> tracking(Entity entity) {
        return Set.copyOf(tracking.getOrDefault(entity.getUUID(), Set.of()));
    }

    void track(Entity entity, ServerPlayer player) {
        tracking.computeIfAbsent(entity.getUUID(), ignored -> new HashSet<>()).add(player);
    }

    void untrack(Entity entity, ServerPlayer player) {
        var players = tracking.get(entity.getUUID());
        if (players != null) {
            players.remove(player);
            if (players.isEmpty()) tracking.remove(entity.getUUID());
        }
    }

    void disconnect(ServerPlayer player) {
        tracking.values().forEach(players -> players.remove(player));
    }

    void unload(Entity entity) {
        tracking.remove(entity.getUUID());
    }

    void clear() {
        tracking.clear();
    }
}
