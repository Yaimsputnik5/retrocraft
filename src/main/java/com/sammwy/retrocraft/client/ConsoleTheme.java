package com.sammwy.retrocraft.client;

import java.util.List;

/**
 * Per-console look of the play screen: the style of the slim bezel drawn around the video, the
 * accent palette and the on-screen legend of the RetroPad bindings.
 */
record ConsoleTheme(
        String id,
        Bezel bezel,
        int shell,
        int bezelColor,
        int accent,
        int screenOff,
        List<Binding> bindings) {
    /**
     * One legend entry: RetroPad bit mask (as sent by {@link InputControls}), keyboard key and
     * console button name.
     */
    record Binding(int mask, String key, String label) {}

    enum Bezel {
        GB,
        GBC,
        GBA,
        NES_HANDHELD,
        TV,
        ARCADE
    }

    private static final int DPAD = 0xf0,
            B = 1,
            Y = 1 << 1,
            SELECT = 1 << 2,
            START = 1 << 3,
            A = 1 << 8,
            X = 1 << 9,
            L = 1 << 10,
            R = 1 << 11,
            L2 = 1 << 12;
    private static final Binding ARROWS = new Binding(DPAD, "Arrows", "D-pad");
    private static final Binding START_KEY = new Binding(START, "Enter", "Start");
    private static final int LCD_OFF = 0xff8c9a5e, CRT_OFF = 0xff101218;

    static ConsoleTheme of(String console) {
        List<Binding> two =
                List.of(
                        ARROWS,
                        new Binding(B, "Z", "B"),
                        new Binding(A, "X", "A"),
                        START_KEY,
                        new Binding(SELECT, "R-Shift", "Select"));
        return switch (console) {
            case "gb_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.GB, 0xffc8c4b8, 0xff4a4a5a, 0xff9b2d5f, LCD_OFF, two);
            case "gbc_handheld" ->
                    new ConsoleTheme(
                            console, Bezel.GBC, 0xff1f8d8a, 0xff2d2d35, 0xff4b3a7a, LCD_OFF, two);
            case "gba_handheld" ->
                    new ConsoleTheme(
                            console,
                            Bezel.GBA,
                            0xff584c9e,
                            0xff1c1b23,
                            0xff8d85c4,
                            LCD_OFF,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "B"),
                                    new Binding(A, "X", "A"),
                                    new Binding(L, "Q", "L"),
                                    new Binding(R, "W", "R"),
                                    START_KEY,
                                    new Binding(SELECT, "R-Shift", "Select")));
            case "nes_handheld" ->
                    new ConsoleTheme(
                            console,
                            Bezel.NES_HANDHELD,
                            0xffc7c5c0,
                            0xff141417,
                            0xffc1392b,
                            LCD_OFF,
                            two);
            case "snes_console" ->
                    tv(
                            console,
                            0xffb5b4bb,
                            0xff4f3f91,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "B"),
                                    new Binding(A, "X", "A"),
                                    new Binding(Y, "A", "Y"),
                                    new Binding(X, "S", "X"),
                                    new Binding(L, "Q", "L"),
                                    new Binding(R, "W", "R"),
                                    START_KEY,
                                    new Binding(SELECT, "R-Shift", "Select")));
            case "megadrive_console", "genesis_console" ->
                    tv(
                            console,
                            0xff232428,
                            console.startsWith("genesis") ? 0xffd4a947 : 0xff4e8ad0,
                            List.of(
                                    ARROWS,
                                    new Binding(Y, "A", "A"),
                                    new Binding(B, "Z", "B"),
                                    new Binding(A, "X", "C"),
                                    new Binding(L, "Q", "X"),
                                    new Binding(X, "S", "Y"),
                                    new Binding(R, "W", "Z"),
                                    START_KEY,
                                    new Binding(SELECT, "R-Shift", "Mode")));
            case "n64_console" ->
                    tv(
                            console,
                            0xff44464c,
                            0xffc0392b,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "B"),
                                    new Binding(A, "X", "A"),
                                    new Binding(L, "Q", "L"),
                                    new Binding(R, "W", "R"),
                                    new Binding(L2, "I", "Z"),
                                    START_KEY));
            case "mame_console", "mame_tetris_console", "mame_space_invaders_console" ->
                    new ConsoleTheme(
                            console,
                            Bezel.ARCADE,
                            0xff2a2831,
                            0xff0c0c10,
                            0xfff0b53a,
                            CRT_OFF,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "1"),
                                    new Binding(Y, "A", "2"),
                                    new Binding(A, "X", "3"),
                                    new Binding(X, "S", "4"),
                                    new Binding(L, "Q", "5"),
                                    new Binding(R, "W", "6"),
                                    START_KEY,
                                    new Binding(SELECT, "R-Shift", "Coin")));
            case "ps1_console" ->
                    tv(
                            console,
                            0xffb8b8bc,
                            0xff4a4a5a,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "O"),
                                    new Binding(A, "X", "X"),
                                    new Binding(Y, "A", "S"),
                                    new Binding(X, "S", "T"),
                                    new Binding(L, "Q", "L1"),
                                    new Binding(R, "W", "R1"),
                                    new Binding(L2, "I", "L2"),
                                    START_KEY,
                                    new Binding(SELECT, "R-Shift", "Select")));
            case "ps2_console" ->
                    tv(
                            console,
                            0xff1a1a2e,
                            0xff3a5cbe,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "O"),
                                    new Binding(A, "X", "X"),
                                    new Binding(Y, "A", "S"),
                                    new Binding(X, "S", "T"),
                                    new Binding(L, "Q", "L1"),
                                    new Binding(R, "W", "R1"),
                                    new Binding(L2, "I", "L2"),
                                    START_KEY,
                                    new Binding(SELECT, "R-Shift", "Select")));
            case "dreamcast_console" ->
                    tv(
                            console,
                            0xffe8e6e0,
                            0xfff07f1f,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "B"),
                                    new Binding(A, "X", "A"),
                                    new Binding(Y, "A", "Y"),
                                    new Binding(X, "S", "X"),
                                    new Binding(L, "Q", "L"),
                                    new Binding(R, "W", "R"),
                                    START_KEY));
            case "wii_console" ->
                    tv(
                            console,
                            0xffdfe4ea,
                            0xff4e8ad0,
                            List.of(
                                    ARROWS,
                                    new Binding(A, "X", "A"),
                                    new Binding(B, "Z", "B"),
                                    new Binding(X, "S", "1"),
                                    new Binding(Y, "A", "2"),
                                    new Binding(L, "Q", "-"),
                                    new Binding(R, "W", "+"),
                                    START_KEY,
                                    new Binding(SELECT, "H", "Home")));
            case "gamecube_console" ->
                    tv(
                            console,
                            0xff6e5896,
                            0xff8d85c4,
                            List.of(
                                    ARROWS,
                                    new Binding(B, "Z", "B"),
                                    new Binding(A, "X", "A"),
                                    new Binding(Y, "A", "Y"),
                                    new Binding(X, "S", "X"),
                                    new Binding(L, "Q", "L"),
                                    new Binding(R, "W", "R"),
                                    START_KEY));
            default -> tv(console, 0xffc7c5c0, 0xffc1392b, two);
        };
    }

    private static ConsoleTheme tv(String console, int shell, int accent, List<Binding> bindings) {
        return new ConsoleTheme(console, Bezel.TV, shell, 0xff15161a, accent, CRT_OFF, bindings);
    }

    boolean handheld() {
        return bezel != Bezel.TV && bezel != Bezel.ARCADE;
    }

    /**
     * Eight-way pads (arcade sticks, the N64 stick, Sega's round d-pad) light diagonal cells on the
     * HUD cross.
     */
    boolean diagonals() {
        return bezel == Bezel.ARCADE
                || id.endsWith("n64_console")
                || id.endsWith("megadrive_console")
                || id.endsWith("genesis_console");
    }

    /**
     * Darkened/lightened variant of an ARGB colour, used for panels derived from the shell colour.
     */
    static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * factor)),
                g = Math.min(255, Math.round(((argb >> 8) & 255) * factor)),
                b = Math.min(255, Math.round((argb & 255) * factor));
        return (argb & 0xff000000) | r << 16 | g << 8 | b;
    }
}
