package com.sammwy.retrocraft.cores;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.zip.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreSystemAssetsTest {
    @TempDir Path root;

    @Test
    void installedSystemFilesArePinnedAndLocalCorruptionIsRejected() throws Exception {
        byte[] archive =
                zip(
                        Map.of(
                                "PPSSPP/compat.ini",
                                "[Compatibility]",
                                "PPSSPP/flash0/font/ltn0.pgf",
                                "font",
                                "PPSSPP/lang/en_US.ini",
                                "[English]"));
        CoreSystemAssets.install(new ByteArrayInputStream(archive), root);
        CoreSystemAssets.verify(root);
        assertEquals("font", Files.readString(root.resolve("PPSSPP/flash0/font/ltn0.pgf")));
        Files.writeString(root.resolve("PPSSPP/compat.ini"), "corrupted");
        assertThrows(IOException.class, () -> CoreSystemAssets.verify(root));
    }

    @Test
    void archivePathsCannotEscapeTheSystemTreeAndIncompleteAssetsAreRejected() throws Exception {
        for (String name :
                new String[] {
                    "PPSSPP/../../escape",
                    "PPSSPP/../escape",
                    "/PPSSPP/absolute",
                    "PPSSPP\\escape",
                    "foreign/file"
                }) {
            byte[] archive = zip(Map.of(name, "bad"));
            assertThrows(
                    IOException.class,
                    () -> CoreSystemAssets.install(new ByteArrayInputStream(archive), root));
        }
        assertFalse(Files.exists(root.getParent().resolve("escape")));
        assertThrows(
                IOException.class,
                () ->
                        CoreSystemAssets.install(
                                new ByteArrayInputStream(
                                        zip(Map.of("PPSSPP/only.ini", "incomplete"))),
                                root));
        assertFalse(Files.exists(root.resolve("receipt.properties")));
    }

    private static byte[] zip(Map<String, String> files) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
