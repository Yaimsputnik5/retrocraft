package com.sammwy.retrocraft.server.console;

import java.util.*;

import com.sammwy.retrocraft.server.console.builtin.*;

import net.minecraft.util.Identifier;

/**
 * Every built-in console. A console's ID is its system's name; only alternative hardware of the
 * same system gets a suffix (e.g. {@code nes_handheld}). Instances are plain declarations, so core
 * mappings and ROM formats can be read before (and without) game registration.
 */
public final class Consoles {
    public static final Identifier ARCADE_CABINET = Identifier.of("retrocraft", "arcade_cabinet");
    public static final ConsoleDefinition NES = new NesConsole(),
            NES_HANDHELD = new NesHandheldConsole();

    public static final List<ConsoleDefinition> ALL =
            List.of(
                    NES,
                    NES_HANDHELD,
                    new SnesConsole(),
                    new GameBoyConsole(),
                    new GameBoyColorConsole(),
                    new GameBoyAdvanceConsole(),
                    new GameBoyAdvanceSpConsole(),
                    new NintendoDsConsole(),
                    new Nintendo3dsConsole(),
                    new PspConsole(),
                    new PlayStationConsole(),
                    new PlayStation2Console(),
                    new DreamcastConsole(),
                    new WiiConsole(),
                    new GameCubeConsole(),
                    new MegaDriveConsole(),
                    new GenesisConsole(),
                    new Nintendo64Console(),
                    new ArcadeMachine("arcade", "Arcade Machine", true, false),
                    new ArcadeMachine("arcade_tetris", "Tetris Arcade Machine", false, false),
                    new ArcadeMachine(
                            "arcade_space_invaders", "Space Invaders Arcade Machine", false, false),
                    new ArcadeMachine(ARCADE_CABINET.getPath(), "Arcade Machine", false, true));

    /** Distinct systems in console order. */
    public static List<GameSystem> systems() {
        Map<Identifier, GameSystem> systems = new LinkedHashMap<>();
        for (var console : ALL) systems.putIfAbsent(console.system().id(), console.system());
        return List.copyOf(systems.values());
    }

    public static void register() {
        for (var console : ALL) ConsoleRegistry.register(console);
        ConsoleRegistry.registerTelevisions();
    }

    private Consoles() {}
}
