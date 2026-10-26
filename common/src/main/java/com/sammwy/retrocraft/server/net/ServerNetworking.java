package com.sammwy.retrocraft.server.net;

import java.util.function.Supplier;

import com.sammwy.retrocraft.platform.Network;
import com.sammwy.retrocraft.server.InstanceManager;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Payload registration and C2S routing; packets are dropped until the manager is ready. */
public final class ServerNetworking {
    private interface Handler<T> {
        void handle(InstanceManager manager, ServerPlayer player, T packet);
    }

    public static void register(Supplier<InstanceManager> instances) {
        Packets.register();
        LinkPackets.register();
        HybridPackets.register();
        SettingsPackets.register();
        receive(instances, Packets.Request.ID, InstanceManager::request);
        receive(instances, LinkPackets.Request.ID, InstanceManager::linkRequest);
        receive(instances, Packets.Preferences.ID, InstanceManager::preferences);
        receive(instances, Packets.ThumbnailUpload.ID, InstanceManager::thumbnailUpload);
        receive(instances, HybridPackets.Status.ID, InstanceManager::coreStatus);
        receive(instances, HybridPackets.Lease.ID, InstanceManager::ownership);
        receive(instances, HybridPackets.Blob.ID, InstanceManager::upload);
        receive(
                instances,
                SettingsPackets.GuiRequest.ID,
                (manager, player, packet) -> manager.openSettings(player));
        receive(instances, SettingsPackets.ConfigApply.ID, InstanceManager::applySettings);
        receive(instances, SettingsPackets.RomAction.ID, InstanceManager::romAction);
        receive(instances, SettingsPackets.RomUpload.ID, InstanceManager::romUpload);
        receive(instances, SettingsPackets.BiosRequest.ID, InstanceManager::requestBios);
        receive(instances, SettingsPackets.BiosReady.ID, InstanceManager::biosReady);
    }

    private static <T extends CustomPacketPayload> void receive(
            Supplier<InstanceManager> instances,
            CustomPacketPayload.Type<T> id,
            Handler<T> handler) {
        Network.receive(
                id,
                (packet, player) -> {
                    InstanceManager manager = instances.get();
                    if (manager != null) handler.handle(manager, player, packet);
                });
    }

    private ServerNetworking() {}
}
