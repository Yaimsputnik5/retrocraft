package com.sammwy.retrocraft;

import java.nio.file.Path;

import org.slf4j.*;

import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.InstanceManager;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.console.Consoles;
import com.sammwy.retrocraft.server.item.RetrocraftItemGroups;
import com.sammwy.retrocraft.server.net.ServerNetworking;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.storage.LevelResource;

public final class Retrocraft {
    public static final Logger LOG = LoggerFactory.getLogger("retrocraft");
    public static java.util.function.Supplier<MenuType<ConsoleScreenHandler>> CONSOLE_SCREEN;
    private static InstanceManager instances;
    private static volatile MinecraftServer bootServer;

    public static InstanceManager instances() {
        return instances;
    }

    public static void initialize() {
        Consoles.register();
        CONSOLE_SCREEN =
                Platform.register(
                        BuiltInRegistries.MENU,
                        Ids.of("retrocraft", "console"),
                        Platform::consoleMenuType);
        RetrocraftItemGroups.register();
        ServerNetworking.register(Retrocraft::instances);
    }

    public static void stop() {
        bootServer = null;
        if (instances != null) {
            instances.close();
            instances = null;
        }
    }

    /** Config, worker extraction and the first ROM scan happen off the server thread. */
    public static void start(MinecraftServer server) {
        bootServer = server;
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
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
