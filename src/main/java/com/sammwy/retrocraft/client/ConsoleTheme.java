package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.client.config.ControllerProfile;

/** Console shell colours and bezel geometry. Controls live in the client controller profile. */
record ConsoleTheme(String id, Bezel bezel, int shell, int bezelColor, int accent, int screenOff) {
    enum Bezel {
        GB,
        GBC,
        GBA,
        GBA_SP,
        NDS,
        THREEDS,
        NES_HANDHELD,
        TV,
        ARCADE
    }

    private static final int LCD_OFF = 0xff8c9a5e, CRT_OFF = 0xff101218;

    ControllerProfile profile() {
        return ControllerProfile.of(id);
    }

    static ConsoleTheme of(String console) {
        return switch (console) {
            case "gb_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.GB, 0xffc8c4b8, 0xff4a4a5a, 0xff9b2d5f, LCD_OFF);
            case "gbc_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.GBC, 0xff1f8d8a, 0xff2d2d35, 0xff4b3a7a, LCD_OFF);
            case "gba_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.GBA, 0xff584c9e, 0xff1c1b23, 0xff8d85c4, LCD_OFF);
            case "gba_sp_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.GBA_SP, 0xff8193ad, 0xff282c34, 0xffc0c9d4, CRT_OFF);
            case "nds_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.NDS, 0xffd4d6dc, 0xff45484f, 0xff8e969e, CRT_OFF);
            case "3ds_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.THREEDS, 0xff258f9e, 0xff15191e, 0xff68c4cc, CRT_OFF);
            case "nes_handheld" ->
                    new ConsoleTheme(
                            console,
                            Bezel.NES_HANDHELD,
                            0xffc7c5c0,
                            0xff141417,
                            0xffc1392b,
                            LCD_OFF);
            case "snes_console" -> tv(console, 0xffb5b4bb, 0xff4f3f91);
            case "megadrive_console" -> tv(console, 0xff232428, 0xff4e8ad0);
            case "genesis_console" -> tv(console, 0xff232428, 0xffd4a947);
            case "n64_console" -> tv(console, 0xff44464c, 0xffc0392b);
            case "mame_console",
                            "mame_tetris_console",
                            "mame_space_invaders_console",
                            "mame_internal_console" ->
                    new ConsoleTheme(
                            console, Bezel.ARCADE, 0xff2a2831, 0xff0c0c10, 0xfff0b53a, CRT_OFF);
            case "ps1_console" -> tv(console, 0xffb8b8bc, 0xff4a4a5a);
            case "ps2_console" -> tv(console, 0xff1a1a2e, 0xff3a5cbe);
            case "dreamcast_console" -> tv(console, 0xffe8e6e0, 0xfff07f1f);
            case "wii_console" -> tv(console, 0xffdfe4ea, 0xff4e8ad0);
            case "gamecube_console" -> tv(console, 0xff6e5896, 0xff8d85c4);
            default -> tv(console, 0xffc7c5c0, 0xffc1392b);
        };
    }

    private static ConsoleTheme tv(String console, int shell, int accent) {
        return new ConsoleTheme(console, Bezel.TV, shell, 0xff15161a, accent, CRT_OFF);
    }

    boolean handheld() {
        return bezel != Bezel.TV && bezel != Bezel.ARCADE;
    }

    boolean diagonals() {
        return bezel == Bezel.ARCADE
                || id.equals("megadrive_console")
                || id.equals("genesis_console");
    }

    static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * factor)),
                g = Math.min(255, Math.round(((argb >> 8) & 255) * factor)),
                b = Math.min(255, Math.round((argb & 255) * factor));
        return (argb & 0xff000000) | r << 16 | g << 8 | b;
    }
}
