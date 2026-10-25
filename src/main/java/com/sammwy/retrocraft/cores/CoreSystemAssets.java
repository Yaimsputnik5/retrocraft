package com.sammwy.retrocraft.cores;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

import com.sammwy.retrocraft.cores.transfer.Downloads;
import com.sammwy.retrocraft.roms.InstanceStore;
import com.sammwy.retrocraft.roms.SaveStates;

/** Official, separately downloaded PPSSPP fonts and compatibility data, pinned like the core. */
final class CoreSystemAssets {
    private static final URI SOURCE =
            URI.create("https://buildbot.libretro.com/assets/system/PPSSPP.zip");
    private static final int MAX_ARCHIVE = 64 * 1024 * 1024,
            MAX_EXPANDED = 128 * 1024 * 1024,
            MAX_FILE = 16 * 1024 * 1024,
            MAX_ENTRIES = 2048;
    private static final String RECEIPT = "receipt.properties";

    static void prepare(Path coreDirectory, Downloads downloads) throws IOException {
        Path destination = coreDirectory.resolve("system");
        if (Files.exists(destination)) {
            verify(destination);
            return;
        }
        Path stage = Files.createTempDirectory(coreDirectory, "system-stage-");
        Path archive = Files.createTempFile(coreDirectory, "system-download-", ".zip");
        var progress = downloads.begin("Core", "PPSSPP system files", -1);
        try (HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()) {
            HttpResponse<InputStream> response;
            try {
                response =
                        client.send(
                                HttpRequest.newBuilder(SOURCE)
                                        .timeout(Duration.ofMinutes(2))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            }
            try (InputStream body = response.body();
                    OutputStream out = Files.newOutputStream(archive)) {
                var timeout = CoreManager.readTimeout(body);
                try {
                    if (response.statusCode() != 200)
                        throw new IOException(
                                "Official PPSSPP system download returned HTTP "
                                        + response.statusCode());
                    long expected =
                            response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (expected > MAX_ARCHIVE)
                        throw new IOException("PPSSPP system archive exceeds limit");
                    byte[] block = new byte[32768];
                    int read;
                    long size = 0;
                    while ((read = body.read(block)) != -1) {
                        size += read;
                        if (size > MAX_ARCHIVE)
                            throw new IOException("PPSSPP system archive exceeds limit");
                        out.write(block, 0, read);
                        progress.update(size, expected);
                    }
                    if (expected >= 0 && size != expected)
                        throw new IOException("Incomplete PPSSPP system download");
                } finally {
                    timeout.cancel(false);
                }
            }
            try (InputStream in = Files.newInputStream(archive)) {
                install(in, stage);
            }
            Files.move(stage, destination);
            progress.complete();
        } catch (IOException | RuntimeException ex) {
            progress.fail();
            throw ex;
        } finally {
            Files.deleteIfExists(archive);
            if (Files.exists(stage))
                try (var paths = Files.walk(stage)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                        Files.delete(path);
                }
        }
    }

    static void install(InputStream archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        var receipt = new Properties();
        receipt.setProperty("source", SOURCE.toString());
        long expanded = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(archive)) {
            ZipEntry entry;
            byte[] block = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new IOException("Too many PPSSPP asset entries");
                String name = entry.getName();
                Path target = assetPath(destination, name);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                if (receipt.containsKey("file:" + name))
                    throw new IOException("Duplicate PPSSPP asset");
                Files.createDirectories(target.getParent());
                long size = 0;
                try (OutputStream out =
                        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    int read;
                    while ((read = zip.read(block)) != -1) {
                        size += read;
                        expanded += read;
                        if (size > MAX_FILE || expanded > MAX_EXPANDED)
                            throw new IOException("PPSSPP assets expand beyond limit");
                        out.write(block, 0, read);
                    }
                }
                receipt.setProperty("file:" + name, InstanceStore.sha256(target));
            }
        }
        for (String required :
                List.of(
                        "PPSSPP/compat.ini",
                        "PPSSPP/flash0/font/ltn0.pgf",
                        "PPSSPP/lang/en_US.ini"))
            if (!receipt.containsKey("file:" + required))
                throw new IOException("PPSSPP system archive is missing " + required);
        StringWriter text = new StringWriter();
        receipt.store(text, "Official PPSSPP system files; pinned after first download");
        SaveStates.writeAtomic(
                destination.resolve(RECEIPT), text.toString().getBytes(StandardCharsets.UTF_8));
    }

    static void verify(Path directory) throws IOException {
        Path file = directory.resolve(RECEIPT);
        if (!Files.isRegularFile(file) || Files.size(file) > 1024 * 1024)
            throw new IOException("Incomplete PPSSPP system cache");
        var receipt = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            receipt.load(reader);
        }
        if (!SOURCE.toString().equals(receipt.getProperty("source")))
            throw new IOException("Invalid PPSSPP system receipt");
        int count = 0;
        for (String key : receipt.stringPropertyNames()) {
            if (!key.startsWith("file:")) continue;
            if (++count > MAX_ENTRIES) throw new IOException("Invalid PPSSPP asset count");
            Path asset = assetPath(directory, key.substring(5));
            if (Files.isSymbolicLink(asset)
                    || !Files.isRegularFile(asset)
                    || Files.size(asset) > MAX_FILE
                    || !InstanceStore.sha256(asset).equals(receipt.getProperty(key)))
                throw new IOException(
                        "Cached PPSSPP system checksum mismatch: " + key.substring(5));
        }
        if (count == 0
                || !receipt.containsKey("file:PPSSPP/compat.ini")
                || !receipt.containsKey("file:PPSSPP/flash0/font/ltn0.pgf")
                || !receipt.containsKey("file:PPSSPP/lang/en_US.ini"))
            throw new IOException("Incomplete PPSSPP system receipt");
    }

    private static Path assetPath(Path directory, String name) throws IOException {
        if (!name.startsWith("PPSSPP/") || name.contains("\\") || name.contains(":"))
            throw new IOException("Invalid PPSSPP asset path");
        for (String segment : name.split("/"))
            if (segment.equals(".") || segment.equals(".."))
                throw new IOException("PPSSPP asset escaped cache");
        Path target = directory.resolve(name).normalize();
        if (!target.startsWith(directory.normalize()))
            throw new IOException("PPSSPP asset escaped cache");
        return target;
    }

    private CoreSystemAssets() {}
}
