package com.sammwy.retrocraft.roms.discovery;

import java.util.*;
import java.util.function.BiPredicate;

/** Extension rules are evaluated for every cartridge before any magic rule. */
public final class DiscoveryStrategy {
    private final BiPredicate<String, byte[]> extensions, magic;
    private final String suffix;
    private java.util.function.Predicate<String> names = name -> false;
    private DiscoveryStrategy(String suffix, BiPredicate<String, byte[]> extensions, BiPredicate<String, byte[]> magic) {
        this.suffix = suffix; this.extensions = extensions; this.magic = magic;
    }
    public static DiscoveryStrategy extension(String... names) {
        if (names.length == 0) throw new IllegalArgumentException("At least one extension is required");
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : names) {
            String value = name.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
            if (!value.matches("[a-z0-9]+")) throw new IllegalArgumentException("Invalid extension");
            normalized.add(value);
        }
        DiscoveryStrategy result = new DiscoveryStrategy(normalized.iterator().next(), (name, bytes) -> normalized.contains(suffix(name)), (name, bytes) -> false);
        result.names = name -> normalized.contains(suffix(name)); return result;
    }
    public static DiscoveryStrategy magic(int... bytes) { return magicAt(0, bytes); }
    public static DiscoveryStrategy magicAt(int offset, int... bytes) {
        if (offset < 0 || bytes.length == 0) throw new IllegalArgumentException("Invalid signature");
        byte[] expected = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 0 || bytes[i] > 255) throw new IllegalArgumentException("Signature bytes must be unsigned");
            expected[i] = (byte) bytes[i];
        }
        return matching("bin", (name, header) -> header.length >= offset + expected.length && Arrays.equals(header, offset, offset + expected.length, expected, 0, expected.length));
    }
    public static DiscoveryStrategy matching(String suffix, BiPredicate<String, byte[]> predicate) {
        return new DiscoveryStrategy(suffix, (name, bytes) -> false, Objects.requireNonNull(predicate));
    }
    public DiscoveryStrategy or(DiscoveryStrategy other) {
        DiscoveryStrategy result = new DiscoveryStrategy(suffix, (name, bytes) -> extensions.test(name, bytes) || other.extensions.test(name, bytes),
                (name, bytes) -> magic.test(name, bytes) || other.magic.test(name, bytes));
        result.names = name -> names.test(name) || other.names.test(name); return result;
    }
    /** The candidate only matches when some ancestor directory contains one of the given tokens (case-insensitive). */
    public DiscoveryStrategy inDir(String... tokens) { return inDir(tokens, new String[0]); }
    /** Same, but segments containing an excluded token never count (e.g. "playstation" must skip "playstation 2"). */
    public DiscoveryStrategy inDir(String[] include, String[] exclude) {
        Set<String> dirs = new LinkedHashSet<>(), blocked = new LinkedHashSet<>();
        for (String token : include) dirs.add(token.toLowerCase(Locale.ROOT));
        for (String token : exclude) blocked.add(token.toLowerCase(Locale.ROOT));
        return require((name, bytes) -> {
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
    public DiscoveryStrategy require(BiPredicate<String, byte[]> predicate) {
        DiscoveryStrategy result = new DiscoveryStrategy(suffix, (name, bytes) -> predicate.test(name, bytes) && extensions.test(name, bytes),
                (name, bytes) -> predicate.test(name, bytes) && magic.test(name, bytes));
        result.names = names; return result;
    }
    public boolean recognizesExtension(String name) { return names.test(name); }
    public boolean matchesExtension(String name, byte[] header) { return extensions.test(name, header); }
    public boolean matchesMagic(String name, byte[] header) { return magic.test(name, header); }
    public String canonicalExtension() { return suffix; }
    public static String suffix(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
