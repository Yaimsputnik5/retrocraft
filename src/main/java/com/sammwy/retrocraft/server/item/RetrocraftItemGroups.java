package com.sammwy.retrocraft.server.item;

import java.util.function.Supplier;

import com.sammwy.retrocraft.roms.RomCatalog;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.Consoles;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Hardware, one game per catalog ROM, and one permanent cabinet per arcade ROM. */
public final class RetrocraftItemGroups {
    public static ItemGroup CONSOLES, GAMES, ARCADES;

    public static void register() {
        CONSOLES =
                register(
                        "consoles",
                        () -> new ItemStack(Registries.ITEM.get(Consoles.NES_HANDHELD.id())),
                        (context, entries) -> {
                            for (var console : ConsoleRegistry.consoles())
                                if (console.listed())
                                    entries.add(Registries.ITEM.get(console.id()));
                            for (var tv : ConsoleRegistry.televisions()) entries.add(tv.asItem());
                        });
        GAMES =
                register(
                        "games",
                        () -> new ItemStack(Consoles.NES.cartridge()),
                        (context, entries) -> {
                            for (var game : RomCatalog.clientEntries()) {
                                CartridgeItem media =
                                        ConsoleRegistry.media(
                                                Identifier.tryParse(game.cartridge()));
                                if (media != null)
                                    entries.add(media.game(game.gameId(), game.name()));
                            }
                        });
        ARCADES =
                register(
                        "arcades",
                        () -> new ItemStack(Registries.ITEM.get(Consoles.ARCADE_CABINET)),
                        (context, entries) -> {
                            var cabinet = (ArcadeItem) Registries.ITEM.get(Consoles.ARCADE_CABINET);
                            for (var game : RomCatalog.clientEntries()) {
                                CartridgeItem media =
                                        ConsoleRegistry.media(
                                                Identifier.tryParse(game.cartridge()));
                                if (media != null && media.system().arcade())
                                    entries.add(cabinet.game(game.gameId(), game.name()));
                            }
                        });
    }

    private static ItemGroup register(
            String id, Supplier<ItemStack> icon, ItemGroup.EntryCollector entries) {
        return Registry.register(
                Registries.ITEM_GROUP,
                Identifier.of("retrocraft", id),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.retrocraft." + id))
                        .icon(icon)
                        .entries(entries)
                        .build());
    }

    private RetrocraftItemGroups() {}
}
