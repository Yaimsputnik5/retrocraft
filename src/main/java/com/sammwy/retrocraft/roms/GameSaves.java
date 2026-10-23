package com.sammwy.retrocraft.roms;

import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Durable per-game saves under config/retrocraft/saves/<scope>/, so progress survives powered-off or abandoned consoles. */
public final class GameSaves {
    private final Path root;
    public GameSaves(Path root) { this.root = root; }

    private Path directory(UUID owner, boolean global) { return root.resolve(global ? "global" : owner.toString()); }
    private Path state(UUID owner, boolean global, String gameId) { return directory(owner, global).resolve(gameId + ".state"); }
    private Path battery(UUID owner, boolean global, String gameId) { return directory(owner, global).resolve(gameId + ".sav"); }

    /** The persisted savestate for this game/scope, or null when none exists. Loading refreshes its orphan age. */
    public byte[] restore(UUID owner, boolean global, String gameId) {
        Path file = state(owner, global, gameId);
        try {
            if (!Files.isRegularFile(file)) return null;
            byte[] bytes = Files.readAllBytes(file);
            SaveStates.validate(bytes);
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            return bytes;
        } catch (IOException ex) { return null; }
    }

    /**
     * Publishes the instance snapshot to the durable save directory and exports the battery SRAM next to it.
     * A clean shutdown ({@code powered=false}) deletes the savestate: it is the powered console's RAM only.
     * The battery save is always permanent.
     */
    public void persist(UUID owner, boolean global, String gameId, Path snapshot, boolean powered) throws IOException {
        if (!powered) Files.deleteIfExists(state(owner, global, gameId));
        if (!Files.isRegularFile(snapshot)) return;
        byte[] bytes = Files.readAllBytes(snapshot);
        SaveStates.validate(bytes);
        if (powered) SaveStates.writeAtomic(state(owner, global, gameId), bytes);
        byte[] sram = sram(bytes);
        if (sram != null) SaveStates.writeAtomic(battery(owner, global, gameId), sram);
    }

    /** Deletes .state files not loaded within {@code maxAge}; battery .sav files are never touched. */
    public int cleanOrphans(java.time.Duration maxAge) {
        if (!Files.isDirectory(root)) return 0;
        int removed = 0;
        long cutoff = System.currentTimeMillis() - maxAge.toMillis();
        try (var dirs = Files.list(root)) {
            for (Path dir : (Iterable<Path>) dirs::iterator) {
                if (!Files.isDirectory(dir)) continue;
                try (var files = Files.list(dir)) {
                    for (Path file : (Iterable<Path>) files::iterator)
                        if (file.getFileName().toString().endsWith(".state") && Files.getLastModifiedTime(file).toMillis() < cutoff && Files.deleteIfExists(file)) removed++;
                }
            }
        } catch (IOException ignored) { }
        return removed;
    }

    /** Extracts the raw SRAM tail of a snapshot blob (MAGIC | state | sram | end), or null when empty. */
    public static byte[] sram(byte[] snapshot) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(snapshot))) {
            if (in.readInt() != WorkerProtocol.MAGIC) throw new IOException("Snapshot format mismatch");
            int state = in.readInt();
            if (state < 1 || state > WorkerProtocol.MAX_STATE || in.skipBytes(state) != state) throw new IOException("Invalid native snapshot");
            int ram = in.readInt();
            if (ram < 0 || ram > WorkerProtocol.MAX_STATE) throw new IOException("Invalid SRAM");
            byte[] bytes = in.readNBytes(ram);
            if (bytes.length != ram || in.read() != -1) throw new IOException("Truncated SRAM");
            return bytes.length == 0 ? null : bytes;
        }
    }
}
