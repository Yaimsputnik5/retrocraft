package com.sammwy.retrocraft.roms.discovery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A ZIP is an arcade set only when its basename identifies a known MAME driver. */
public final class ArcadeSets {
    private static final Set<String> NAMES;
    static {
        try (InputStream in = ArcadeSets.class.getResourceAsStream("/retrocraft/mame2003-plus-machines.txt")) {
            if (in == null) throw new IOException("Missing arcade machine catalog");
            NAMES = new HashSet<>(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(s -> !s.isBlank() && !s.startsWith("#")).toList());
        } catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
    public static boolean contains(String filename) {
        String base = filename.replace('\\', '/'); base = base.substring(base.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return base.endsWith(".zip") && NAMES.contains(base.substring(0, base.length() - 4));
    }
    private ArcadeSets() {}
}
