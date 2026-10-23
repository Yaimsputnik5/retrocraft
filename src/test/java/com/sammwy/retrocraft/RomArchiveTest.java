package com.sammwy.retrocraft;

import com.sammwy.retrocraft.roms.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Archive utilities remain available for explicit BIOS installation, never automatic ROM discovery. */
class RomArchiveTest {
    @TempDir Path root;

    @Test void explicitArchiveReadsSupportZipGzipTarAndTarGzip() throws Exception {
        byte[] rom = HomebrewRom.bytes();
        for (String type : List.of("zip", "gz", "tar", "tar.gz")) {
            String name = type.equals("gz") ? "game.nes.gz" : "collection." + type;
            byte[] archive = switch (type) {
                case "zip" -> zip(Map.of("game.nes", rom));
                case "tar" -> tar("game.nes", rom, '0');
                case "tar.gz" -> gzip(tar("game.nes", rom, '0'));
                default -> gzip(rom);
            };
            Path file = Files.write(root.resolve(name), archive);
            var member = RomArchives.list(file).getFirst();
            assertArrayEquals(rom, RomArchives.read(file, member.name(), member.ordinal(), 1024 * 1024));
        }
    }

    @Test void explicitArchiveReadsRejectTraversalLinksTruncationAndOversizedMembers() throws Exception {
        Path traversal = Files.write(root.resolve("traversal.zip"), zip(Map.of("../escape.nes", HomebrewRom.bytes(), "/absolute.nes", HomebrewRom.bytes())));
        assertTrue(RomArchives.list(traversal).isEmpty());
        assertTrue(RomArchives.list(Files.write(root.resolve("link.tar"), tar("linked.nes", new byte[0], '2'))).isEmpty());
        Path truncated = Files.write(root.resolve("truncated.tar"), Arrays.copyOf(tar("broken.nes", HomebrewRom.bytes(), '0'), 700));
        assertThrows(IOException.class, () -> RomArchives.list(truncated));
        Path large = Files.write(root.resolve("large.nes.gz"), gzip(new byte[100_000]));
        assertThrows(IOException.class, () -> RomArchives.read(large, "large.nes", 0, 50_000));
        assertThrows(IllegalArgumentException.class, () -> new RomSource("../escape.nes", null, -1));
        assertThrows(IllegalArgumentException.class, () -> new RomSource("safe.zip", "../escape.nes", 0));
    }

    @Test void explicitTarReadsSupportPaxAndGnuLongNames() throws Exception {
        byte[] rom = HomebrewRom.bytes(); String name = "nested/" + "a".repeat(130) + ".nes";
        Path pax = Files.write(root.resolve("pax.tar"), concat(tarEntry("PaxHeader", pax("path", name), 'x'), tar("short", rom, '0')));
        var member = RomArchives.list(pax).getFirst();
        assertEquals(name, member.name());
        assertArrayEquals(rom, RomArchives.read(pax, name, member.ordinal(), 1024 * 1024));
        Path gnu = Files.write(root.resolve("gnu.tar"), concat(tarEntry("././@LongLink", (name + "\0").getBytes(StandardCharsets.UTF_8), 'L'), tar("short", rom, '0')));
        assertEquals(name, RomArchives.list(gnu).getFirst().name());
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
