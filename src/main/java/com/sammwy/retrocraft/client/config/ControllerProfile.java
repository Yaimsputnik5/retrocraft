package com.sammwy.retrocraft.client.config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

import com.sammwy.retrocraft.cores.worker.PadInput;

/** Console controls and keyboard defaults, independent of networking and rendering. */
public record ControllerProfile(String id, List<Button> buttons, int sticks) {
    public enum Action {
        B,
        Y,
        SELECT,
        START,
        UP,
        DOWN,
        LEFT,
        RIGHT,
        A,
        X,
        L,
        R,
        L2,
        R2,
        L3,
        R3,
        LEFT_UP,
        LEFT_DOWN,
        LEFT_LEFT,
        LEFT_RIGHT,
        RIGHT_UP,
        RIGHT_DOWN,
        RIGHT_LEFT,
        RIGHT_RIGHT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public int mask() {
            return ordinal() < 16 ? 1 << ordinal() : 0;
        }

        public boolean analog() {
            return ordinal() >= 16;
        }
    }

    public record Button(Action action, String label) {}

    private static Button b(Action action, String label) {
        return new Button(action, label);
    }

    private static final List<Button> TWO = List.of(b(Action.B, "B"), b(Action.A, "A"));
    private static final List<Button> FOUR =
            List.of(b(Action.B, "B"), b(Action.A, "A"), b(Action.Y, "Y"), b(Action.X, "X"));
    private static final List<Button> SHOULDERS = List.of(b(Action.L, "L"), b(Action.R, "R"));
    private static final List<Button> PLAYSTATION =
            List.of(
                    b(Action.B, "cross"),
                    b(Action.A, "circle"),
                    b(Action.Y, "square"),
                    b(Action.X, "triangle"),
                    b(Action.L, "L1"),
                    b(Action.R, "R1"),
                    b(Action.L2, "L2"),
                    b(Action.R2, "R2"),
                    b(Action.L3, "L3"),
                    b(Action.R3, "R3"));

    private static final Map<String, ControllerProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, List<Action>> ACTIONS = new ConcurrentHashMap<>();

    public static ControllerProfile of(String id) {
        return PROFILES.computeIfAbsent(id, ControllerProfile::create);
    }

    private static ControllerProfile create(String id) {
        var buttons = new ArrayList<Button>();
        int sticks = 0;
        switch (id) {
            case "ps1_console", "ps2_console" -> {
                buttons.addAll(PLAYSTATION);
                sticks = 2;
            }
            case "gba_handheld" -> {
                buttons.addAll(TWO);
                buttons.addAll(SHOULDERS);
            }
            case "snes_console" -> {
                buttons.addAll(FOUR);
                buttons.addAll(SHOULDERS);
            }
            case "megadrive_console", "genesis_console" ->
                    buttons.addAll(
                            List.of(
                                    b(Action.Y, "A"),
                                    b(Action.B, "B"),
                                    b(Action.A, "C"),
                                    b(Action.L, "X"),
                                    b(Action.X, "Y"),
                                    b(Action.R, "Z")));
            case "n64_console" -> {
                buttons.addAll(
                        List.of(
                                b(Action.B, "A"),
                                b(Action.Y, "B"),
                                b(Action.L, "L"),
                                b(Action.R, "R"),
                                b(Action.L2, "Z")));
                sticks = 2; // The core maps the right virtual stick to the four C buttons.
            }
            case "dreamcast_console" -> {
                buttons.addAll(FOUR);
                buttons.addAll(SHOULDERS);
                sticks = 1;
            }
            case "gamecube_console" -> {
                buttons.addAll(FOUR);
                buttons.addAll(SHOULDERS);
                buttons.add(b(Action.R2, "Z"));
                sticks = 2;
            }
            case "wii_console" -> {
                buttons.addAll(
                        List.of(
                                b(Action.A, "A"),
                                b(Action.B, "B"),
                                b(Action.X, "1"),
                                b(Action.Y, "2"),
                                b(Action.L, "−"),
                                b(Action.R, "+"),
                                b(Action.L2, "C"),
                                b(Action.R2, "Z")));
                sticks = 1;
            }
            case "mame_console",
                            "mame_tetris_console",
                            "mame_space_invaders_console",
                            "mame_internal_console" ->
                    buttons.addAll(
                            List.of(
                                    b(Action.B, "1"),
                                    b(Action.Y, "2"),
                                    b(Action.A, "3"),
                                    b(Action.X, "4"),
                                    b(Action.L, "5"),
                                    b(Action.R, "6")));
            default -> buttons.addAll(TWO);
        }
        buttons.add(b(Action.START, "Start"));
        if (!Set.of("n64_console", "dreamcast_console", "gamecube_console").contains(id))
            buttons.add(
                    b(
                            Action.SELECT,
                            id.startsWith("mame_")
                                    ? "Coin"
                                    : id.equals("wii_console")
                                            ? "Home"
                                            : id.equals("megadrive_console")
                                                            || id.equals("genesis_console")
                                                    ? "Mode"
                                                    : "Select"));
        return new ControllerProfile(id, List.copyOf(buttons), sticks);
    }

