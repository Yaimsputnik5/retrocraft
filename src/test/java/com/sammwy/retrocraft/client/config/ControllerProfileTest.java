package com.sammwy.retrocraft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.sammwy.retrocraft.client.config.ControllerProfile.Action;

class ControllerProfileTest {
    @Test
    void playStationHasEveryDigitalButtonAndIndependentSticks() {
        var profile = ControllerProfile.of("ps2_console");
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
        var ps2 = ControllerProfile.of("ps2_console");
        var nes = ControllerProfile.of("nes_console");
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
                        "nes_console",
                        "nes_handheld",
                        "snes_console",
                        "gb_handheld",
                        "gbc_handheld",
                        "gba_handheld",
                        "ps1_console",
                        "ps2_console",
                        "dreamcast_console",
                        "wii_console",
                        "gamecube_console",
                        "megadrive_console",
                        "genesis_console",
                        "n64_console",
                        "mame_console",
                        "mame_tetris_console",
                        "mame_space_invaders_console")) {
            var profile = ControllerProfile.of(id);
            var keys = new HashSet<Integer>();
            for (Action action : profile.actions())
                assertTrue(
                        keys.add(profile.defaultKey(action)),
                        id + ": duplicate default for " + action);
        }
        assertEquals(8, ControllerProfile.of("nes_console").actions().size());
        assertEquals(0, ControllerProfile.of("nes_console").sticks());
        assertEquals(2, ControllerProfile.of("ps2_console").sticks());
    }
}
