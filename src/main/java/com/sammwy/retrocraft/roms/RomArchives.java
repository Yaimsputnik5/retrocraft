package com.sammwy.retrocraft.roms;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Streaming archive reads only; archive paths are never extraction destinations. */
public final class RomArchives {
    public static final long MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024;
    public static final int MAX_ENTRIES = 100_000;
    public record Member(String name, long size, int ordinal) {}
    @FunctionalInterface public interface Visitor { void accept(Member member, InputStream bytes) throws IOException; }
    public static boolean supported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".gz") || lower.endsWith(".tar");
    }
    public static List<Member> list(Path path) throws IOException {
        if (path.toString().toLowerCase(Locale.ROOT).endsWith(".gz") && !tarGzip(path)) return List.of(new Member(gzipName(path), -1, 0));
        var entries = new ArrayList<Member>(); visit(path, (member, bytes) -> entries.add(member)); return entries;
    }
    public static void visit(Path path, Visitor visitor) throws IOException {
        if (Files.size(path) > MAX_ARCHIVE_BYTES) throw new IOException("Archive exceeds limit");
        String lower = path.toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(path.toFile(), java.nio.charset.Charset.forName("CP437"))) {
                var entries = zip.entries(); int ordinal = 0; long total = 0;
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    int index = ordinal++; if (ordinal > MAX_ENTRIES) throw new IOException("Too many archive entries");
                    if (entry.isDirectory() || !safeMember(entry.getName())) continue;
                    if (entry.getSize() < 0 || entry.getSize() > MAX_ARCHIVE_BYTES || (total += entry.getSize()) > MAX_ARCHIVE_BYTES) throw new IOException("Archive expands beyond limit");
                    try (InputStream in = zip.getInputStream(entry)) { visitor.accept(new Member(entry.getName(), entry.getSize(), index), in); }
                }
            }
        } else if (lower.endsWith(".tar") || tarGzip(path)) {
            try (InputStream raw = Files.newInputStream(path);
                 InputStream in = new BudgetInputStream(tarGzip(path) ? new GZIPInputStream(raw) : raw, MAX_ARCHIVE_BYTES)) { tar(in, visitor); }
        } else if (lower.endsWith(".gz")) {
            try (InputStream raw = Files.newInputStream(path); InputStream in = new BudgetInputStream(new GZIPInputStream(raw), MAX_ARCHIVE_BYTES)) {
                visitor.accept(new Member(gzipName(path), -1, 0), in);
            }
        } else throw new IOException("Unsupported archive");
    }
    public static byte[] read(Path path, String name, int ordinal, long limit) throws IOException {
        // ZIP entries can be accessed directly; TAR/GZIP are streamed without unpacking unrelated files.
        if (path.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(path.toFile(), java.nio.charset.Charset.forName("CP437"))) {
                var entries = zip.entries(); int index = 0;
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (index++ != ordinal) continue;
                    if (!entry.getName().equals(name) || !safeMember(name)) throw new IOException("Archive member changed");
                    try (InputStream in = zip.getInputStream(entry)) { return bounded(in, limit); }
                }
            }
        } else {
            try {
                visit(path, (member, in) -> {
                    if (member.ordinal == ordinal && member.name.equals(name)) throw new Found(bounded(in, limit));
                });
            } catch (Found found) { return found.bytes; }
        }
        throw new IOException("ROM archive member missing");
    }
    public static byte[] bounded(InputStream in, long limit) throws IOException {
        if (limit < 1 || limit > 64 * 1024 * 1024) throw new IOException("Invalid ROM size limit");
        byte[] bytes = in.readNBytes((int) limit + 1);
        if (bytes.length < 1 || bytes.length > limit) throw new IOException("ROM outside size limit");
        return bytes;
    }
    public static boolean safeMember(String name) {
        if (name == null || name.isBlank() || name.length() > 1024 || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.chars().anyMatch(c -> c < 32)) return false;
        return Arrays.stream(name.split("/")).noneMatch(part -> part.equals(".."));
    }
    private static boolean tarGzip(Path path) { return path.toString().toLowerCase(Locale.ROOT).endsWith(".tar.gz"); }
    private static String gzipName(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readUnsignedByte() != 0x1f || in.readUnsignedByte() != 0x8b || in.readUnsignedByte() != 8) throw new IOException("Invalid gzip header");
            int flags = in.readUnsignedByte(); if ((flags & 0xe0) != 0) throw new IOException("Invalid gzip flags");
            in.skipNBytes(6);
            if ((flags & 4) != 0) { int length = in.readUnsignedByte() | in.readUnsignedByte() << 8; in.skipNBytes(length); }
            if ((flags & 8) != 0) {
                var name = new ByteArrayOutputStream(); int b;
                while ((b = in.read()) > 0) { if (name.size() >= 1024) throw new IOException("Gzip name too long"); name.write(b); }
                if (b < 0) throw new EOFException();
                String value = name.toString(StandardCharsets.ISO_8859_1);
                if (safeMember(value)) return value;
                throw new IOException("Unsafe gzip filename");
            }
        }
        String filename = path.getFileName().toString(); return filename.substring(0, filename.length() - 3);
    }
    private static void tar(InputStream in, Visitor visitor) throws IOException {
        String longName = null; Map<String, String> global = new HashMap<>(), local = new HashMap<>();
        for (int ordinal = 0; ordinal < MAX_ENTRIES; ordinal++) {
            byte[] header = in.readNBytes(512);
            if (header.length == 0) return;
            if (header.length != 512) throw new EOFException("Truncated TAR header");
            boolean zero = true; for (byte b : header) if (b != 0) { zero = false; break; } if (zero) return;
            long checksum = 0; for (int i = 0; i < 512; i++) checksum += i >= 148 && i < 156 ? 32 : header[i] & 255;
            if (checksum != number(header, 148, 8)) throw new IOException("TAR checksum mismatch");
            long size = number(header, 124, 12); if (size < 0 || size > MAX_ARCHIVE_BYTES) throw new IOException("Invalid TAR entry size");
            String name = text(header, 0, 100), prefix = text(header, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            int type = header[156];
            if (type == 'L' || type == 'x' || type == 'g') {
                if (size > 65_536) throw new IOException("TAR metadata exceeds limit");
                byte[] data = in.readNBytes((int) size); if (data.length != size) throw new EOFException();
                if (type == 'L') longName = text(data, 0, data.length);
                else if (type == 'g') global.putAll(pax(data)); else local = pax(data);
            } else {
                var attrs = new HashMap<>(global); attrs.putAll(local); local.clear();
                if (longName != null) { name = longName; longName = null; }
                name = attrs.getOrDefault("path", name);
                if (attrs.containsKey("size")) {
                    try { size = Long.parseLong(attrs.get("size")); } catch (NumberFormatException ex) { throw new IOException("Invalid PAX size", ex); }
                    if (size < 0 || size > MAX_ARCHIVE_BYTES) throw new IOException("Invalid PAX size");
                }
                var member = new BudgetInputStream(in, size);
                if ((type == 0 || type == '0') && safeMember(name)) visitor.accept(new Member(name, size, ordinal), member);
                member.skipNBytes(member.remaining);
            }
            in.skipNBytes((512 - size % 512) % 512);
        }
        throw new IOException("Too many TAR entries");
    }
    private static Map<String, String> pax(byte[] bytes) throws IOException {
        var result = new HashMap<String, String>(); int offset = 0;
        while (offset < bytes.length) {
            int space = offset; while (space < bytes.length && bytes[space] != ' ') space++;
            int length;
            try { length = Integer.parseInt(new String(bytes, offset, space - offset, StandardCharsets.US_ASCII)); }
            catch (NumberFormatException ex) { throw new IOException("Invalid PAX record", ex); }
            if (length <= space - offset + 1 || length > bytes.length - offset || bytes[offset + length - 1] != '\n') throw new IOException("Invalid PAX record length");
            String record = new String(bytes, space + 1, offset + length - space - 2, StandardCharsets.UTF_8);
            int equals = record.indexOf('='); if (equals > 0) result.put(record.substring(0, equals), record.substring(equals + 1));
            offset += length;
        }
        return result;
    }
    private static String text(byte[] bytes, int offset, int count) {
        int end = offset; while (end < offset + count && bytes[end] != 0) end++;
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }
    private static long number(byte[] bytes, int offset, int length) throws IOException {
        if ((bytes[offset] & 0x80) != 0) throw new IOException("Unsupported binary TAR number");
        String value = text(bytes, offset, length).trim();
        try { return value.isEmpty() ? 0 : Long.parseLong(value, 8); } catch (NumberFormatException ex) { throw new IOException("Invalid TAR number", ex); }
    }
    private static final class Found extends IOException { final byte[] bytes; Found(byte[] bytes) { this.bytes = bytes; } }
    private static final class BudgetInputStream extends FilterInputStream {
        long remaining;
        BudgetInputStream(InputStream in, long size) { super(in); remaining = size; }
        @Override public int read() throws IOException { if (remaining == 0) return -1; int b = in.read(); if (b < 0) return -1; remaining--; return b; }
        @Override public int read(byte[] b, int offset, int count) throws IOException {
            if (count == 0) return 0; if (remaining == 0) return -1;
            int n = in.read(b, offset, (int) Math.min(count, remaining));
            if (n < 0) return -1; remaining -= n; return n;
        }
        @Override public long skip(long count) throws IOException {
            long n = in.skip(Math.min(count, remaining)); remaining -= n; return n;
        }
    }
    private RomArchives() {}
}
