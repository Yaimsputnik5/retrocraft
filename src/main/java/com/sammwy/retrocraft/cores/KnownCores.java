package com.sammwy.retrocraft.cores;

import java.net.URI;
import java.util.*;

/** Compiled-in allowlist. Network packets can name an ID, never a URL or native path. */
public final class KnownCores {
    public static final String POOL = "retrocraft-libretro-1";

    public record Core(
            String id,
            String upstream,
            boolean portableSnapshots,
            String display,
            boolean needsBios,
            boolean defaultEnabled) {}

    public record Platform(String os, String arch) {
        public static Platform current() {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
            return new Platform(
                    os.startsWith("windows")
                            ? "windows"
                            : os.contains("mac") || os.contains("darwin")
                                    ? "macos"
                                    : os.contains("linux") ? "linux" : "unsupported",
                    arch.equals("amd64") || arch.equals("x86_64")
                            ? "x86_64"
                            : arch.equals("aarch64") || arch.equals("arm64")
                                    ? "aarch64"
                                    : "unsupported");
        }

        public String key() {
            return os + "-" + arch;
        }

        public String extension() {
            return switch (os) {
                case "windows" -> ".dll";
                case "macos" -> ".dylib";
                case "linux" -> ".so";
                default -> throw new IllegalArgumentException("Unsupported OS");
            };
        }

        public String upstreamPath() {
            if (arch.equals("unsupported"))
                throw new IllegalArgumentException("Unsupported architecture");
            return switch (os) {
                case "linux", "windows" -> os + "/" + arch;
                case "macos" -> "apple/osx/" + (arch.equals("aarch64") ? "arm64" : "x86_64");
                default -> throw new IllegalArgumentException("Unsupported OS");
            };
        }
    }

    private static final Map<String, Core> CORES =
            Map.ofEntries(
                    Map.entry(
                            "fceumm",
                            new Core("fceumm", "fceumm", true, "NES / Famicom", false, true)),
                    Map.entry(
                            "snes9x",
                            new Core(
                                    "snes9x", "snes9x", true, "SNES / Super Famicom", false, true)),
                    Map.entry(
                            "gambatte",
                            new Core(
                                    "gambatte",
                                    "gambatte",
                                    true,
                                    "Game Boy & Game Boy Color",
                                    false,
                                    true)),
                    Map.entry(
                            "mgba",
                            new Core("mgba", "mgba", true, "Game Boy Advance", false, true)),
                    Map.entry(
                            "desmume",
                            new Core("desmume", "desmume", false, "Nintendo DS", false, true)),
                    Map.entry(
                            "citra",
                            new Core("citra", "citra", false, "Nintendo 3DS", false, false)),
                    Map.entry(
                            "genesis_plus_gx",
                            new Core(
                                    "genesis_plus_gx",
                                    "genesis_plus_gx",
                                    true,
                                    "Genesis / Mega Drive",
                                    false,
                                    true)),
                    Map.entry(
                            "parallel_n64",
                            new Core(
                                    "parallel_n64",
                                    "parallel_n64",
                                    false,
                                    "Nintendo 64",
                                    false,
                                    false)),
                    Map.entry(
                            "mame2003",
                            new Core(
                                    "mame2003",
                                    "mame2003",
                                    false,
                                    "Arcade (MAME 2003)",
                                    true,
                                    true)),
                    Map.entry(
                            "beetle_psx",
                            new Core(
                                    "beetle_psx", "beetle_psx", false, "PlayStation", true, false)),
                    Map.entry(
                            "pcsx2",
                            new Core("pcsx2", "pcsx2", false, "PlayStation 2", true, false)),
                    Map.entry(
                            "flycast",
                            new Core("flycast", "flycast", false, "Dreamcast", true, false)),
                    Map.entry(
                            "dolphin",
                            new Core("dolphin", "dolphin", false, "Wii & GameCube", true, false)));

    /** Ordered list for the core library UI. */
    public static List<Core> all() {
        return CORES.values().stream().sorted(Comparator.comparing(Core::id)).toList();
    }

    /** Cores enabled on a fresh config. */
    public static List<String> defaults() {
        return CORES.values().stream().filter(Core::defaultEnabled).map(Core::id).toList();
    }

    public static Map<String, String> defaultMappings() {
        return Map.ofEntries(
                Map.entry("retrocraft:nes", "fceumm"),
                Map.entry("retrocraft:snes", "snes9x"),
                Map.entry("retrocraft:gb", "gambatte"),
                Map.entry("retrocraft:gbc", "gambatte"),
                Map.entry("retrocraft:gba", "mgba"),
                Map.entry("retrocraft:nds", "desmume"),
                Map.entry("retrocraft:3ds", "citra"),
                Map.entry("retrocraft:megadrive", "genesis_plus_gx"),
                Map.entry("retrocraft:n64", "parallel_n64"),
                Map.entry("retrocraft:mame", "mame2003"),
                Map.entry("retrocraft:ps1", "beetle_psx"),
                Map.entry("retrocraft:ps2", "pcsx2"),
                Map.entry("retrocraft:dreamcast", "flycast"),
                Map.entry("retrocraft:wii", "dolphin"),
                Map.entry("retrocraft:gamecube", "dolphin"));
    }

    /**
     * Cores that can actually run a cartridge. One entry today; a list so alternatives can join
     * later.
     */
    public static List<String> compatible(String cartridge) {
        String core = defaultMappings().get(cartridge);
        return core == null ? List.of() : List.of(core);
    }

    public static Set<String> ids() {
        return CORES.keySet();
    }

    public static Core get(String id) {
        Core core = CORES.get(id);
        if (core == null)
            throw new IllegalArgumentException("Core is not in the local trusted pool: " + id);
        return core;
    }

    public static String defaultForCartridge(String cartridge) {
        String id = defaultMappings().get(cartridge);
        if (id == null)
            throw new IllegalArgumentException("No known core for cartridge " + cartridge);
        return id;
    }

    public static String filename(String id, Platform platform) {
        return get(id).upstream + "_libretro" + platform.extension();
    }

    public static URI uri(String id, Platform platform) {
        return URI.create(
                "https://buildbot.libretro.com/nightly/"
                        + platform.upstreamPath()
                        + "/latest/"
                        + filename(id, platform)
                        + ".zip");
    }

    private KnownCores() {}
}
