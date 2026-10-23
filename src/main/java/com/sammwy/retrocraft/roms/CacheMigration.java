package com.sammwy.retrocraft.roms;

import java.io.*;
import java.nio.file.*;

/** Preserve locally pinned native builds when upgrading from the config-directory cache. */
public final class CacheMigration {
    public static synchronized void prepare(Path config, Path cache, boolean client)
            throws IOException {
        Path legacy = config.resolve("cores/managed"),
                server = cache.resolve("server/cores"),
                target = cache.resolve(client ? "client/cores" : "server/cores");
        boolean oldLayout =
                Files.isDirectory(legacy)
                        || Files.isDirectory(config.resolve("runtime"))
                        || Files.isDirectory(config.resolve("client-sessions"));
        moveIfAbsent(legacy, server);
        moveIfAbsent(config.resolve("runtime"), cache.resolve("server/runtime"));
        if (oldLayout)
            moveIfAbsent(config.resolve("roms/cache"), cache.resolve("client/legacy-roms"));
        moveIfAbsent(config.resolve("client-sessions"), cache.resolve("client/sessions"));
        if (client && !Files.exists(target) && Files.isDirectory(server)) {
            try (var paths = Files.walk(server)) {
                for (Path source : paths.toList()) {
                    if (Files.isSymbolicLink(source)) continue;
                    Path destination = target.resolve(server.relativize(source));
                    if (Files.isDirectory(source)) Files.createDirectories(destination);
                    else if (Files.isRegularFile(source) && !Files.exists(destination))
                        Files.copy(source, destination);
                }
            }
        }
        Files.createDirectories(target);
    }

    private static void moveIfAbsent(Path from, Path to) throws IOException {
        if (Files.isDirectory(from) && !Files.exists(to)) {
            Files.createDirectories(to.getParent());
            Files.move(from, to);
        }
    }

    private CacheMigration() {}
}
