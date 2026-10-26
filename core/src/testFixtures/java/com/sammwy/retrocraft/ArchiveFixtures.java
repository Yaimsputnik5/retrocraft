package com.sammwy.retrocraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

final class ArchiveFixtures {
    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) {
            for (var entry : new TreeMap<>(entries).entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static byte[] gzip(byte[] content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(bytes)) {
            out.write(content);
        }
        return bytes.toByteArray();
    }

    static byte[] tar(String name, byte[] content, int type) throws IOException {
        return concat(tarEntry(name, content, type), new byte[1024]);
    }

    static byte[] tarEntry(String name, byte[] content, int type) throws IOException {
        byte[] header = new byte[512];
        byte[] filename = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(filename, 0, header, 0, Math.min(filename.length, 100));
        octal(header, 100, 8, 0644);
        octal(header, 124, 12, content.length);
        header[156] = (byte) type;
        Arrays.fill(header, 148, 156, (byte) ' ');
        int sum = 0;
        for (byte b : header) sum += b & 255;
        octal(header, 148, 8, sum);
        return concat(header, content, new byte[(512 - content.length % 512) % 512]);
    }

    static void octal(byte[] header, int offset, int length, int value) {
        byte[] bytes =
                String.format("%0" + (length - 1) + "o", value).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, bytes.length);
    }

    static byte[] concat(byte[]... arrays) throws IOException {
        var out = new ByteArrayOutputStream();
        for (byte[] a : arrays) out.write(a);
        return out.toByteArray();
    }

    static byte[] pax(String key, String value) {
        String record = " " + key + "=" + value + "\n";
        int size = record.length() + 1;
        while ((Integer.toString(size) + record).length() != size)
            size = (Integer.toString(size) + record).length();
        return (size + record).getBytes(StandardCharsets.UTF_8);
    }
}
