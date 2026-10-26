package com.sammwy.retrocraft.platform.forge;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.client.RetrocraftClient;
import com.sammwy.retrocraft.client.ui.ConsoleInventoryScreen;
import com.sammwy.retrocraft.compat.ExtraModels;
import com.sammwy.retrocraft.compat.ItemModels;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.platform.ClientPlatform;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client references are only reached through Forge's physical-side dispatch. */
final class ForgeClient implements ClientNetwork.Backend, ClientPlatform.Backend {
    private final List<KeyMapping> keys = new ArrayList<>();
    private final List<ResourceLocation> models = new ArrayList<>();
    private final List<Runnable> setup = new ArrayList<>();
    private final List<Consumer<EntityRenderersEvent.RegisterRenderers>> renderers =
            new ArrayList<>();

    static void install() {
        var client = new ForgeClient();
        ClientNetwork.install(client);
        ClientPlatform.install(client);
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(
                (RegisterKeyMappingsEvent event) -> client.keys.forEach(event::register));
        modBus.addListener(
                (ModelEvent.RegisterAdditional event) ->
                        ExtraModels.register(event, client.models));
        modBus.addListener(
                (EntityRenderersEvent.RegisterRenderers event) ->
                        client.renderers.forEach(callback -> callback.accept(event)));
        modBus.addListener(
                (FMLClientSetupEvent event) ->
                        event.enqueueWork(
                                () -> {
                                    new RetrocraftClient().initialize();
                                    client.setup.forEach(Runnable::run);
                                }));
    }

    @Override
    public <T extends CustomPacketPayload> void receive(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ClientNetwork.Context> receiver) {
        ForgePlatform.NETWORK.clientReceiver(
                type,
                packet ->
                        receiver.accept(
                                packet, new ClientNetwork.Context(Minecraft.getInstance())));
    }

    @Override
    public void send(CustomPacketPayload packet) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null)
            ForgePlatform.NETWORK.channel().send(packet, connection.getConnection());
    }

    @Override
    public boolean canSend(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null
                && ForgePlatform.NETWORK.channel().isRemotePresent(connection.getConnection());
    }

    @Override
    public KeyMapping key(KeyMapping key) {
        keys.add(key);
        return key;
    }

    @Override
    public void joined(Runnable callback) {
        MinecraftForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingIn event) -> callback.run());
    }

    @Override
    public void disconnected(Runnable callback) {
        MinecraftForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut event) -> callback.run());
    }

    @Override
    public void tick(Consumer<Minecraft> callback) {
        MinecraftForge.EVENT_BUS.addListener(
                (TickEvent.ClientTickEvent.Post event) -> callback.accept(Minecraft.getInstance()));
    }

    @Override
    public void frame(Runnable callback) {
        MinecraftForge.EVENT_BUS.addListener(
                (TickEvent.RenderTickEvent.Pre event) -> callback.run());
    }

    @Override
    public void hud(Consumer<GuiGraphics> callback) {
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener(
                        (AddGuiOverlayLayersEvent event) ->
                                event.getLayeredDraw()
                                        .addWithCondition(
                                                Ids.of("retrocraft", "downloads"),
                                                (graphics, tick) -> callback.accept(graphics),
                                                () -> Minecraft.getInstance().screen == null));
        MinecraftForge.EVENT_BUS.addListener(
                (ScreenEvent.Render.Post event) -> callback.accept(event.getGuiGraphics()));
    }

    @Override
    public void predicate(Item item, ResourceLocation id, ClientPlatform.ItemPredicate predicate) {
        setup.add(() -> ItemModels.register(item, id, predicate));
    }

    @Override
    public void models(Collection<ResourceLocation> additions) {
        models.addAll(additions);
    }

    @Override
    public <T extends BlockEntity> void blockRenderer(
            BlockEntityType<T> type, BlockEntityRendererProvider<T> renderer) {
        renderers.add(event -> event.registerBlockEntityRenderer(type, renderer));
    }

    @Override
    public void model(
            ResourceLocation id,
            DockedConsoleBlockEntity console,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            boolean ambient) {
        ExtraModels.draw(id, console, poses, buffers, light, overlay, ambient);
    }

    @Override
    public void consoleScreen() {
        setup.add(
                () ->
                        MenuScreens.register(
                                Retrocraft.CONSOLE_SCREEN.get(), ConsoleInventoryScreen::new));
    }
}
