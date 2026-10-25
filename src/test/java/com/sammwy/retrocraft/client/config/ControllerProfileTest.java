package com.sammwy.retrocraft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.sammwy.retrocraft.client.config.ControllerProfile.Action;

class ControllerProfileTest {
    @Test
    void pspHasOneIndependentAnalogStickAndOnlyItsPhysicalButtons() {
        var profile = ControllerProfile.of("psp");
        assertEquals(1, profile.sticks());
        assertEquals("cross", profile.label(Action.B));
        assertEquals("triangle", profile.label(Action.X));
        assertEquals("L", profile.label(Action.L));
        assertFalse(profile.actions().contains(Action.L2));
        assertFalse(profile.actions().contains(Action.L3));
        assertFalse(profile.actions().contains(Action.RIGHT_UP));
        var input = profile.sample(Map.of(), Set.of(265, 68, 90, 69)::contains);
        assertEquals(Action.UP.mask() | Action.B.mask() | Action.R.mask(), input.buttons());
        assertEquals(32767, input.leftX());
        assertEquals(0, input.leftY());
        assertEquals(0, input.rightX());
        assertEquals(0, input.rightY());
    }

    @Test
    void playStationHasEveryDigitalButtonAndIndependentSticks() {
        var profile = ControllerProfile.of("ps2");
        assertEquals(24, profile.actions().size());
        Set<Integer> keys = Set.of(265, 65, 73, 49, 51, 340, 345);
        var input = profile.sample(Map.of(), keys::contains);
        assertEquals(
                Action.UP.mask()
                        | Action.L2.mask()
                        | Action.R2.mask()
                        | Action.L3.mask()
                        | Action.R3.mask(),
                input.buttons());
        assertEquals(-32767, input.leftX());
        assertEquals(0, input.leftY());
        assertEquals(0, input.rightX());
        assertEquals(-32767, input.rightY());
        assertEquals("cross", profile.label(Action.B));
        assertEquals("circle", profile.label(Action.A));
    }

    @Test
    void rebindingIsConsoleSpecificAndOppositeDirectionsCancel() {
        var ps2 = ControllerProfile.of("ps2");
        var nes = ControllerProfile.of("nes");
        var keys = Set.of(70, 265, 264, 65, 68);
        var input = ps2.sample(Map.of("b", 70), keys::contains);
        assertEquals(Action.B.mask(), input.buttons());
        assertEquals(0, input.leftX());
        assertEquals(0, input.leftY());
        assertEquals(0, nes.sample(Map.of(), keys::contains).buttons());
        assertEquals(0, ps2.sample(Map.of("b", -1), key -> key == 90).buttons());
    }

    @Test
    void everyConsoleHasDistinctDefaultsAndOnlyRelevantControls() {
        for (String id :
                List.of(
                        "nes",
                        "nes_handheld",
                        "snes",
                        "gb",
                        "gbc",
                        "gba",
                        "gba_sp",
                        "nds",
                        "3ds",
                        "psp",
                        "ps1",
                        "ps2",
                        "dreamcast",
                        "wii",
                        "gamecube",
                        "megadrive",
                        "genesis",
                        "n64",
                        "arcade",
                        "arcade_tetris",
                        "arcade_space_invaders")) {
            var profile = ControllerProfile.of(id);
            var keys = new HashSet<Integer>();
            for (Action action : profile.actions())
                assertTrue(
                        keys.add(profile.defaultKey(action)),
                        id + ": duplicate default for " + action);
        }
        assertEquals(8, ControllerProfile.of("nes").actions().size());
        assertEquals(0, ControllerProfile.of("nes").sticks());
        assertEquals(2, ControllerProfile.of("ps2").sticks());
    }
}
