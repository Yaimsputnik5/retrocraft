package com.sammwy.retrocraft.roms;

import java.io.IOException;
import java.nio.file.*;

/** The BIOS manager, sync protocol and native core must use the same firmware directory. */
public final class BiosFiles {
    public static String directory(String coreId) {
        return coreId.equals("pcsx2") ? "pcsx2/bios" : coreId;
    }

    /** Accept older UI requests as well as the canonical paths sent by current clients. */
    public static String canonical(String relative) {
        if (relative.equals("pcsx2")) return directory("pcsx2");
        if (relative.startsWith("pcsx2/")
                && !relative.equals("pcsx2/bios")
                && !relative.startsWith("pcsx2/bios/"))
            return "pcsx2/bios/" + relative.substring("pcsx2/".length());
        return relative;
    }

    /** Recover BIOS files uploaded by the old UI without replacing any installed firmware. */
    public static synchronized void migrateLegacy(Path root) throws IOException {
        Path legacy = root.resolve("pcsx2");
        if (!Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) return;
        RomLibrary library = new RomLibrary(root);
        for (var entry : library.list("pcsx2").entries()) {
            if (entry.directory() || !RomLibrary.safeName(entry.name())) continue;
            Path destination = root.resolve(directory("pcsx2")).resolve(entry.name());
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) continue;
            library.destination(directory("pcsx2"), entry.name());
            library.move("pcsx2/" + entry.name(), directory("pcsx2"));
        }
    }

    private BiosFiles() {}
}
