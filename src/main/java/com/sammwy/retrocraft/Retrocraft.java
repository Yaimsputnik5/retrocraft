package com.sammwy.retrocraft;

import java.nio.file.Path;

import org.slf4j.*;

import com.sammwy.retrocraft.server.InstanceManager;
import com.sammwy.retrocraft.server.command.RetrocraftCommands;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.console.Consoles;
import com.sammwy.retrocraft.server.item.RetrocraftItemGroups;
import com.sammwy.retrocraft.server.net.ServerNetworking;
import com.sammwy.retrocraft.server.screen.*;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

public final class Retrocraft implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("retrocraft");
    public static ScreenHandlerType<ConsoleScreenHandler> CONSOLE_SCREEN;
    private static InstanceManager instances;
    private static volatile MinecraftServer bootServer;

    public static InstanceManager instances() {
        return instances;
    }

    @Override
    public void onInitialize() {
        Consoles.register();
        CONSOLE_SCREEN =
                Registry.register(
                        Registries.SCREEN_HANDLER,
                        Identifier.of("retrocraft", "console"),
                        new ExtendedScreenHandlerType<>(
                                ConsoleScreenHandler::new, ConsoleOpenData.CODEC));
        RetrocraftItemGroups.register();
        ServerNetworking.register(Retrocraft::instances);
        RetrocraftCommands.register(Retrocraft::instances);
        ServerLifecycleEvents.SERVER_STARTED.register(Retrocraft::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(
                server -> {
                    bootServer = null;
                    if (instances != null) {
                        instances.close();
                        instances = null;
                    }
                });
        ServerEntityEvents.ENTITY_LOAD.register(
                (entity, world) -> {
                    if (instances != null) instances.entityLoaded(entity);
                });
        ServerEntityEvents.ENTITY_UNLOAD.register(
                (entity, world) -> {
                    if (instances != null) instances.entityUnloaded(entity);
                });
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (instances != null) instances.tick();
                });
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    if (instances != null) instances.sendCatalog(handler.player);
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    if (instances != null) instances.disconnect(handler.player.getUuid());
                });
    }

    /** Config, worker extraction and the first ROM scan happen off the server thread. */
    private static void start(MinecraftServer server) {
        bootServer = server;
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        Thread.ofVirtual()
                .name("retrocraft-config-load")
                .start(
                        () -> {
                            try {
                                InstanceManager manager =
                                        new InstanceManager(
                                                server, RetrocraftConfig.load(), worldRoot);
                                server.execute(
                                        () -> {
                                            if (bootServer == server) instances = manager;
                                            else manager.close();
                                        });
                            } catch (Exception ex) {
                                LOG.error("Retrocraft could not initialize", ex);
                            }
                        });
    }
}
