package com.sammwy.retrocraft.platform.fabric;

import java.nio.file.Path;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.platform.Network;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.command.RetrocraftCommands;
import com.sammwy.retrocraft.server.console.ConsoleHost;
import com.sammwy.retrocraft.server.screen.ConsoleOpenData;
import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class FabricPlatform implements ModInitializer, Platform.Backend, Network.Backend {
    @Override
    public void onInitialize() {
        Platform.install(this);
        Network.install(this);
        Retrocraft.initialize();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) ->
                        RetrocraftCommands.register(dispatcher, Retrocraft::instances));
        ServerLifecycleEvents.SERVER_STARTED.register(Retrocraft::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Retrocraft.stop());
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (Retrocraft.instances() != null) Retrocraft.instances().tick();
                });
        ServerEntityEvents.ENTITY_LOAD.register(
                (entity, world) -> {
                    if (Retrocraft.instances() != null) Retrocraft.instances().entityLoaded(entity);
                });
        ServerEntityEvents.ENTITY_UNLOAD.register(
                (entity, world) -> {
                    if (Retrocraft.instances() != null)
                        Retrocraft.instances().entityUnloaded(entity);
                });
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    if (Retrocraft.instances() != null)
                        Retrocraft.instances().sendCatalog(handler.player);
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    if (Retrocraft.instances() != null)
                        Retrocraft.instances().disconnect(handler.player.getUUID());
                });
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public String version() {
        return FabricLoader.getInstance()
                .getModContainer("retrocraft")
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
    }

    @Override
    public <V, T extends V> Supplier<T> register(
            Registry<V> registry, ResourceLocation id, Supplier<T> factory) {
        T value = Registry.register(registry, id, factory.get());
        return () -> value;
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            Platform.BlockEntityFactory<T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::create, blocks).build();
    }

    @Override
    public MenuType<ConsoleScreenHandler> consoleMenuType() {
        return new ExtendedScreenHandlerType<>(ConsoleScreenHandler::new, ConsoleOpenData.CODEC);
    }

    @Override
    public void openConsoleMenu(ServerPlayer player, ConsoleHost host, int locked) {
        player.openMenu(
                new ExtendedScreenHandlerFactory<ConsoleOpenData>() {
                    @Override
                    public ConsoleOpenData getScreenOpeningData(ServerPlayer p) {
                        return new ConsoleOpenData(host.definition().id(), locked);
                    }

                    @Override
                    public Component getDisplayName() {
                        return host.definition().displayName();
                    }

                    @Override
                    public AbstractContainerMenu createMenu(
                            int id, Inventory inventory, Player user) {
                        return new ConsoleScreenHandler(id, inventory, host, locked);
                    }
                });
    }

    @Override
    public CreativeModeTab creativeTab(
            Component title,
            Supplier<ItemStack> icon,
            CreativeModeTab.DisplayItemsGenerator entries) {
        return FabricItemGroup.builder().title(title).icon(icon).displayItems(entries).build();
    }

    @Override
    public <T extends CustomPacketPayload> void register(
            Network.Direction direction,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        if (direction == Network.Direction.TO_SERVER)
            PayloadTypeRegistry.playC2S().register(type, codec);
        else PayloadTypeRegistry.playS2C().register(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void receive(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayer> receiver) {
        ServerPlayNetworking.registerGlobalReceiver(
                type, (packet, context) -> receiver.accept(packet, context.player()));
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload packet) {
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public Collection<ServerPlayer> tracking(Entity entity) {
        return PlayerLookup.tracking(entity);
    }
}
