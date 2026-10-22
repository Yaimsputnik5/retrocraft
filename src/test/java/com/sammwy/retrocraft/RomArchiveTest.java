package com.sammwy.retrocraft;

import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.roms.discovery.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class RomArchiveTest {
    @TempDir Path root;
    @Test void zipGzipTarAndTarGzipUsePayloadHashAndExtractOnlyOnDemand() throws Exception {
        byte[] rom = HomebrewRom.bytes(); Path cache = root.resolve("cache");
        for (String type : List.of("zip", "gz", "tar", "tar.gz")) {
            Path library = Files.createDirectories(root.resolve(type)); String filename = type.equals("gz") ? "game.nes.gz" : "collection." + type;
            byte[] archive = switch (type) { case "zip" -> zip(Map.of("nested/GAME.NES", rom, "readme.txt", new byte[]{1})); case "tar" -> tar("nested/game.nes", rom, '0'); case "tar.gz" -> gzip(tar("nested/game.nes", rom, '0')); default -> gzip(rom); };
            Path source = Files.write(library.resolve(filename), archive);
            var games = new RomScanner(RomFormats.ALL, 1024 * 1024).scan(library, RomDiscoveryTest.failWarning());
            assertEquals(1, games.size(), type); var game = games.getFirst();
            assertEquals(com.sammwy.retrocraft.cores.transfer.BlobTransfer.hash(rom), game.id());
            assertNotEquals(InstanceStore.sha256(source), game.id()); assertFalse(Files.exists(cache));
            Path extracted = game.source().materialize(library, cache, game.id(), game.filename(), 1024 * 1024);
            assertArrayEquals(rom, Files.readAllBytes(extracted));
            try (var files = Files.walk(cache)) { assertEquals(1, files.filter(Files::isRegularFile).count()); }
            try (var files = Files.walk(cache)) { for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
    }
    @Test void namesAreSelectedBeforeReadingArchivePayloadsAndEachMatchingMemberBecomesAGame() throws Exception {
        byte[] other = HomebrewRom.bytes().clone(); other[20] ^= 1;
        Files.write(root.resolve("pack.zip"), zip(Map.of("one.nes", HomebrewRom.bytes(), "sub/two.NES", other, "header-only.txt", HomebrewRom.bytes())));
        var games = new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, RomDiscoveryTest.failWarning());
        assertEquals(2, games.size()); assertEquals(Set.of("one", "two"), new HashSet<>(games.stream().map(RomScanner.Game::name).toList()));
    }
    @Test void arcadeSetsRemainWholeAndKeepMachineBasename() throws Exception {
        byte[] content = zip(Map.of("chip.6e", new byte[]{1,2,3}, "chip.bin", new byte[]{4,5}));
        Path file = Files.write(root.resolve("PACMAN.ZIP"), content);
        var games = new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, RomDiscoveryTest.failWarning());
        assertEquals(1, games.size()); var game = games.getFirst();
        assertEquals("mame", game.cartridge()); assertEquals("pacman.zip", game.filename()); assertEquals(InstanceStore.sha256(file), game.id());
        assertEquals(-1, game.source().ordinal());
        assertArrayEquals(content, Files.readAllBytes(game.source().materialize(root, root.resolveSibling("mame-cache"), game.id(), game.filename(), 1024 * 1024)));
    }
    @Test void rejectsTraversalLinksTruncationAndOversizedMembers() throws Exception {
        Files.write(root.resolve("traversal.zip"), zip(Map.of("../escape.nes", HomebrewRom.bytes(), "/absolute.nes", HomebrewRom.bytes())));
        Files.write(root.resolve("link.tar"), tar("linked.nes", new byte[0], '2'));
        Files.write(root.resolve("truncated.tar"), Arrays.copyOf(tar("broken.nes", HomebrewRom.bytes(), '0'), 700));
        Files.write(root.resolve("large.nes.gz"), gzip(new byte[100_000]));
        List<String> warnings = new ArrayList<>();
        assertTrue(new RomScanner(RomFormats.ALL, 50_000).scan(root, warnings::add).isEmpty()); assertFalse(warnings.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new RomSource("../escape.nes", null, -1));
        assertThrows(IllegalArgumentException.class, () -> new RomSource("safe.zip", "../escape.nes", 0));
    }
    @Test void paxAndGnuLongNamesAndGzipOriginalNameAreRecognized() throws Exception {
        byte[] rom = HomebrewRom.bytes(); String longName = "nested/" + "a".repeat(130) + ".nes";
        byte[] paxValue = pax("path", longName);
        Files.write(root.resolve("pax.tar"), concat(tarEntry("PaxHeader", paxValue, 'x'), tar("short", rom, '0')));
        var first = new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, RomDiscoveryTest.failWarning()); assertEquals(1, first.size());
        assertEquals(longName, first.getFirst().source().member());
        assertArrayEquals(rom, Files.readAllBytes(first.getFirst().source().materialize(root, root.resolveSibling("long-cache"), first.getFirst().id(), first.getFirst().filename(), 1024 * 1024)));
        Files.delete(root.resolve("pax.tar"));
        Files.write(root.resolve("gnu.tar"), concat(tarEntry("././@LongLink", (longName + "\0").getBytes(StandardCharsets.UTF_8), 'L'), tar("short", rom, '0')));
        assertEquals(longName, new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, RomDiscoveryTest.failWarning()).getFirst().source().member());
        Files.delete(root.resolve("gnu.tar"));
        byte[] gz = gzip(rom), named = new byte[gz.length + 9]; System.arraycopy(gz, 0, named, 0, 10); named[3] = 8;
        System.arraycopy("game.nes\0".getBytes(StandardCharsets.US_ASCII), 0, named, 10, 9); System.arraycopy(gz, 10, named, 19, gz.length - 10);
        Files.write(root.resolve("unnamed.gz"), named);
        assertEquals("game.nes", new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, RomDiscoveryTest.failWarning()).getFirst().source().member());
    }
    @Test void changedArchiveCannotBeServedUnderOldGameIdAndEscapingSymlinkIsIgnored() throws Exception {
        Path archive = Files.write(root.resolve("pack.zip"), zip(Map.of("game.nes", HomebrewRom.bytes())));
        var game = new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, RomDiscoveryTest.failWarning()).getFirst();
        byte[] changed = HomebrewRom.bytes().clone(); changed[20] ^= 1; Files.write(archive, zip(Map.of("game.nes", changed)));
        assertThrows(IOException.class, () -> game.source().materialize(root, root.resolveSibling("stale-cache"), game.id(), game.filename(), 1024 * 1024));
        Files.delete(archive); Path external = Files.write(root.resolveSibling(root.getFileName() + "-outside.nes"), changed);
        try { Files.createSymbolicLink(root.resolve("escape.nes"), external); assertTrue(new RomScanner(RomFormats.ALL, 1024 * 1024).scan(root, RomDiscoveryTest.failWarning()).isEmpty()); }
        finally { Files.deleteIfExists(external); }
    }
    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) { for (var entry : new TreeMap<>(entries).entrySet()) { out.putNextEntry(new ZipEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry(); } }
        return bytes.toByteArray();
    }
    static byte[] gzip(byte[] content) throws IOException { var bytes = new ByteArrayOutputStream(); try (var out = new GZIPOutputStream(bytes)) { out.write(content); } return bytes.toByteArray(); }
    static byte[] tar(String name, byte[] content, int type) throws IOException { return concat(tarEntry(name, content, type), new byte[1024]); }
    static byte[] tarEntry(String name, byte[] content, int type) throws IOException {
        byte[] header = new byte[512]; byte[] filename = name.getBytes(StandardCharsets.UTF_8); System.arraycopy(filename, 0, header, 0, Math.min(filename.length, 100));
        octal(header, 100, 8, 0644); octal(header, 124, 12, content.length); header[156] = (byte) type;
        Arrays.fill(header, 148, 156, (byte) ' '); int sum = 0; for (byte b : header) sum += b & 255; octal(header, 148, 8, sum);
        return concat(header, content, new byte[(512 - content.length % 512) % 512]);
    }
    static void octal(byte[] header, int offset, int length, int value) { byte[] bytes = String.format("%0" + (length - 1) + "o", value).getBytes(StandardCharsets.US_ASCII); System.arraycopy(bytes, 0, header, offset, bytes.length); }
    static byte[] concat(byte[]... arrays) throws IOException { var out = new ByteArrayOutputStream(); for (byte[] a : arrays) out.write(a); return out.toByteArray(); }
    static byte[] pax(String key, String value) { String record = " " + key + "=" + value + "\n"; int size = record.length() + 1; while ((Integer.toString(size) + record).length() != size) size = (Integer.toString(size) + record).length(); return (size + record).getBytes(StandardCharsets.UTF_8); }
}
