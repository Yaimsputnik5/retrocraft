package com.sammwy.retrocraft.cores;

import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.cores.stream.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Blocking native IPC; call only from the instance's worker thread. */
public final class WorkerProcess implements AutoCloseable {
    public record Probe(String name, String version) {}
    public static Probe probe(Path jar, Path core) throws IOException {
        Path result = Files.createTempFile("retrocraft-probe-", ".bin"), log = Files.createTempFile("retrocraft-probe-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(javaExecutable(), "-Xmx64m", "-jar", jar.toAbsolutePath().toString(), "--probe", core.toAbsolutePath().toString(), result.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) throw new IOException("Core probe timed out");
            if (process.exitValue() != 0) {
                String detail = Files.readString(log);
                throw new IOException("Core probe failed: " + detail.substring(0, Math.min(detail.length(), 1000)));
            }
            try (DataInputStream in = new DataInputStream(Files.newInputStream(result))) {
                if (in.readInt() != WorkerProtocol.MAGIC) throw new IOException("Invalid core probe");
                return new Probe(in.readUTF(), in.readUTF());
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException(ex); }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); Files.deleteIfExists(result); Files.deleteIfExists(log); }
    }
    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().startsWith("windows") ? "java.exe" : "java").toString();
    }
    public record Output(FrameCodec.Frame frame, byte[] audio) {}
    private final Process process;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final double fps;
    private final int sampleRate;

    public WorkerProcess(Path workerJar, Path core, Path rom, Path state, Path log) throws IOException {
        this(workerJar, core, rom, state, state.getParent(), log);
    }

    public WorkerProcess(Path workerJar, Path core, Path rom, Path state, Path bios, Path log) throws IOException {
        String token = UUID.randomUUID().toString();
        Files.createDirectories(state.getParent());
        if (bios != null) Files.createDirectories(bios);
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Process child = null;
        Socket connection = null;
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(20_000);
            child = new ProcessBuilder(java.toString(), "-Xms16m", "-Xmx96m", "-jar", workerJar.toAbsolutePath().toString(),
                    core.toAbsolutePath().toString(), rom.toAbsolutePath().toString(), state.toAbsolutePath().toString(),
                    Integer.toString(listener.getLocalPort()), token, bios == null ? "" : bios.toAbsolutePath().toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
            connection = listener.accept();
            connection.setSoTimeout(15_000);
            connection.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(connection.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));
            if (!token.equals(in.readUTF()) || in.readInt() != WorkerProtocol.MAGIC) throw new IOException("Invalid worker handshake");
            fps = in.readDouble();
            sampleRate = in.readInt();
            if (!Double.isFinite(fps) || fps < 40 || fps > 120 || sampleRate < 8000 || sampleRate > 192000)
                throw new IOException("Invalid worker timing");
            process = child;
            socket = connection;
        } catch (IOException | RuntimeException ex) {
            if (connection != null) try { connection.close(); } catch (IOException ignored) {}
            if (child != null) child.destroyForcibly();
            throw ex;
        }
    }

    public double fps() { return fps; }
    public int sampleRate() { return sampleRate; }

    public Output advance(int mask) throws IOException {
        return advance(new int[]{mask}, true, true);
    }

    public Output advance(int mask, boolean video, boolean sound) throws IOException {
        return advance(new int[]{mask}, video, sound);
    }

    /** One 16-bit RetroPad mask per controller port; missing ports read as idle. */
    public Output advance(int[] masks, boolean video, boolean sound) throws IOException {
        out.writeByte(WorkerProtocol.RUN);
        for (int port = 0; port < WorkerProtocol.PORTS; port++) out.writeShort(port < masks.length ? masks[port] : 0);
        out.writeByte((video ? 1 : 0) | (sound ? 2 : 0));
        out.flush();
        int width = in.readUnsignedShort(), height = in.readUnsignedShort();
        if (!((width == 0 && height == 0) || (width >= 1 && width <= FrameCodec.MAX_WIDTH && height >= 1 && height <= FrameCodec.MAX_HEIGHT))) throw new IOException("Invalid worker frame");
        byte[] rgb = new byte[width * height * 3];
        in.readFully(rgb);
        int length = in.readInt();
        if (length < 0 || length > WorkerProtocol.MAX_AUDIO || length % 4 != 0) throw new IOException("Invalid worker audio");
        byte[] audio = new byte[length];
        in.readFully(audio);
        return new Output(width == 0 ? null : new FrameCodec.Frame(width, height, rgb), audio);
    }

    public void save() throws IOException {
        out.writeByte(WorkerProtocol.SAVE);
        out.flush();
        if (!in.readBoolean()) throw new IOException("Snapshot rejected");
    }

    @Override public void close() {
        try { out.writeByte(WorkerProtocol.QUIT); out.flush(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
            }
        } catch (InterruptedException ex) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }

    public void abort() { process.destroyForcibly(); try { socket.close(); } catch (IOException ignored) {} }
}
