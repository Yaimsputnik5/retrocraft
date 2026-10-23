package com.sammwy.retrocraft.client;

import java.util.*;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.cores.transfer.Downloads;
import com.sammwy.retrocraft.roms.RomCatalog;
import com.sammwy.retrocraft.server.block.DockedConsoleBlock;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.net.HybridPackets;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.net.SettingsPackets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class RetrocraftClient implements ClientModInitializer {
    private final AudioStreams audio = new AudioStreams();
    private final VideoStreams video = new VideoStreams();
    static VideoStreams VIDEO;
    static AudioStreams AUDIO;
    static KeyBinding SETTINGS_KEY;
    public static HybridClient EMULATION;
    private final List<RomCatalog.Entry> pendingCatalog = new ArrayList<>();
    private int catalogGeneration;
    private boolean catalogDirty;

    @Override
    public void onInitializeClient() {
        ClientPreferences.load();
        DownloadHud.register();
        SETTINGS_KEY =
                KeyBindingHelper.registerKeyBinding(
                        new KeyBinding(
                                "key.retrocraft.settings",
                                InputUtil.Type.KEYSYM,
                                GLFW.GLFW_KEY_F8,
                                "category.retrocraft"));
        EMULATION = new HybridClient(audio);
        VIDEO = video;
        AUDIO = audio;
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> ClientPreferences.send());
        for (var console : ConsoleRegistry.consoles()) {
            if (console.type() == ConsoleType.HANDHELD)
                FabricModelPredicateProviderRegistry.register(
                        Registries.ITEM.get(console.id()),
                        Identifier.of("retrocraft", "powered"),
                        (stack, world, entity, seed) ->
                                HandheldConsoleItem.instance(stack) == null ? 0 : 1);
            else
                BlockEntityRendererFactories.register(
                        ((DockedConsoleBlock) Registries.BLOCK.get(console.id())).entityType(),
                        DockedConsoleRenderer::new);
        }
        for (var tv : ConsoleRegistry.televisions())
            BlockEntityRendererFactories.register(tv.entityType(), TvRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(
                HybridPackets.Settings.ID,
                (packet, context) -> {
                    ClientPreferences.send();
                    EMULATION.settings(packet);
                });
        ClientPlayNetworking.registerGlobalReceiver(
                HybridPackets.Lease.ID, (packet, context) -> EMULATION.lease(packet));
        ClientPlayNetworking.registerGlobalReceiver(
                HybridPackets.Blob.ID, (packet, context) -> EMULATION.blob(packet));
        ClientPlayNetworking.registerGlobalReceiver(
                HybridPackets.Demand.ID, (packet, context) -> EMULATION.demand(packet));
        HandledScreens.register(Retrocraft.CONSOLE_SCREEN, ConsoleInventoryScreen::new);
        ModelLoadingPlugin.register(
                plugin -> DockedConsoleRenderer.arcadeModels().forEach(id -> plugin.addModels(id)));
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.Ui.ID,
                (packet, context) -> {
                    var client = context.client();
                    switch (packet.mode()) {
                        case Packets.PLAY -> {
                            if (client.currentScreen instanceof ConsoleScreen screen
                                    && screen.instance.equals(packet.instance()))
                                screen.update(packet);
                            else client.setScreen(new ConsoleScreen(packet));
                        }
                        case Packets.ROLE -> {
                            if (client.currentScreen instanceof ConsoleScreen screen
                                    && screen.instance.equals(packet.instance()))
                                screen.update(packet);
                        }
                        case Packets.STOP -> {
                            if (client.currentScreen instanceof ConsoleScreen screen
                                    && screen.instance.equals(packet.instance()))
                                screen.serverClosed();
                            PlayerHud.drop(packet.instance());
                        }
                        default -> {}
                    }
                });
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.Video.ID,
                (packet, context) -> {
                    if (!EMULATION.owns(packet.instance())) video.accept(packet);
                });
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.Audio.ID,
                (packet, context) -> {
                    if (!EMULATION.owns(packet.instance())) audio.accept(context.client(), packet);
                });
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.AudioStop.ID, (packet, context) -> audio.stop(packet.instance()));
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.Thumbnail.ID, (packet, context) -> Thumbnails.accept(packet));
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.Players.ID, (packet, context) -> PlayerHud.roster(packet));
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.Pad.ID, (packet, context) -> PlayerHud.pad(packet));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.BiosRequired.ID,
                (packet, context) ->
                        context.client()
                                .execute(
                                        () ->
                                                context.client()
                                                        .setScreen(
                                                                new BiosRequiredScreen(
                                                                        packet.coreId(),
                                                                        packet.administrator()))));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.GuiOpen.ID,
                (packet, context) ->
                        context.client()
                                .execute(() -> SettingsClient.guiOpen(context.client(), packet)));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.ConfigSaved.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.configSaved(packet)));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.RomList.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.romList(packet)));

        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.BiosChunk.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.biosChunk(packet)));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.BiosManifest.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.biosManifest(packet)));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.BiosUnavailable.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.biosUnavailable(packet)));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.ScanEvent.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.scanEvent(packet)));
        ClientPlayNetworking.registerGlobalReceiver(
                SettingsPackets.RomEvent.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.romEvent(packet)));
        ClientPlayNetworking.registerGlobalReceiver(
                Packets.Catalog.ID,
                (packet, context) -> {
                    if (packet.first()) {
                        pendingCatalog.clear();
                        catalogGeneration = packet.generation();
                    }
                    if (packet.generation() != catalogGeneration) return;
                    pendingCatalog.addAll(packet.entries());
                    if (packet.last()) {
                        RomCatalog.clientEntries(pendingCatalog);
                        pendingCatalog.clear();
                        catalogDirty = true;
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    while (SETTINGS_KEY.wasPressed()) {
                        if (client.currentScreen instanceof SettingsScreen screen) screen.close();
                        else client.setScreen(SettingsScreen.modMenu(client.currentScreen));
                    }
                    EMULATION.tick(client);
                    SettingsClient.tick();
                    video.tick();
                    audio.tick();
                    ScreenTextures.tick();
                    Thumbnails.tick();
                    if (!catalogDirty || client.world == null || client.player == null) return;
                    var display =
                            new ItemGroup.DisplayContext(
                                    client.world.getEnabledFeatures(),
                                    client.player.hasPermissionLevel(2),
                                    client.world.getRegistryManager());
                    Retrocraft.CREATIVE_GROUP.updateEntries(display);
                    Retrocraft.GAMES_GROUP.updateEntries(display);
                    Retrocraft.ARCADES_GROUP.updateEntries(display);
                    ItemGroups.getSearchGroup().updateEntries(display);
                    if (client.currentScreen instanceof CreativeInventoryScreen screen)
                        screen.init(
                                client,
                                client.getWindow().getScaledWidth(),
                                client.getWindow().getScaledHeight());
                    catalogDirty = false;
                });
        // Locally emulated frames are uploaded per rendered frame, not per tick, so in-world
        // screens stay smooth.
        WorldRenderEvents.START.register(
                context -> {
                    EMULATION.pollInput(MinecraftClient.getInstance());
                    for (UUID id : EMULATION.active()) {
                        var frame = EMULATION.frame(id);
                        if (frame != null) video.local(id, frame);
                    }
                    video.render();
                });
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    CameraRig.reset();
                    EMULATION.disconnect();
                    audio.close();
                    video.close();
                    Thumbnails.close();
                    PlayerHud.clear();
                    SettingsClient.disconnect();
                    DownloadHud.DOWNLOADS = new Downloads();
                    RomCatalog.clientEntries(List.of());
                    pendingCatalog.clear();
                    catalogDirty = true;
                    if (client.currentScreen instanceof ConsoleScreen screen) screen.serverClosed();
                });
    }
}
