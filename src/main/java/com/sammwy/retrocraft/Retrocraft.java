package com.sammwy.retrocraft;

import com.sammwy.retrocraft.server.block.*;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.item.CartridgeItem;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.net.HybridPackets;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import com.sammwy.retrocraft.server.screen.*;
import com.sammwy.retrocraft.server.*;
import com.sammwy.retrocraft.roms.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Path;
import org.slf4j.*;
import com.sammwy.retrocraft.roms.discovery.RomFormats;

public final class Retrocraft implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("retrocraft");
    public static CartridgeItem NES_CARTRIDGE;
    public static ConsoleDefinition NES_HANDHELD, NES_DOCKED;
    public static ScreenHandlerType<ConsoleScreenHandler> CONSOLE_SCREEN;
    public static ItemGroup CREATIVE_GROUP;
    private static InstanceManager instances;
    private static volatile MinecraftServer bootServer;
    public static InstanceManager instances() { return instances; }

    @Override public void onInitialize() {
        NES_CARTRIDGE = ConsoleRegistry.registerCartridge(Identifier.of("retrocraft", "nes"), RomFormats.ALL.get("nes"));
        NES_HANDHELD = ConsoleRegistry.registerConsole(Identifier.of("retrocraft", "nes_handheld"), "NES Handheld", ConsoleType.HANDHELD, NES_CARTRIDGE);
        NES_DOCKED = ConsoleRegistry.registerConsole(Identifier.of("retrocraft", "nes_console"), "NES Console", ConsoleType.DOCKED, NES_CARTRIDGE);
        CONSOLE_SCREEN = Registry.register(Registries.SCREEN_HANDLER, Identifier.of("retrocraft", "console"),
                new ExtendedScreenHandlerType<>(ConsoleScreenHandler::new, ConsoleOpenData.CODEC));
        var snes = ConsoleRegistry.registerCartridge(Identifier.of("retrocraft", "snes"), RomFormats.ALL.get("snes"));
        ConsoleRegistry.registerConsole(Identifier.of("retrocraft", "snes_console"), "SNES Console", ConsoleType.DOCKED, snes);
        registerSystem("gb", "Game Boy", ConsoleType.HANDHELD);
        registerSystem("gbc", "Game Boy Color", ConsoleType.HANDHELD);
        registerSystem("gba", "Game Boy Advance", ConsoleType.HANDHELD);
        registerSystem("ps1", "PlayStation", ConsoleType.DOCKED);
        registerSystem("ps2", "PlayStation 2", ConsoleType.DOCKED);
        registerSystem("dreamcast", "Dreamcast", ConsoleType.DOCKED);
        registerSystem("wii", "Wii", ConsoleType.DOCKED);
        registerSystem("gamecube", "GameCube", ConsoleType.DOCKED);
        var megadrive = registerSystem("megadrive", "Mega Drive", ConsoleType.DOCKED);
        ConsoleRegistry.registerConsole(Identifier.of("retrocraft", "genesis_console"), "Genesis", ConsoleType.DOCKED, megadrive);
        registerSystem("n64", "Nintendo 64", ConsoleType.DOCKED);
        registerSystem("mame", "Arcade Machine", ConsoleType.DOCKED);
        ConsoleRegistry.registerTelevisions();
        CREATIVE_GROUP = Registry.register(Registries.ITEM_GROUP, Identifier.of("retrocraft", "consoles"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.retrocraft.consoles"))
                .icon(() -> new ItemStack(Registries.ITEM.get(NES_HANDHELD.id())))
                .entries((context, entries) -> {
                    for (var console : ConsoleRegistry.consoles()) entries.add(Registries.ITEM.get(console.id()));
                    for (var tv : ConsoleRegistry.televisions()) entries.add(tv.asItem());
                    for (var game : RomCatalog.clientEntries()) {
                        CartridgeItem cartridge = ConsoleRegistry.cartridge(Identifier.of(game.cartridge()));
                        if (cartridge != null) entries.add(cartridge.game(game.gameId(), game.name()));
                    }
                }).build());
        Packets.register();
        HybridPackets.register();
        SettingsPackets.register();
        ServerPlayNetworking.registerGlobalReceiver(HybridPackets.Status.ID, (packet, context) -> { if (instances != null) instances.coreStatus(context.player(), packet); });
        ServerPlayNetworking.registerGlobalReceiver(HybridPackets.Lease.ID, (packet, context) -> { if (instances != null) instances.ownership(context.player(), packet); });
        ServerPlayNetworking.registerGlobalReceiver(HybridPackets.Blob.ID, (packet, context) -> { if (instances != null) instances.upload(context.player(), packet); });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            bootServer = server;
            Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
            Thread.ofVirtual().name("retrocraft-config-load").start(() -> {
                try {
                    RetrocraftConfig config = RetrocraftConfig.load();
                    InstanceManager manager = new InstanceManager(server, config, worldRoot);
                    server.execute(() -> { if (bootServer == server) instances = manager; else manager.close(); });
                } catch (Exception ex) { LOG.error("Retrocraft could not initialize", ex); }
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (instances != null) instances.tick(); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { if (instances != null) instances.sendCatalog(handler.player); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { if (instances != null) instances.disconnect(handler.player.getUuid()); });
        ServerPlayNetworking.registerGlobalReceiver(Packets.Request.ID, (packet, context) -> { if (instances != null) instances.request(context.player(), packet); });
        ServerPlayNetworking.registerGlobalReceiver(SettingsPackets.GuiRequest.ID, (packet, context) -> { if (instances != null) instances.openSettings(context.player()); });
        ServerPlayNetworking.registerGlobalReceiver(SettingsPackets.ConfigApply.ID, (packet, context) -> { if (instances != null) instances.applySettings(context.player(), packet); });
        ServerPlayNetworking.registerGlobalReceiver(SettingsPackets.RomAction.ID, (packet, context) -> { if (instances != null) instances.romAction(context.player(), packet); });
        ServerPlayNetworking.registerGlobalReceiver(SettingsPackets.RomUpload.ID, (packet, context) -> { if (instances != null) instances.romUpload(context.player(), packet); });
        ServerPlayNetworking.registerGlobalReceiver(SettingsPackets.BiosRequest.ID, (packet, context) -> { if (instances != null) instances.requestBios(context.player(), packet); });
        ServerPlayNetworking.registerGlobalReceiver(SettingsPackets.BiosReady.ID, (packet, context) -> { if (instances != null) instances.biosReady(context.player(), packet); });

        ServerPlayNetworking.registerGlobalReceiver(Packets.ThumbnailUpload.ID, (packet, context) -> { if (instances != null) instances.thumbnailUpload(context.player(), packet); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { bootServer = null; if (instances != null) { instances.close(); instances = null; } });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            for (String name : java.util.List.of("retrocraft", "rc")) dispatcher.register(commandTree(name));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> commandTree(String name) {
        var root = CommandManager.literal(name).requires(source -> source.hasPermissionLevel(2));
        root.then(CommandManager.literal("gui").executes(context -> {
            if (instances == null) return 0;
            instances.openSettings(context.getSource().getPlayerOrThrow());
            return 1;
        }));
        root.then(CommandManager.literal("scan").executes(context -> {
            if (instances == null) return 0;
            if (instances.reloadLibrary(context.getSource())) return 1;
            context.getSource().sendError(Text.translatable("message.retrocraft.scan_busy")); return 0;
        }).then(CommandManager.literal("onlynews").executes(context -> {
            if (instances == null) return 0;
            if (instances.reloadLibrary(context.getSource(), true)) return 1;
            context.getSource().sendError(Text.translatable("message.retrocraft.scan_busy")); return 0;
        })));
        root.then(CommandManager.literal("reload").executes(context -> {
            if (instances == null) return 0;
            if (instances.reloadConfig(context.getSource())) { context.getSource().sendFeedback(() -> Text.literal("Retrocraft config reloaded"), true); return 1; }
            context.getSource().sendError(Text.literal("Retrocraft config reload failed; see log")); return 0;
        }));
        var config = CommandManager.literal("config").executes(context -> {
            if (instances == null) return 0;
            var source = context.getSource();
            ConfigKey.keys().sorted().forEach(key -> source.sendFeedback(() -> Text.literal(key + " = " + ConfigKey.get(instances.config(), key)), false));
            return 1;
        });
        var key = CommandManager.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((context, builder) -> { ConfigKey.keys().sorted().filter(k -> k.toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest); return builder.buildFuture(); })
                .executes(context -> {
                    if (instances == null) return 0;
                    String k = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "key");
                    String value = ConfigKey.get(instances.config(), k);
                    if (value == null) { context.getSource().sendError(Text.literal("Unknown config key " + k)); return 0; }
                    context.getSource().sendFeedback(() -> Text.literal(k + " = " + value), false); return 1;
                });
        var value = CommandManager.argument("value", com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((context, builder) -> {
                    String k = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "key");
                    ConfigKey.valueSuggestions(instances == null ? new RetrocraftConfig() : instances.config(), k)
                            .filter(v -> v.toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    if (instances == null) return 0;
                    String k = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "key");
                    String v = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "value");
                    try {
                        ConfigKey.set(instances.config(), k, v);
                        instances.config().save(); instances.applyConfig();
                        context.getSource().sendFeedback(() -> Text.literal(k + " = " + ConfigKey.get(instances.config(), k)), true); return 1;
                    } catch (IllegalArgumentException | java.io.IOException ex) { context.getSource().sendError(Text.literal(ex.getMessage())); return 0; }
                });
        key.then(value); config.then(key); root.then(config);
        return root;
    }
    private static CartridgeItem registerSystem(String id, String name, ConsoleType type) {
        var cartridge = ConsoleRegistry.registerCartridge(Identifier.of("retrocraft", id), RomFormats.ALL.get(id));
        ConsoleRegistry.registerConsole(Identifier.of("retrocraft", id + (type == ConsoleType.HANDHELD ? "_handheld" : "_console")), name, type, cartridge);
        return cartridge;
    }
}
