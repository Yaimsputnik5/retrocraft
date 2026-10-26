package com.sammwy.retrocraft.server.item;

import java.util.function.Supplier;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.roms.RomCatalog;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.Consoles;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Hardware, one game per catalog ROM, and one permanent cabinet per arcade ROM. */
public final class RetrocraftItemGroups {
    public static Supplier<CreativeModeTab> CONSOLES, GAMES, ARCADES;

    public static void register() {
        CONSOLES =
                register(
                        "consoles",
                        () ->
                                new ItemStack(
                                        ContentProperties.get(
                                                BuiltInRegistries.ITEM,
                                                Consoles.NES_HANDHELD.id())),
                        (context, entries) -> {
                            for (var console : ConsoleRegistry.consoles())
                                if (console.listed())
                                    entries.accept(
                                            ContentProperties.get(
                                                    BuiltInRegistries.ITEM, console.id()));
                            for (var tv : ConsoleRegistry.televisions())
                                entries.accept(tv.asItem());
                            for (var component : ConsoleRegistry.components())
                                entries.accept(component);
                        });
        GAMES =
                register(
                        "games",
                        () -> new ItemStack(Consoles.NES.cartridge()),
                        (context, entries) -> {
                            for (var game : RomCatalog.clientEntries()) {
                                CartridgeItem media =
                                        ConsoleRegistry.media(
                                                ResourceLocation.tryParse(game.cartridge()));
                                if (media != null)
                                    entries.accept(media.game(game.gameId(), game.name()));
                            }
                        });
        ARCADES =
                register(
                        "arcades",
                        () ->
                                new ItemStack(
                                        ContentProperties.get(
                                                BuiltInRegistries.ITEM, Consoles.ARCADE_CABINET)),
                        (context, entries) -> {
                            var cabinet =
                                    (ArcadeItem)
                                            ContentProperties.get(
                                                    BuiltInRegistries.ITEM,
                                                    Consoles.ARCADE_CABINET);
                            for (var game : RomCatalog.clientEntries()) {
                                CartridgeItem media =
                                        ConsoleRegistry.media(
                                                ResourceLocation.tryParse(game.cartridge()));
                                if (media != null && media.system().arcade())
                                    entries.accept(cabinet.game(game.gameId(), game.name()));
                            }
                        });
    }

    private static Supplier<CreativeModeTab> register(
            String id, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator entries) {
        return Platform.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Ids.of("retrocraft", id),
                () ->
                        Platform.creativeTab(
                                Component.translatable("itemGroup.retrocraft." + id),
                                icon,
                                entries));
    }

    private RetrocraftItemGroups() {}
}
