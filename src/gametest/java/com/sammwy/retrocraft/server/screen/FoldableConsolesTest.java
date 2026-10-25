package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

public class FoldableConsolesTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void foldablesHaveIndependentItemsAndTheCorrectCartridgeAndCore(TestContext context) {
        String[][] systems = {
            {"nds", "nds", "desmume"}, {"3ds", "3ds", "citra"}, {"gba_sp", "gba", "mgba"}
        };
        for (String[] system : systems) {
            var definition = ConsoleRegistry.get(Identifier.of("retrocraft", system[0]));
            context.assertTrue(
                    definition != null && definition.type() == ConsoleType.HANDHELD,
                    system[0] + " is registered as a handheld");
            context.assertTrue(
                    Registries.ITEM.get(definition.id()) instanceof HandheldConsoleItem,
                    system[0] + " has a usable console item");
            context.assertTrue(
                    definition.cartridge()
                            == ConsoleRegistry.media(Identifier.of("retrocraft", system[1])),
                    system[0] + " accepts its system's game cards");
            context.assertTrue(
                    KnownCores.compatible("retrocraft:" + system[1]).contains(system[2]),
                    system[0] + " resolves to a trusted compatible core");
        }
        context.assertTrue(
                ConsoleRegistry.get(Identifier.of("retrocraft", "gba")).cartridge()
                        == ConsoleRegistry.get(Identifier.of("retrocraft", "gba_sp")).cartridge(),
                "GBA and GBA SP share existing games and saves");
        context.complete();
    }
}
