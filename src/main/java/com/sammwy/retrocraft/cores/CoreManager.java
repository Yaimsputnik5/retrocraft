package com.sammwy.retrocraft.cores;

import com.sammwy.retrocraft.roms.InstanceStore;
import com.sammwy.retrocraft.roms.SaveStates;
import java.io.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.*;

/** Shared by client, dedicated server and integrated server. No Minecraft or rendering dependency. */
public final class CoreManager {
    public record Prepared(Path path, CoreIdentity identity) {}
    private static final java.util.concurrent.ScheduledExecutorService TIMEOUTS = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "retrocraft-core-download-timeout"); thread.setDaemon(true); return thread;
    });
    private static final Map<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private final Path root, workerJar;
    private final KnownCores.Platform platform;
    public CoreManager(Path root, Path workerJar) { this(root, workerJar, KnownCores.Platform.current()); }
    public CoreManager(Path root, Path workerJar, KnownCores.Platform platform) { this.root = root; this.workerJar = workerJar; this.platform = platform; }

    public Prepared prepare(String id) throws IOException {
        String filename = KnownCores.filename(id, platform);
        Path directory = root.resolve(KnownCores.POOL).resolve(platform.key()).resolve(id).toAbsolutePath().normalize();
        ReentrantLock lock = LOCKS.computeIfAbsent(directory, ignored -> new ReentrantLock());
        lock.lock();
        try {
            Files.createDirectories(directory);
            Path core = directory.resolve(filename), receipt = directory.resolve("receipt.properties");
            if (Files.exists(core) && Files.exists(receipt)) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(receipt)) { properties.load(reader); }
                if (!InstanceStore.sha256(core).equals(properties.getProperty("sha256"))) throw new IOException("Cached core checksum mismatch: " + id);
            } else {
                download(id, core);
                Properties properties = new Properties();
                properties.setProperty("sha256", InstanceStore.sha256(core));
                properties.setProperty("source", KnownCores.uri(id, platform).toString());
                StringWriter writer = new StringWriter(); properties.store(writer, "Locally pinned official HTTPS download; never silently updated");
                SaveStates.writeAtomic(receipt, writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            WorkerProcess.Probe probe = WorkerProcess.probe(workerJar, core);
            return new Prepared(core, new CoreIdentity(id, probe.name(), probe.version(), platform.key(), InstanceStore.sha256(core)));
        } finally { lock.unlock(); }
    }

    private void download(String id, Path target) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(KnownCores.uri(id, platform)).timeout(Duration.ofMinutes(2)).GET().build();
        Path temporary = Files.createTempFile(target.getParent(), "download-", ".tmp");
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            HttpResponse<InputStream> response;
            try { response = client.send(request, HttpResponse.BodyHandlers.ofInputStream()); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException(ex); }
            try (InputStream body = response.body()) {
                var timeout = TIMEOUTS.schedule(() -> { try { body.close(); } catch (IOException ignored) {} }, 120, java.util.concurrent.TimeUnit.SECONDS);
                try {
                    if (response.statusCode() != 200) throw new IOException("Official core download returned HTTP " + response.statusCode());
                    // The ZIP is small for this pool; bounding before extraction also limits zip bombs.
                    byte[] archive = body.readNBytes(64 * 1024 * 1024 + 1);
                    if (archive.length > 64 * 1024 * 1024) throw new IOException("Core download exceeds limit");
                    boolean found = false;
                    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            if (!entry.getName().equals(target.getFileName().toString()) || entry.isDirectory()) continue;
                            byte[] binary = zip.readNBytes(64 * 1024 * 1024 + 1);
                            if (binary.length < 1 || binary.length > 64 * 1024 * 1024) throw new IOException("Invalid native core size");
                            Files.write(temporary, binary); found = true; break;
                        }
                    }
                    if (!found) throw new IOException("Official archive did not contain the expected core");
                } finally { timeout.cancel(false); }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
