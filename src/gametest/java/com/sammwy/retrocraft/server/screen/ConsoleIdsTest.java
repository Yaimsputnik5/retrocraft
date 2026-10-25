package com.sammwy.retrocraft.server.screen;

import java.util.Map;

import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;
import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

public class ConsoleIdsTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void consolesAreNamedAfterTheirSystemAndMediaAfterItsKind(TestContext context) {
        var media =
                Map.of(
                        "nes", "nes_cartridge",
                        "gba", "gba_cartridge",
                        "nds", "nds_card",
                        "3ds", "3ds_card",
                        "psp", "psp_umd",
                        "ps2", "ps2_disc",
                        "gamecube", "gamecube_disc",
                        "arcade", "arcade_board");
        media.forEach(
                (system, item) -> {
                    var cartridge = ConsoleRegistry.media(Identifier.of("retrocraft", system));
                    context.assertTrue(cartridge != null, system + " is a registered system");
                    context.assertTrue(
                            Registries.ITEM.getId(cartridge).getPath().equals(item),
                            system + " media is registered as " + item);
                });
        var consoles =
                Map.of(
                        "nes", ConsoleType.DOCKED,
                        "nes_handheld", ConsoleType.HANDHELD,
                        "gba", ConsoleType.HANDHELD,
                        "gba_sp", ConsoleType.HANDHELD,
                        "ps2", ConsoleType.DOCKED,
                        "wii", ConsoleType.DOCKED,
                        "arcade", ConsoleType.ARCADE,
                        "arcade_cabinet", ConsoleType.ARCADE);
        consoles.forEach(
                (id, type) -> {
                    var console = ConsoleRegistry.get(Identifier.of("retrocraft", id));
                    context.assertTrue(
                            console != null && console.type() == type,
                            id + " is a " + type + " console");
                });
        for (var console : ConsoleRegistry.consoles()) {
            String path = console.id().getPath();
            context.assertTrue(
                    !path.endsWith("_console")
                            && !(path.endsWith("_handheld") && !path.equals("nes_handheld")),
                    path + " must not carry a redundant suffix");
            context.assertTrue(
                    Registries.ITEM.get(console.id()) != null
                            && !(Registries.ITEM.get(console.id()) instanceof CartridgeItem),
                    path + " has its own hardware item");
        }
        context.complete();
    }
}
