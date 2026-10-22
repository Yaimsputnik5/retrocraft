package com.sammwy.retrocraft.roms;

import java.util.List;

/** Wire/creative metadata only; the catalog itself contains neither ROM bytes nor server filesystem paths. */
public final class RomCatalog {
    public record Entry(String cartridge, String gameId, String name) {}
    private static volatile List<Entry> clientEntries = List.of();
    public static List<Entry> clientEntries() { return clientEntries; }
    public static void clientEntries(List<Entry> entries) { clientEntries = List.copyOf(entries); }
    private RomCatalog() {}
}
