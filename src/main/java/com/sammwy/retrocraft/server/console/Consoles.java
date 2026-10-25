package com.sammwy.retrocraft.server.console;

import static com.sammwy.retrocraft.server.console.MediaKind.*;

import com.sammwy.retrocraft.roms.discovery.RomFormats;
import com.sammwy.retrocraft.server.console.ConsoleDefinition.Bounds;
import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.minecraft.util.Identifier;

/**
 * Every built-in system and console. A console's ID is its system's name; only alternative hardware
 * of the same system gets a suffix (e.g. {@code nes_handheld}).
 */
public final class Consoles {
    public static final Identifier ARCADE_CABINET = id("arcade_cabinet");
    private static final Bounds CABINET = new Bounds(-4.05, -3.15, 20.05, 19.05, 48);
    public static ConsoleDefinition NES, NES_HANDHELD;

    public static void register() {
        var nes = system("nes", CARTRIDGE);
        NES = docked("nes", "NES Console", nes, new Bounds(1, 1.79, 15, 14, 5.85));
        NES_HANDHELD = handheld("nes_handheld", "NES Handheld", nes);
        docked(
                "snes",
                "SNES Console",
                system("snes", CARTRIDGE),
                new Bounds(1, 2.43, 15, 13.5, 5.74));
        handheld("gb", "Game Boy", system("gb", CARTRIDGE));
        handheld("gbc", "Game Boy Color", system("gbc", CARTRIDGE));
        var gba = system("gba", CARTRIDGE);
        handheld("gba", "Game Boy Advance", gba);
        handheld("gba_sp", "Game Boy Advance SP", gba);
        handheld("nds", "Nintendo DS", system("nds", CARD));
        handheld("3ds", "Nintendo 3DS", system("3ds", CARD));
        handheld("psp", "PlayStation Portable (PSP)", system("psp", UMD));
        docked("ps1", "PlayStation", system("ps1", DISC), new Bounds(1, 2.42, 15, 13.5, 3.94));
        docked("ps2", "PlayStation 2", system("ps2", DISC), new Bounds(1, 2.01, 15, 13.5, 4.69));
        docked(
                "dreamcast",
                "Dreamcast",
                system("dreamcast", DISC),
                new Bounds(1.35, 2.42, 14.4, 13.5, 4.22));
        docked("wii", "Wii", system("wii", DISC), new Bounds(5.15, 2.1, 10.85, 14.3, 14.39));
        docked(
                "gamecube",
                "GameCube",
                system("gamecube", DISC),
                new Bounds(2.35, 2.36, 13.65, 15.2, 9.35));
        var megadrive = system("megadrive", CARTRIDGE);
        var sega = new Bounds(1, 2.43, 15, 13.5, 4.39);
        docked("megadrive", "Mega Drive", megadrive, sega);
        docked("genesis", "Genesis", megadrive, sega);
        docked("n64", "Nintendo 64", system("n64", CARTRIDGE), new Bounds(1, 2.43, 15, 13.5, 5.24));
        var arcade = system("arcade", BOARD);
        arcade("arcade", "Arcade Machine", arcade, true, false);
        arcade("arcade_tetris", "Tetris Arcade Machine", arcade, false, false);
        arcade("arcade_space_invaders", "Space Invaders Arcade Machine", arcade, false, false);
        arcade(ARCADE_CABINET.getPath(), "Arcade Machine", arcade, false, true);
        ConsoleRegistry.registerTelevisions();
    }

    private static Identifier id(String path) {
        return Identifier.of("retrocraft", path);
    }

    private static CartridgeItem system(String id, MediaKind media) {
        return ConsoleRegistry.registerSystem(
                new GameSystem(id(id), media, RomFormats.ALL.get(id)));
    }

    private static ConsoleDefinition handheld(String id, String name, CartridgeItem media) {
        return ConsoleRegistry.registerConsole(
                new ConsoleDefinition(
                        id(id), name, ConsoleType.HANDHELD, media, null, true, false));
    }

    private static ConsoleDefinition docked(
            String id, String name, CartridgeItem media, Bounds bounds) {
        return ConsoleRegistry.registerConsole(
                new ConsoleDefinition(
                        id(id), name, ConsoleType.DOCKED, media, bounds, true, false));
    }

    private static void arcade(
            String id, String name, CartridgeItem board, boolean listed, boolean internalRom) {
        ConsoleRegistry.registerConsole(
                new ConsoleDefinition(
                        id(id), name, ConsoleType.ARCADE, board, CABINET, listed, internalRom));
    }

    private Consoles() {}
}
