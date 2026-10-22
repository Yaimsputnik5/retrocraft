package dev.sammwy.retrocraft.client;

import dev.sammwy.retrocraft.Retrocraft;
import dev.sammwy.retrocraft.net.Packets;
import dev.sammwy.retrocraft.server.RomCatalog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.item.*;
import java.util.*;

public final class RetrocraftClient implements ClientModInitializer {
    private final AudioStreams audio = new AudioStreams();
    private final List<RomCatalog.Entry> pendingCatalog = new ArrayList<>();
    private int catalogGeneration;
    private boolean catalogDirty;

    @Override public void onInitializeClient() {
        HandledScreens.register(Retrocraft.CONSOLE_SCREEN, ConsoleInventoryScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(Packets.Ui.ID, (packet, context) -> {
            var client = context.client();
            switch (packet.mode()) {
                case Packets.PLAY -> {
                    if (client.currentScreen instanceof NesScreen screen && screen.instance.equals(packet.instance())) screen.update(packet);
                    else client.setScreen(new NesScreen(packet));
                }
                case Packets.ROLE -> { if (client.currentScreen instanceof NesScreen screen && screen.instance.equals(packet.instance())) screen.update(packet); }
                case Packets.STOP -> { if (client.currentScreen instanceof NesScreen screen && screen.instance.equals(packet.instance())) screen.serverClosed(); }
                default -> { }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Video.ID, (packet, context) -> {
            if (context.client().currentScreen instanceof NesScreen screen && screen.instance.equals(packet.instance())) screen.video(packet.frame());
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Audio.ID, (packet, context) -> audio.accept(context.client(), packet));
        ClientPlayNetworking.registerGlobalReceiver(Packets.AudioStop.ID, (packet, context) -> audio.stop(packet.instance()));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Catalog.ID, (packet, context) -> {
            if (packet.first()) { pendingCatalog.clear(); catalogGeneration = packet.generation(); }
            if (packet.generation() != catalogGeneration) return;
            pendingCatalog.addAll(packet.entries());
            if (packet.last()) { RomCatalog.clientEntries(pendingCatalog); pendingCatalog.clear(); catalogDirty = true; }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
            audio.close(); RomCatalog.clientEntries(List.of()); pendingCatalog.clear(); catalogDirty = true;
            if (client.currentScreen instanceof NesScreen screen) screen.serverClosed();
        });
    }
}
