package com.sammwy.retrocraft.client.hud;

import com.sammwy.retrocraft.cores.stream.DualScreen;

import net.minecraft.util.Identifier;

/** Built-in client registrations. Texture artwork lives under textures/gui/console_hud/. */
public final class DefaultConsoleHuds {
    public static void register() {
        single("gb_handheld", 160, 144, 0xff8c9a5e, new HudInsets(22, 14, 12, 12));
        single("gbc_handheld", 160, 144, 0xff8c9a5e, new HudInsets(22, 14, 12, 12));
        single("gba_handheld", 240, 160, 0xff8c9a5e, new HudInsets(12, 14, 12, 14));
        single("gba_sp_handheld", 240, 160, 0xff101218, new HudInsets(14, 16, 14, 20));
        single("psp_handheld", 480, 272, 0xff101218, new HudInsets(12, 12, 12, 12));
        single("nes_handheld", 256, 240, 0xff8c9a5e, new HudInsets(12, 12, 12, 14));
        dual("nds_handheld", DualScreen.NDS);
        dual("3ds_handheld", DualScreen.THREEDS);
    }

    private static void single(
            String console, int width, int height, int screenOff, HudInsets border) {
        register(console, ConsoleHudConfig.single(width, height, screenOff), border);
    }

    private static void dual(String console, DualScreen screens) {
        register(
                console,
                new ConsoleHudConfig(
                        screens.width,
                        screens.screenHeight * 2,
                        0xff101218,
                        screens,
                        "retrocraft:" + console),
                new HudInsets(14, 14, 14, 14));
    }

    private static void register(String console, ConsoleHudConfig config, HudInsets border) {
        String path = "textures/gui/console_hud/" + console + "/";
        var frame =
                new HudFrame(
                        Identifier.of("retrocraft", path + "background.png"),
                        Identifier.of("retrocraft", path + "overlay.png"),
                        64,
                        64,
                        border);
        ConsoleHudRegistry.registerDefault(
                Identifier.of("retrocraft", console),
                new TexturedConsoleHudRenderer(config, frame));
    }

    private DefaultConsoleHuds() {}
}
