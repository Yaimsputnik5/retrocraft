package com.sammwy.retrocraft.client.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sammwy.retrocraft.client.config.ControllerProfile;
import com.sammwy.retrocraft.client.config.ControllerProfile.FaceLayout;

/** Console shell colours and bezel geometry. Controls live in the client controller profile. */
record ConsoleTheme(String id, Bezel bezel, int shell, int bezelColor, int accent, int screenOff) {
    enum Bezel {
        GB,
        GBC,
        GBA,
        GBA_SP,
        PSP,
        NDS,
        THREEDS,
        NES_HANDHELD,
        TV,
        ARCADE
    }

    private static final int LCD_OFF = 0xff8c9a5e, CRT_OFF = 0xff101218;
    private static final Map<String, ConsoleTheme> THEMES = new HashMap<>();

    static {
        shell("gb", Bezel.GB, 0xffc8c4b8, 0xff4a4a5a, 0xff9b2d5f, LCD_OFF);
        shell("gbc", Bezel.GBC, 0xff1f8d8a, 0xff2d2d35, 0xff4b3a7a, LCD_OFF);
        shell("gba", Bezel.GBA, 0xff584c9e, 0xff1c1b23, 0xff8d85c4, LCD_OFF);
        shell("gba_sp", Bezel.GBA_SP, 0xff8193ad, 0xff282c34, 0xffc0c9d4, CRT_OFF);
        shell("nds", Bezel.NDS, 0xffd4d6dc, 0xff45484f, 0xff8e969e, CRT_OFF);
        shell("3ds", Bezel.THREEDS, 0xff258f9e, 0xff15191e, 0xff68c4cc, CRT_OFF);
        shell("psp", Bezel.PSP, 0xff24262d, 0xff101216, 0xffbdc2c9, CRT_OFF);
        shell("nes_handheld", Bezel.NES_HANDHELD, 0xffc7c5c0, 0xff141417, 0xffc1392b, LCD_OFF);
        tv("snes", 0xffb5b4bb, 0xff4f3f91);
        tv("megadrive", 0xff232428, 0xff4e8ad0);
        tv("genesis", 0xff232428, 0xffd4a947);
        tv("n64", 0xff44464c, 0xffc0392b);
        tv("ps1", 0xffb8b8bc, 0xff4a4a5a);
        tv("ps2", 0xff1a1a2e, 0xff3a5cbe);
        tv("dreamcast", 0xffe8e6e0, 0xfff07f1f);
        tv("wii", 0xffdfe4ea, 0xff4e8ad0);
        tv("gamecube", 0xff6e5896, 0xff8d85c4);
        for (String id :
                List.of("arcade", "arcade_tetris", "arcade_space_invaders", "arcade_cabinet"))
            shell(id, Bezel.ARCADE, 0xff2a2831, 0xff0c0c10, 0xfff0b53a, CRT_OFF);
    }

    private static void shell(
            String id, Bezel bezel, int shell, int bezelColor, int accent, int screenOff) {
        THEMES.put(id, new ConsoleTheme(id, bezel, shell, bezelColor, accent, screenOff));
    }

    private static void tv(String id, int shell, int accent) {
        THEMES.put(id, tvTheme(id, shell, accent));
    }

    private static ConsoleTheme tvTheme(String id, int shell, int accent) {
        return new ConsoleTheme(id, Bezel.TV, shell, 0xff15161a, accent, CRT_OFF);
    }

    ControllerProfile profile() {
        return ControllerProfile.of(id);
    }

    static ConsoleTheme of(String console) {
        ConsoleTheme theme = THEMES.get(console);
        return theme != null ? theme : tvTheme(console, 0xffc7c5c0, 0xffc1392b);
    }

    boolean handheld() {
        return bezel != Bezel.TV && bezel != Bezel.ARCADE;
    }

    /** 8-way arcade sticks and Sega pads light the d-pad corners. */
    boolean diagonals() {
        var layout = profile().layout();
        return layout == FaceLayout.ARCADE || layout == FaceLayout.SEGA;
    }

    static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * factor)),
                g = Math.min(255, Math.round(((argb >> 8) & 255) * factor)),
                b = Math.min(255, Math.round((argb & 255) * factor));
        return (argb & 0xff000000) | r << 16 | g << 8 | b;
    }
}
