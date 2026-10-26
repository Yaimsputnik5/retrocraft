package com.sammwy.retrocraft.server.console.component;

import static com.sammwy.retrocraft.cores.worker.HardwareProfile.options;

import java.util.*;

import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;

/**
 * Built-in peripherals. Option names and device IDs come from each core's libretro sources; the
 * worker ignores any value a core does not advertise.
 */
public final class Components {
    public static final String FCEUMM = "fceumm",
            SNES9X = "snes9x",
            GAMBATTE = "gambatte",
            PARALLEL_N64 = "parallel_n64",
            BEETLE_PSX = "beetle_psx",
            FLYCAST = "flycast",
            DOLPHIN = "dolphin";

    /** fceumm RETRO_DEVICE_ZAPPER, read as a touchscreen pointer from the first pad. */
    public static final ConsoleComponent NES_ZAPPER =
            ConsoleComponent.of("nes_zapper", "NES Zapper")
                    .on(
                            FCEUMM,
                            options("fceumm_zapper_mode", "touchscreen")
                                    .device(1, (1 << 8) | 2)
                                    .pointerFrom(0));

    /** snes9x RETRO_DEVICE_JOYPAD_MULTITAP on the second port: four pads on one console. */
    public static final ConsoleComponent SNES_MULTITAP =
            ConsoleComponent.of("snes_multitap", "Super Multitap")
                    .on(SNES9X, HardwareProfile.NONE.device(1, (1 << 8) | 1));

    public static final ConsoleComponent GB_LINK_CABLE =
            ConsoleComponent.of("gb_link_cable", "Game Link Cable").link();

    public static final ConsoleComponent N64_EXPANSION_PAK =
            ConsoleComponent.of("n64_expansion_pak", "Expansion Pak")
                    .on(PARALLEL_N64, options("parallel-n64-disable_expmem", "enabled"));
    public static final ConsoleComponent N64_CONTROLLER_PAK =
            ConsoleComponent.of("n64_controller_pak", "Controller Pak")
                    .on(PARALLEL_N64, n64Paks("memory"));
    public static final ConsoleComponent N64_RUMBLE_PAK =
            ConsoleComponent.of("n64_rumble_pak", "Rumble Pak").on(PARALLEL_N64, n64Paks("rumble"));

    public static final ConsoleComponent PS1_MEMORY_CARD =
            ConsoleComponent.of("ps1_memory_card", "PlayStation Memory Card")
                    .on(BEETLE_PSX, options("beetle_psx_enable_memcard1", "enabled"));
    public static final ConsoleComponent PS1_MULTITAP =
            ConsoleComponent.of("ps1_multitap", "PlayStation Multitap")
                    .on(BEETLE_PSX, options("beetle_psx_enable_multitap_port1", "enabled"));

    public static final ConsoleComponent DC_VMU =
            ConsoleComponent.of("dc_vmu", "Visual Memory Unit")
                    .on(
                            FLYCAST,
                            dreamcastSlots(1, "VMU")
                                    .merge(
                                            options(
                                                    "reicast_per_content_vmus",
                                                    "All VMUs",
                                                    "reicast_vmu1_screen_display",
                                                    "enabled")));
    public static final ConsoleComponent DC_PURU_PURU =
            ConsoleComponent.of("dc_puru_puru", "Puru Puru Pack")
                    .on(FLYCAST, dreamcastSlots(2, "Purupuru"));

    /** Dolphin RETRO_DEVICE_WIIMOTE_NC / _CC on every controller port. */
    public static final ConsoleComponent WII_NUNCHUK =
            ConsoleComponent.of("wii_nunchuk", "Wii Nunchuk").on(DOLPHIN, allPorts((3 << 8) | 1));

    public static final ConsoleComponent WII_CLASSIC_CONTROLLER =
            ConsoleComponent.of("wii_classic_controller", "Wii Classic Controller")
                    .on(DOLPHIN, allPorts((4 << 8) | 1));

    /** Ports gambatte offers for its network Game Link. */
    public static final int[] LINK_PORTS = {56400, 56420};

    /**
     * Gambatte's network Game Link options. The managed bridge uses two clients connecting to
     * 127.0.0.1, entered as twelve digits; it owns the listener and cable lifetime.
     */
    public static HardwareProfile gambatteLink(boolean server, int port) {
        if (port < LINK_PORTS[0] || port > LINK_PORTS[1])
            throw new IllegalArgumentException("Link port out of range");
        Map<String, String> options = new HashMap<>();
        options.put("gambatte_gb_link_mode", server ? "Network Server" : "Network Client");
        options.put("gambatte_gb_link_network_port", Integer.toString(port));
        String address = "127000000001";
        for (int i = 0; i < address.length(); i++)
            options.put(
                    "gambatte_gb_link_network_server_ip_" + (i + 1),
                    String.valueOf(address.charAt(i)));
        return new HardwareProfile(options, Map.of(), -1);
    }

    public static HardwareProfile n64Paks(String pak) {
        Map<String, String> options = new HashMap<>();
        for (int port = 1; port <= WorkerProtocol.PORTS; port++)
            options.put("parallel-n64-pak" + port, pak);
        return new HardwareProfile(options, Map.of(), -1);
    }

    public static HardwareProfile dreamcastSlots(int slot, String device) {
        Map<String, String> options = new HashMap<>();
        for (int port = 1; port <= WorkerProtocol.PORTS; port++)
            options.put("reicast_device_port" + port + "_slot" + slot, device);
        return new HardwareProfile(options, Map.of(), -1);
    }

    private static HardwareProfile allPorts(int device) {
        HardwareProfile profile = HardwareProfile.NONE;
        for (int port = 0; port < WorkerProtocol.PORTS; port++)
            profile = profile.device(port, device);
        return profile;
    }

    private Components() {}
}
