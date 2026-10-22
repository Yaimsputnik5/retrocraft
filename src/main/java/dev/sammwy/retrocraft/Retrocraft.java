package dev.sammwy.retrocraft;

import dev.sammwy.retrocraft.block.*;
import dev.sammwy.retrocraft.config.RetrocraftConfig;
import dev.sammwy.retrocraft.console.*;
import dev.sammwy.retrocraft.item.CartridgeItem;
import dev.sammwy.retrocraft.net.Packets;
import dev.sammwy.retrocraft.screen.*;
import dev.sammwy.retrocraft.server.*;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.*;
import java.util.Set;

public final class Retrocraft implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("retrocraft");
    public static CartridgeItem NES_CARTRIDGE;
    public static ConsoleDefinition NES_HANDHELD, NES_DOCKED;
    public static ScreenHandlerType<ConsoleScreenHandler> CONSOLE_SCREEN;
    public static ItemGroup CREATIVE_GROUP;
    private static InstanceManager instances;
    public static InstanceManager instances() { return instances; }

    @Override public void onInitialize() {
        NES_CARTRIDGE = ConsoleRegistry.registerCartridge(Identifier.of("retrocraft", "nes"), Set.of("nes"));
        NES_HANDHELD = ConsoleRegistry.registerConsole(Identifier.of("retrocraft", "nes_handheld"), "NES Handheld", ConsoleType.HANDHELD, NES_CARTRIDGE);
        NES_DOCKED = ConsoleRegistry.registerConsole(Identifier.of("retrocraft", "nes_console"), "NES Console", ConsoleType.DOCKED, NES_CARTRIDGE);
        CONSOLE_SCREEN = Registry.register(Registries.SCREEN_HANDLER, Identifier.of("retrocraft", "console"),
                new ExtendedScreenHandlerType<>(ConsoleScreenHandler::new, ConsoleOpenData.CODEC));
        CREATIVE_GROUP = Registry.register(Registries.ITEM_GROUP, Identifier.of("retrocraft", "consoles"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.retrocraft.consoles"))
                .icon(() -> new ItemStack(Registries.ITEM.get(NES_HANDHELD.id())))
                .entries((context, entries) -> {
                    for (var console : ConsoleRegistry.consoles()) entries.add(Registries.ITEM.get(console.id()));
                    for (var game : RomCatalog.clientEntries()) {
                        CartridgeItem cartridge = ConsoleRegistry.cartridge(Identifier.of(game.cartridge()));
                        if (cartridge != null) entries.add(cartridge.game(game.gameId(), game.name()));
                    }
                }).build());
        Packets.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { instances = new InstanceManager(server, RetrocraftConfig.load()); }
            catch (Exception ex) { LOG.error("Retrocraft could not initialize", ex); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (instances != null) instances.tick(); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { if (instances != null) instances.sendCatalog(handler.player); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { if (instances != null) instances.disconnect(handler.player.getUuid()); });
        ServerPlayNetworking.registerGlobalReceiver(Packets.Request.ID, (packet, context) -> { if (instances != null) instances.request(context.player(), packet); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (instances != null) { instances.close(); instances = null; } });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(CommandManager.literal("retrocraft")
                .then(CommandManager.literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(context -> {
                    if (instances == null) return 0;
                    try { instances.reloadLibrary(); context.getSource().sendFeedback(() -> Text.translatable("message.retrocraft.reloaded"), false); return 1; }
                    catch (Exception ex) { LOG.error("Cannot reload ROM catalog", ex); return 0; }
                }))));
    }
}
