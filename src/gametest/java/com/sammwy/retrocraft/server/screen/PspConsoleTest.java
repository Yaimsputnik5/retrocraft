package com.sammwy.retrocraft.server.screen;

import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

public class PspConsoleTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pspUsesItsOwnUmdAndPortableCore(TestContext context) {
        var id = Identifier.of("retrocraft", "psp");
        var console = ConsoleRegistry.get(id);
        context.assertTrue(
                console != null && console.type() == ConsoleType.HANDHELD, "PSP is a handheld");
        context.assertTrue(
                Registries.ITEM.get(id) instanceof HandheldConsoleItem, "PSP has a usable item");
        context.assertTrue(
                console.cartridge() == ConsoleRegistry.media(Identifier.of("retrocraft", "psp")),
                "PSP accepts UMD games");
        context.assertTrue(!console.usesTelevision(), "PSP uses its own screen");
        context.assertTrue(
                KnownCores.defaultForCartridge("retrocraft:psp").equals("ppsspp"),
                "PSP resolves to PPSSPP");
        context.complete();
    }
}
