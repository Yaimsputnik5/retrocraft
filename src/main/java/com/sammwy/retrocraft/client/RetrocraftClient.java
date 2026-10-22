package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.net.HybridPackets;
import com.sammwy.retrocraft.roms.RomCatalog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.item.*;
import java.util.*;

public final class RetrocraftClient implements ClientModInitializer {
    private final AudioStreams audio = new AudioStreams();
    public static HybridClient EMULATION;
    private final List<RomCatalog.Entry> pendingCatalog = new ArrayList<>();
    private int catalogGeneration;
    private boolean catalogDirty;

    @Override public void onInitializeClient() {
        EMULATION = new HybridClient(audio);
        ClientPlayNetworking.registerGlobalReceiver(HybridPackets.Settings.ID, (packet, context) -> EMULATION.settings(packet));
        ClientPlayNetworking.registerGlobalReceiver(HybridPackets.Lease.ID, (packet, context) -> EMULATION.lease(packet));
        ClientPlayNetworking.registerGlobalReceiver(HybridPackets.Blob.ID, (packet, context) -> EMULATION.blob(packet));
        ClientPlayNetworking.registerGlobalReceiver(HybridPackets.Demand.ID, (packet, context) -> EMULATION.demand(packet));
        HandledScreens.register(Retrocraft.CONSOLE_SCREEN, ConsoleInventoryScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(Packets.Ui.ID, (packet, context) -> {
            var client = context.client();
            switch (packet.mode()) {
                case Packets.PLAY -> {
                    if (client.currentScreen instanceof ConsoleScreen screen && screen.instance.equals(packet.instance())) screen.update(packet);
                    else client.setScreen(new ConsoleScreen(packet));
                }
                case Packets.ROLE -> { if (client.currentScreen instanceof ConsoleScreen screen && screen.instance.equals(packet.instance())) screen.update(packet); }
                case Packets.STOP -> { if (client.currentScreen instanceof ConsoleScreen screen && screen.instance.equals(packet.instance())) screen.serverClosed(); }
                default -> { }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Video.ID, (packet, context) -> {
            if (!EMULATION.owns(packet.instance()) && context.client().currentScreen instanceof ConsoleScreen screen && screen.instance.equals(packet.instance())) screen.video(packet.frame());
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Audio.ID, (packet, context) -> { if (!EMULATION.owns(packet.instance())) audio.accept(context.client(), packet); });
        ClientPlayNetworking.registerGlobalReceiver(Packets.AudioStop.ID, (packet, context) -> audio.stop(packet.instance()));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Catalog.ID, (packet, context) -> {
            if (packet.first()) { pendingCatalog.clear(); catalogGeneration = packet.generation(); }
            if (packet.generation() != catalogGeneration) return;
            pendingCatalog.addAll(packet.entries());
            if (packet.last()) { RomCatalog.clientEntries(pendingCatalog); pendingCatalog.clear(); catalogDirty = true; }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            EMULATION.tick(client);
            audio.tick();
            if (!catalogDirty || client.world == null || client.player == null) return;
            var display = new ItemGroup.DisplayContext(client.world.getEnabledFeatures(), client.player.hasPermissionLevel(2), client.world.getRegistryManager());
            Retrocraft.CREATIVE_GROUP.updateEntries(display);
            ItemGroups.getSearchGroup().updateEntries(display);
            if (client.currentScreen instanceof CreativeInventoryScreen screen)
                screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            catalogDirty = false;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            EMULATION.disconnect(); audio.close(); RomCatalog.clientEntries(List.of()); pendingCatalog.clear(); catalogDirty = true;
            if (client.currentScreen instanceof ConsoleScreen screen) screen.serverClosed();
        });
    }
}
