package com.sammwy.retrocraft.roms.discovery;

import java.util.*;
import java.util.function.Predicate;

/** Filename rules only: matching never needs ROM bytes or archive reads. */
public final class DiscoveryStrategy {
    private final Predicate<String> matches;
    private DiscoveryStrategy(Predicate<String> matches) { this.matches = matches; }

    public static DiscoveryStrategy extension(String... names) {
        if (names.length == 0) throw new IllegalArgumentException("At least one extension is required");
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            String value = name.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
            if (!value.matches("[a-z0-9]+")) throw new IllegalArgumentException("Invalid extension");
            normalized.add(value);
        }
        return new DiscoveryStrategy(name -> normalized.contains(suffix(name)));
    }
    public DiscoveryStrategy or(DiscoveryStrategy other) { return new DiscoveryStrategy(matches.or(other.matches)); }
    /** The candidate only matches when some ancestor directory contains one of the given tokens (case-insensitive). */
    public DiscoveryStrategy inDir(String... tokens) { return inDir(tokens, new String[0]); }
    /** Same, but segments containing an excluded token never count (e.g. "playstation" must skip "playstation 2"). */
    public DiscoveryStrategy inDir(String[] include, String[] exclude) {
        Set<String> dirs = new LinkedHashSet<>(), blocked = new LinkedHashSet<>();
        for (String token : include) dirs.add(token.toLowerCase(Locale.ROOT));
        for (String token : exclude) blocked.add(token.toLowerCase(Locale.ROOT));
        return require(name -> {
            String path = name.replace('\\', '/').toLowerCase(Locale.ROOT);
            int last = path.lastIndexOf('/');
            if (last < 0) return false;
            for (String segment : path.substring(0, last).split("/")) {
                boolean denied = false;
                for (String token : blocked) if (segment.contains(token)) { denied = true; break; }
                if (denied) continue;
                for (String token : dirs) if (segment.contains(token)) return true;
            }
            return false;
        });
    }
    public DiscoveryStrategy require(Predicate<String> predicate) { return new DiscoveryStrategy(matches.and(predicate)); }
    public boolean matches(String name) { return matches.test(name); }
    public static String suffix(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
