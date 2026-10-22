package dev.sammwy.retrocraft.server;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class InstanceStore {
    public record Metadata(int version, UUID id, UUID owner, String console, String gameId, String rom, String coreSha256) {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;
    public InstanceStore(Path root) throws IOException { this.root = root; Files.createDirectories(root); }
    public Path directory(UUID id) { return root.resolve(id.toString()); }
    public Path snapshot(UUID id) { return directory(id).resolve("snapshot.bin"); }

    public Metadata create(UUID owner, String console, String gameId, String rom, Path core) throws IOException {
        UUID id = UUID.randomUUID();
        Metadata data = new Metadata(2, id, owner, console, gameId, rom, sha256(core));
        Files.createDirectories(directory(id));
        Path temporary = directory(id).resolve("instance.json.tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) { GSON.toJson(data, writer); }
        Files.move(temporary, directory(id).resolve("instance.json"), StandardCopyOption.REPLACE_EXISTING);
        return data;
    }

    public Metadata load(UUID id) throws IOException {
        Path file = directory(id).resolve("instance.json");
        if (Files.size(file) > 8192) throw new IOException("Oversized instance metadata");
        try (Reader reader = Files.newBufferedReader(file)) {
            Metadata data = GSON.fromJson(reader, Metadata.class);
            if (data == null || data.version != 2 || !id.equals(data.id) || data.owner == null || data.rom == null
                    || data.console == null || data.gameId == null || data.coreSha256 == null) throw new IOException("Invalid instance metadata");
            return data;
        } catch (JsonParseException ex) { throw new IOException("Invalid instance metadata", ex); }
    }

    public void destroy(UUID id) throws IOException {
        Path directory = directory(id);
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] chunk = new byte[65536];
                int length;
                while ((length = in.read(chunk)) != -1) digest.update(chunk, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
}
