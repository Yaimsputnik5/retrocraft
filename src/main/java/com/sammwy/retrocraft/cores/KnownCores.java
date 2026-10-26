package com.sammwy.retrocraft.cores;

import java.net.URI;
import java.util.*;

import com.sammwy.retrocraft.server.console.Consoles;

/** Compiled-in allowlist. Network packets can name an ID, never a URL or native path. */
public final class KnownCores {
    public static final String POOL = "retrocraft-libretro-1";

    public record Core(
            String id,
            String upstream,
            boolean portableSnapshots,
            String display,
            boolean needsBios,
            boolean defaultEnabled,
            boolean serverOnly) {}

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
                            new Core(
                                    "fceumm", "fceumm", true, "NES / Famicom", false, true, false)),
                    Map.entry(
                            "snes9x",
                            new Core(
                                    "snes9x",
                                    "snes9x",
                                    true,
                                    "SNES / Super Famicom",
                                    false,
                                    true,
                                    false)),
                    Map.entry(
                            "gambatte",
                            new Core(
                                    "gambatte",
                                    "gambatte",
                                    true,
                                    "Game Boy & Game Boy Color",
                                    false,
                                    true,
                                    false)),
                    Map.entry(
                            "mgba",
                            new Core("mgba", "mgba", true, "Game Boy Advance", false, true, false)),
                    Map.entry(
                            "desmume",
                            new Core(
                                    "desmume",
                                    "desmume",
                                    false,
                                    "Nintendo DS",
                                    false,
                                    true,
                                    false)),
                    Map.entry(
                            "citra",
                            new Core("citra", "citra", false, "Nintendo 3DS", false, false, false)),
                    Map.entry(
                            "ppsspp",
                            new Core(
                                    "ppsspp",
                                    "ppsspp",
                                    false,
                                    "PlayStation Portable",
                                    false,
                                    false,
                                    true)),
                    Map.entry(
                            "genesis_plus_gx",
                            new Core(
                                    "genesis_plus_gx",
                                    "genesis_plus_gx",
                                    true,
                                    "Genesis / Mega Drive",
                                    false,
                                    true,
                                    false)),
                    Map.entry(
                            "parallel_n64",
                            new Core(
                                    "parallel_n64",
                                    "parallel_n64",
                                    false,
                                    "Nintendo 64",
                                    false,
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
                                    true,
                                    true)),
                    Map.entry(
                            "beetle_psx",
                            new Core(
                                    "beetle_psx",
                                    "beetle_psx",
                                    false,
                                    "PlayStation",
                                    true,
                                    false,
                                    false)),
                    Map.entry(
                            "pcsx2",
                            new Core("pcsx2", "pcsx2", false, "PlayStation 2", true, false, false)),
                    Map.entry(
                            "flycast",
                            new Core("flycast", "flycast", false, "Dreamcast", true, false, false)),
                    Map.entry(
                            "dolphin",
                            new Core(
                                    "dolphin",
                                    "dolphin",
                                    false,
                                    "Wii & GameCube",
                                    true,
                                    false,
                                    false)));

    /** Ordered list for the core library UI. */
    public static List<Core> all() {
        return CORES.values().stream().sorted(Comparator.comparing(Core::id)).toList();
    }

    /** Cores enabled on a fresh config. */
    public static List<String> defaults() {
        return CORES.values().stream().filter(Core::defaultEnabled).map(Core::id).toList();
    }

    /** System ID to default core, as declared by each console's game system. */
    public static Map<String, String> defaultMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (var system : Consoles.systems())
            mappings.put(system.id().toString(), system.defaultCore());
        return Collections.unmodifiableMap(mappings);
    }

    /** Cores that can actually run a cartridge, as declared by its game system. */
    public static List<String> compatible(String cartridge) {
        for (var system : Consoles.systems())
            if (system.id().toString().equals(cartridge)) return system.cores();
        return List.of();
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
