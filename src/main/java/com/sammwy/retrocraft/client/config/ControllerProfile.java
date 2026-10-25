package com.sammwy.retrocraft.client.config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

import com.sammwy.retrocraft.cores.worker.PadInput;

/**
 * Console controls and keyboard defaults, independent of networking and rendering. {@code id} is
 * the console ID, so bindings stay per console even when two consoles share a pad.
 */
public record ControllerProfile(
        String id,
        List<Button> buttons,
        int sticks,
        FaceLayout layout,
        String leftStick,
        String rightStick) {
    /** How the player HUD arranges the face and shoulder buttons. */
    public enum FaceLayout {
        ROW,
        GBA,
        SEGA,
        N64,
        ARCADE,
        DIAMOND,
        DIAMOND_WIDE,
        TRIGGERS
    }

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

    private static List<Button> concat(List<Button> first, List<Button> second, Button... rest) {
        var all = new ArrayList<>(first);
        all.addAll(second);
        all.addAll(List.of(rest));
        return List.copyOf(all);
    }

    /** {@code select} is the label of the Select action, or null when the pad has none. */
    private record Pad(
            List<Button> buttons,
            int sticks,
            FaceLayout layout,
            String select,
            String leftStick,
            String rightStick) {
        Pad(List<Button> buttons, FaceLayout layout) {
            this(buttons, 0, layout, "Select", "L", "R");
        }

        Pad sticks(int count) {
            return new Pad(buttons, count, layout, select, leftStick, rightStick);
        }

        Pad select(String label) {
            return new Pad(buttons, sticks, layout, label, leftStick, rightStick);
        }

        Pad stickLabels(String left, String right) {
            return new Pad(buttons, sticks, layout, select, left, right);
        }
    }

    private static final Pad DEFAULT = new Pad(TWO, FaceLayout.ROW);
    private static final Map<String, Pad> PADS = new HashMap<>();

    static {
        var gba = new Pad(concat(TWO, SHOULDERS), FaceLayout.GBA);
        var sega =
                new Pad(
                                List.of(
                                        b(Action.Y, "A"),
                                        b(Action.B, "B"),
                                        b(Action.A, "C"),
                                        b(Action.L, "X"),
                                        b(Action.X, "Y"),
                                        b(Action.R, "Z")),
                                FaceLayout.SEGA)
                        .select("Mode");
        var playStation =
                new Pad(PLAYSTATION, FaceLayout.TRIGGERS).sticks(2).stickLabels("L3", "R3");
        var arcade =
                new Pad(
                                List.of(
                                        b(Action.B, "1"),
                                        b(Action.Y, "2"),
                                        b(Action.A, "3"),
                                        b(Action.X, "4"),
                                        b(Action.L, "5"),
                                        b(Action.R, "6")),
                                FaceLayout.ARCADE)
                        .select("Coin");
        pad(gba, "gba", "gba_sp");
        pad(sega, "megadrive", "genesis");
        pad(playStation, "ps1", "ps2");
        pad(arcade, "arcade", "arcade_tetris", "arcade_space_invaders", "arcade_cabinet");
        pad(new Pad(concat(FOUR, SHOULDERS), FaceLayout.DIAMOND), "snes");
        pad(
                new Pad(concat(PLAYSTATION.subList(0, 4), SHOULDERS), FaceLayout.DIAMOND).sticks(1),
                "psp");
        pad(new Pad(concat(FOUR, SHOULDERS, b(Action.L3, "Mic")), FaceLayout.DIAMOND_WIDE), "nds");
        pad(
                new Pad(
                                concat(FOUR, SHOULDERS, b(Action.L2, "ZL"), b(Action.R2, "ZR")),
                                FaceLayout.DIAMOND_WIDE)
                        .sticks(2),
                "3ds");
        // The core maps the right virtual stick to the four C buttons.
        pad(
                new Pad(
                                List.of(
                                        b(Action.B, "A"),
                                        b(Action.Y, "B"),
                                        b(Action.L, "L"),
                                        b(Action.R, "R"),
                                        b(Action.L2, "Z")),
                                FaceLayout.N64)
                        .sticks(2)
                        .select(null)
                        .stickLabels("L", "C"),
                "n64");
        pad(
                new Pad(concat(FOUR, SHOULDERS), FaceLayout.TRIGGERS).sticks(1).select(null),
                "dreamcast");
        pad(
                new Pad(concat(FOUR, SHOULDERS, b(Action.R2, "Z")), FaceLayout.TRIGGERS)
                        .sticks(2)
                        .select(null),
                "gamecube");
        pad(
                new Pad(
                                List.of(
                                        b(Action.A, "A"),
                                        b(Action.B, "B"),
                                        b(Action.X, "1"),
                                        b(Action.Y, "2"),
                                        b(Action.L, "−"),
                                        b(Action.R, "+"),
                                        b(Action.L2, "C"),
                                        b(Action.R2, "Z")),
                                FaceLayout.TRIGGERS)
                        .sticks(1)
                        .select("Home"),
                "wii");
    }

    private static void pad(Pad pad, String... consoles) {
        for (String console : consoles) PADS.put(console, pad);
    }

    private static final Map<String, ControllerProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, List<Action>> ACTIONS = new ConcurrentHashMap<>();

    public static ControllerProfile of(String id) {
        return PROFILES.computeIfAbsent(id, ControllerProfile::create);
    }

    private static ControllerProfile create(String id) {
        Pad pad = PADS.getOrDefault(id, DEFAULT);
        var buttons = new ArrayList<>(pad.buttons());
        buttons.add(b(Action.START, "Start"));
        if (pad.select() != null) buttons.add(b(Action.SELECT, pad.select()));
        return new ControllerProfile(
                id,
                List.copyOf(buttons),
                pad.sticks(),
                pad.layout(),
                pad.leftStick(),
                pad.rightStick());
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
