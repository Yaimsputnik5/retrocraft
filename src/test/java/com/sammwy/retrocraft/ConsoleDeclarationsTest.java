package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.roms.discovery.RomFormats;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.builtin.*;
import com.sammwy.retrocraft.server.console.component.*;

class ConsoleDeclarationsTest {
    private static ConsoleDefinition console(String path) {
        return Consoles.ALL.stream()
                .filter(c -> c.id().getPath().equals(path))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void coreMappingsAndRomFormatsComeFromTheConsoleClasses() {
        assertEquals(
                Map.ofEntries(
                        Map.entry("retrocraft:nes", "fceumm"),
                        Map.entry("retrocraft:snes", "snes9x"),
                        Map.entry("retrocraft:gb", "gambatte"),
                        Map.entry("retrocraft:gbc", "gambatte"),
                        Map.entry("retrocraft:gba", "mgba"),
                        Map.entry("retrocraft:nds", "desmume"),
                        Map.entry("retrocraft:3ds", "citra"),
                        Map.entry("retrocraft:psp", "ppsspp"),
                        Map.entry("retrocraft:megadrive", "genesis_plus_gx"),
                        Map.entry("retrocraft:n64", "parallel_n64"),
                        Map.entry("retrocraft:arcade", "mame2003"),
                        Map.entry("retrocraft:ps1", "beetle_psx"),
                        Map.entry("retrocraft:ps2", "pcsx2"),
                        Map.entry("retrocraft:dreamcast", "flycast"),
                        Map.entry("retrocraft:wii", "dolphin"),
                        Map.entry("retrocraft:gamecube", "dolphin")),
                KnownCores.defaultMappings());
        assertEquals(List.of("fceumm"), KnownCores.compatible("retrocraft:nes"));
        assertEquals(List.of(), KnownCores.compatible("retrocraft:unknown"));
        for (var system : Consoles.systems())
            for (String core : system.cores()) assertNotNull(KnownCores.get(core));
        assertEquals(KnownCores.defaultMappings().size(), RomFormats.ALL.size());
        assertSame(NesConsole.SYSTEM, console("nes_handheld").system());
        assertSame(GameBoyAdvanceConsole.SYSTEM, console("gba_sp").system());
        assertSame(MegaDriveConsole.SYSTEM, console("genesis").system());
        assertEquals(
                Consoles.ALL.size(),
                Consoles.ALL.stream().map(ConsoleDefinition::id).distinct().count());
    }

    @Test
    void emptySlotsAndInstalledComponentsProduceTheirCoreHardware() {
        var n64 = console("n64");
        var bare = n64.hardware("parallel_n64", Arrays.asList((ConsoleComponent) null, null));
        assertEquals("disabled", bare.options().get("parallel-n64-disable_expmem"));
        assertEquals("none", bare.options().get("parallel-n64-pak1"));
        var expanded =
                n64.hardware(
                        "parallel_n64",
                        List.of(Components.N64_EXPANSION_PAK, Components.N64_RUMBLE_PAK));
        assertEquals("enabled", expanded.options().get("parallel-n64-disable_expmem"));
        assertEquals("rumble", expanded.options().get("parallel-n64-pak4"));
        // A component in a slot that does not accept it counts as empty.
        assertEquals(
                "disabled",
                n64.hardware("parallel_n64", Arrays.asList(Components.N64_RUMBLE_PAK, null))
                        .options()
                        .get("parallel-n64-disable_expmem"));
        assertTrue(n64.hardware("other_core", List.of()).isEmpty());

        var dreamcast = console("dreamcast");
        assertEquals(
                "None",
                dreamcast
                        .hardware("flycast", List.of())
                        .options()
                        .get("reicast_device_port1_slot1"));
        assertEquals(
                "VMU",
                dreamcast
                        .hardware("flycast", Arrays.asList(Components.DC_VMU, null))
                        .options()
                        .get("reicast_device_port3_slot1"));

        var zapper = console("nes").hardware("fceumm", List.of(Components.NES_ZAPPER));
        assertEquals(258, zapper.devices().get(1));
        assertTrue(zapper.aims());
        assertFalse(console("nes_handheld").hardware("fceumm", List.of()).aims());
    }

    @Test
    void peripheralsControlWhichPlayerPortsAreAvailable() {
        assertEquals(2, console("snes").controllerPorts(List.of()));
        assertEquals(4, console("snes").controllerPorts(List.of(Components.SNES_MULTITAP)));
        assertEquals(2, console("ps1").controllerPorts(List.of()));
        assertEquals(4, console("ps1").controllerPorts(List.of(Components.PS1_MULTITAP)));
        assertEquals(2, console("nes").controllerPorts(List.of()));
        assertEquals(1, console("nes").controllerPorts(List.of(Components.NES_ZAPPER)));
        assertEquals(1, console("gb").controllerPorts(List.of(Components.GB_LINK_CABLE)));
    }

    @Test
    void linkCablesPairGameBoysThroughGambatte() {
        var gb = console("gb");
        assertTrue(gb.links(List.of(Components.GB_LINK_CABLE)));
        assertFalse(gb.links(List.of(Components.NES_ZAPPER)));
        assertFalse(gb.links(Arrays.asList((ConsoleComponent) null)));
        assertTrue(console("gbc").componentSlots().getFirst().accepts(Components.GB_LINK_CABLE));
        assertTrue(console("gba").componentSlots().isEmpty());
        var client = Components.gambatteLink(false, 56401).options();
        assertEquals("Network Client", client.get("gambatte_gb_link_mode"));
        assertEquals("56401", client.get("gambatte_gb_link_network_port"));
        StringBuilder address = new StringBuilder();
        for (int i = 1; i <= 12; i++)
            address.append(client.get("gambatte_gb_link_network_server_ip_" + i));
        assertEquals("127000000001", address.toString());
        assertThrows(IllegalArgumentException.class, () -> Components.gambatteLink(true, 80));
    }
}