    public boolean playStation() {
        return id.equals("ps1_console") || id.equals("ps2_console");
    }

    public List<Action> actions() {
        return ACTIONS.computeIfAbsent(id, ignored -> buildActions());
    }

    private List<Action> buildActions() {
        var actions = new ArrayList<>(List.of(Action.UP, Action.DOWN, Action.LEFT, Action.RIGHT));
        for (var button : buttons) actions.add(button.action());
        if (sticks > 0)
            actions.addAll(
                    List.of(Action.LEFT_UP, Action.LEFT_DOWN, Action.LEFT_LEFT, Action.LEFT_RIGHT));
        if (sticks > 1)
            actions.addAll(
                    List.of(
                            Action.RIGHT_UP,
                            Action.RIGHT_DOWN,
                            Action.RIGHT_LEFT,
                            Action.RIGHT_RIGHT));
        return List.copyOf(actions);
    }

    public String label(Action action) {
        for (var button : buttons) if (button.action() == action) return button.label();
        return action.id();
    }

    /** GLFW key codes. Modern pads leave WASD and IJKL free for independent sticks. */
    public int defaultKey(Action action) {
        return switch (action) {
            case UP -> 265;
            case DOWN -> 264;
            case LEFT -> 263;
            case RIGHT -> 262;
            case START -> 257;
            case SELECT -> 344;
            case B -> 90;
            case A -> 88;
            case Y -> sticks > 0 ? 67 : 65;
            case X -> sticks > 0 ? 86 : 83;
            case L -> 81;
            case R -> sticks > 0 ? 69 : 87;
            case L2 -> 49;
            case R2 -> 51;
            case L3 -> 340;
            case R3 -> 345;
            case LEFT_UP -> 87;
            case LEFT_DOWN -> 83;
            case LEFT_LEFT -> 65;
            case LEFT_RIGHT -> 68;
            case RIGHT_UP -> 73;
            case RIGHT_DOWN -> 75;
            case RIGHT_LEFT -> 74;
            case RIGHT_RIGHT -> 76;
        };
    }

    public int key(Map<String, Integer> overrides, Action action) {
        return overrides.getOrDefault(action.id(), defaultKey(action));
    }

    public PadInput sample(Map<String, Integer> overrides, IntPredicate pressed) {
        int buttons = 0;
        for (Action action : actions())
            if (!action.analog() && held(overrides, action, pressed)) buttons |= action.mask();
        // Opposite directions cancel rather than depending on key event order.
        if ((buttons & 0x30) == 0x30) buttons &= ~0x30;
        if ((buttons & 0xc0) == 0xc0) buttons &= ~0xc0;
        return new PadInput(
                buttons,
                axis(overrides, Action.LEFT_LEFT, Action.LEFT_RIGHT, pressed, sticks > 0),
                axis(overrides, Action.LEFT_UP, Action.LEFT_DOWN, pressed, sticks > 0),
                axis(overrides, Action.RIGHT_LEFT, Action.RIGHT_RIGHT, pressed, sticks > 1),
                axis(overrides, Action.RIGHT_UP, Action.RIGHT_DOWN, pressed, sticks > 1));
    }

    private boolean held(Map<String, Integer> overrides, Action action, IntPredicate pressed) {
        int key = key(overrides, action);
        return key != -1 && pressed.test(key);
    }

    private short axis(
            Map<String, Integer> overrides,
            Action negative,
            Action positive,
            IntPredicate pressed,
            boolean enabled) {
        if (!enabled) return 0;
        return (short)
                (((held(overrides, positive, pressed) ? 1 : 0)
                                - (held(overrides, negative, pressed) ? 1 : 0))
                        * 32767);
    }
}
