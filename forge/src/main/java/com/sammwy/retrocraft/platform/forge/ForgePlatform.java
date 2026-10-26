package com.sammwy.retrocraft.platform.forge;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.platform.Network;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.command.RetrocraftCommands;
import com.sammwy.retrocraft.server.console.ConsoleHost;
import com.sammwy.retrocraft.server.screen.ConsoleOpenData;
import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;

@Mod("retrocraft")
public final class ForgePlatform implements Platform.Backend {
    static final ForgeNetwork NETWORK = new ForgeNetwork();
    private final List<Registration<?, ?>> registrations = new ArrayList<>();

    private static final class Registration<V, T extends V> implements Supplier<T> {
        private final Registry<V> registry;
        private final ResourceLocation id;
        private final Supplier<T> factory;
        private T value;

        Registration(Registry<V> registry, ResourceLocation id, Supplier<T> factory) {
            this.registry = registry;
            this.id = id;
            this.factory = factory;
        }

        void apply(RegisterEvent event) {
            event.register(
                    registry.key(),
                    id,
                    () -> {
                        value = factory.get();
                        return value;
                    });
        }

        @Override
        public T get() {
            if (value == null)
                throw new IllegalStateException("Content is not registered yet: " + id);
            return value;
        }
    }

    public ForgePlatform() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Platform.install(this);
        Network.install(NETWORK);
        Retrocraft.initialize();
        NETWORK.finish();
        modBus.addListener(
                (RegisterEvent event) -> registrations.forEach(entry -> entry.apply(event)));
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(
                (RegisterCommandsEvent event) ->
                        RetrocraftCommands.register(event.getDispatcher(), Retrocraft::instances));
        bus.addListener((ServerStartedEvent event) -> Retrocraft.start(event.getServer()));
        bus.addListener(
                (ServerStoppingEvent event) -> {
                    Retrocraft.stop();
                    NETWORK.clear();
                });
        bus.addListener(
                (TickEvent.ServerTickEvent.Post event) -> {
                    if (Retrocraft.instances() != null) Retrocraft.instances().tick();
                });
        bus.addListener(
                (EntityJoinLevelEvent event) -> {
                    if (!event.getLevel().isClientSide() && Retrocraft.instances() != null)
                        Retrocraft.instances().entityLoaded(event.getEntity());
                });
        bus.addListener(
                (EntityLeaveLevelEvent event) -> {
                    if (!event.getLevel().isClientSide()) {
                        NETWORK.unload(event.getEntity());
                        if (Retrocraft.instances() != null)
                            Retrocraft.instances().entityUnloaded(event.getEntity());
                    }
                });
        bus.addListener(
                (PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player
                            && Retrocraft.instances() != null)
                        Retrocraft.instances().sendCatalog(player);
                });
        bus.addListener(
                (PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        NETWORK.disconnect(player);
                        if (Retrocraft.instances() != null)
                            Retrocraft.instances().disconnect(player.getUUID());
                    }
                });
        bus.addListener(
                (PlayerEvent.StartTracking event) -> {
                    if (event.getEntity() instanceof ServerPlayer player)
                        NETWORK.track(event.getTarget(), player);
                });
        bus.addListener(
                (PlayerEvent.StopTracking event) -> {
                    if (event.getEntity() instanceof ServerPlayer player)
                        NETWORK.untrack(event.getTarget(), player);
                });
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClient::install);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public String version() {
        return ModList.get()
                .getModContainerById("retrocraft")
                .orElseThrow()
                .getModInfo()
                .getVersion()
                .toString();
    }

    @Override
    public <V, T extends V> Supplier<T> register(
            Registry<V> registry, ResourceLocation id, Supplier<T> factory) {
        var entry = new Registration<>(registry, id, factory);
        registrations.add(entry);
        return entry;
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            Platform.BlockEntityFactory<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::create, blocks).build(null);
    }

    @Override
    public MenuType<ConsoleScreenHandler> consoleMenuType() {
        return IForgeMenuType.create(
                (id, inventory, buffer) ->
                        new ConsoleScreenHandler(
                                id,
                                inventory,
                                new ConsoleOpenData(
                                        buffer.readResourceLocation(), buffer.readInt())));
    }

    @Override
    public void openConsoleMenu(ServerPlayer player, ConsoleHost host, int lockedSlot) {
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, user) ->
                                new ConsoleScreenHandler(id, inventory, host, lockedSlot),
                        host.definition().displayName()),
                buffer -> {
                    buffer.writeResourceLocation(host.definition().id());
                    buffer.writeInt(lockedSlot);
                });
    }

    @Override
    public CreativeModeTab creativeTab(
            Component title,
            Supplier<ItemStack> icon,
            CreativeModeTab.DisplayItemsGenerator entries) {
        return CreativeModeTab.builder().title(title).icon(icon).displayItems(entries).build();
    }
}
