package com.sammwy.retrocraft.cores;

import java.net.URI;
import java.util.*;

/** Compiled-in allowlist. Network packets can name an ID, never a URL or native path. */
public final class KnownCores {
    public static final String POOL = "retrocraft-libretro-1";
    public record Core(String id, String upstream, boolean portableSnapshots) {}
    public record Platform(String os, String arch) {
        public static Platform current() {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
            return new Platform(os.startsWith("windows") ? "windows" : os.contains("mac") || os.contains("darwin") ? "macos" : os.contains("linux") ? "linux" : "unsupported",
                    arch.equals("amd64") || arch.equals("x86_64") ? "x86_64" : arch.equals("aarch64") || arch.equals("arm64") ? "aarch64" : "unsupported");
        }
        public String key() { return os + "-" + arch; }
        public String extension() { return switch (os) { case "windows" -> ".dll"; case "macos" -> ".dylib"; case "linux" -> ".so"; default -> throw new IllegalArgumentException("Unsupported OS"); }; }
        public String upstreamPath() {
            if (arch.equals("unsupported")) throw new IllegalArgumentException("Unsupported architecture");
            return switch (os) {
                case "linux", "windows" -> os + "/" + arch;
                case "macos" -> "apple/osx/" + (arch.equals("aarch64") ? "arm64" : "x86_64");
                default -> throw new IllegalArgumentException("Unsupported OS");
            };
        }
    }
    private static final Map<String, Core> CORES = Map.of("fceumm", new Core("fceumm", "fceumm", true), "snes9x", new Core("snes9x", "snes9x", true));
    public static Core get(String id) {
        Core core = CORES.get(id);
        if (core == null) throw new IllegalArgumentException("Core is not in the local trusted pool: " + id);
        return core;
    }
    public static String defaultForCartridge(String cartridge) { return switch (cartridge) { case "retrocraft:nes" -> "fceumm"; case "retrocraft:snes" -> "snes9x"; default -> throw new IllegalArgumentException("No known core for cartridge " + cartridge); }; }
    public static String filename(String id, Platform platform) { return get(id).upstream + "_libretro" + platform.extension(); }
    public static URI uri(String id, Platform platform) { return URI.create("https://buildbot.libretro.com/nightly/" + platform.upstreamPath() + "/latest/" + filename(id, platform) + ".zip"); }
    private KnownCores() {}
}
