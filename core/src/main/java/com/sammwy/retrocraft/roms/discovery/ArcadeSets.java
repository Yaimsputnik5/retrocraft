package com.sammwy.retrocraft.roms.discovery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Built-in MAME 2003 / MAME 2003-Plus machine and BIOS index: maps short set IDs to titles, parent
 * sets and required BIOS zips.
 */
public final class ArcadeSets {
    public record Bios(String id, String zip, String title, List<String> members) {}

    public record Machine(String id, String zip, String title, String parent, String bios) {}

    private static final Map<String, Bios> BIOSES;
    private static final Map<String, Machine> MACHINES;
    private static final Map<String, Machine> BY_TITLE;

    static {
        try (InputStream in =
                ArcadeSets.class.getResourceAsStream("/retrocraft/mame2003-plus-machines.txt")) {
            if (in == null) throw new IOException("Missing arcade machine catalog");
            var bioses = new LinkedHashMap<String, Bios>();
            var machines = new LinkedHashMap<String, Machine>();
            var byTitle = new HashMap<String, Machine>();
            for (String line :
                    new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", -1);
                if (parts[0].equals("@bios")) {
                    if (parts.length < 2 || parts[1].isBlank()) continue;
                    String id = parts[1].trim().toLowerCase(Locale.ROOT);
                    String title = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : id;
                    List<String> members =
                            parts.length > 3 && !parts[3].isBlank()
                                    ? Arrays.stream(parts[3].split(","))
                                            .map(s -> s.trim().toLowerCase(Locale.ROOT))
                                            .filter(s -> !s.isEmpty())
                                            .toList()
                                    : List.of();
                    bioses.put(id, new Bios(id, id + ".zip", title, members));
                    continue;
                }
                String id = parts[0].trim().toLowerCase(Locale.ROOT);
                if (id.isEmpty()) continue;
                String title = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : id;
                String parent =
                        parts.length > 2 && !parts[2].isBlank()
                                ? parts[2].trim().toLowerCase(Locale.ROOT)
                                : null;
                String bios =
                        parts.length > 3 && !parts[3].isBlank()
                                ? parts[3].trim().toLowerCase(Locale.ROOT)
                                : null;
                var machine = new Machine(id, id + ".zip", title, parent, bios);
                machines.put(id, machine);
                byTitle.putIfAbsent(title.toLowerCase(Locale.ROOT), machine);
            }
            BIOSES = Collections.unmodifiableMap(bioses);
            MACHINES = Collections.unmodifiableMap(machines);
            BY_TITLE = Collections.unmodifiableMap(byTitle);
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static String zipStem(String filename) {
        if (filename == null) return null;
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return base.endsWith(".zip") ? base.substring(0, base.length() - 4) : null;
    }

    private static String token(String value) {
        if (value == null) return "";
        String base = value.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim().toLowerCase(Locale.ROOT);
        return base.endsWith(".zip") ? base.substring(0, base.length() - 4) : base;
    }

    public static boolean contains(String filename) {
        String id = zipStem(filename);
        return id != null && MACHINES.containsKey(id);
    }

    public static boolean isBios(String filename) {
        String id = zipStem(filename);
        return id != null && BIOSES.containsKey(id);
    }

    public static boolean isArchive(String filename) {
        return contains(filename) || isBios(filename);
    }

    public static Machine machine(String name) {
        if (name == null || name.isBlank()) return null;
        Machine byId = MACHINES.get(token(name));
        if (byId != null) return byId;
        return BY_TITLE.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static Bios bios(String name) {
        if (name == null || name.isBlank()) return null;
        return BIOSES.get(token(name));
    }

    public static Bios requiredBios(String name) {
        Machine m = machine(name);
        return m != null && m.bios() != null ? BIOSES.get(m.bios()) : null;
    }

    public static String title(String name) {
        Machine m = machine(name);
        if (m != null) return m.title();
        if (name == null) return "";
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    public static Map<String, Machine> machines() {
        return MACHINES;
    }

    public static Map<String, Bios> bioses() {
        return BIOSES;
    }

    private ArcadeSets() {}
}
