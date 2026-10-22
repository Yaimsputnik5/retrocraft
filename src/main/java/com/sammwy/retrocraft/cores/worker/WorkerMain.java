package com.sammwy.retrocraft.cores.worker;

import com.sun.jna.*;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;

/** One native core per process: cores have globals and are not safely multi-instance. */
public final class WorkerMain {
    private final Libretro core;
    private final Path statePath;
    private final Memory directory;
    private final Map<String, Memory> variables = new HashMap<>();
    private int pixelFormat, input;
    private boolean shutdown;
    private boolean captureVideo = true, captureAudio = true;
    private FrameCodec.Frame frame = new FrameCodec.Frame(256, 240, new byte[256 * 240 * 3]);
    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();

    // Keep strong references: JNA callbacks must live as long as the native core.
    private final Libretro.Environment environment = this::environment;
    private final Libretro.Video video = this::video;
    private final Libretro.Audio sample = this::sample;
    private final Libretro.AudioBatch batch = this::batch;
    private final Libretro.InputPoll poll = () -> {};
    private final Libretro.InputState buttons = this::inputState;
    // Some cores (MAME 2003-Plus) call the log callback unconditionally, so one must always be provided.
    private final Libretro.Log log = (level, format) -> { if (level >= 2) System.err.println("[core] " + format.strip()); };
    private final Pointer logPointer = CallbackReference.getFunctionPointer(log);
    private short inputState(int port, int device, int index, int id) {
        if (port != 0) return 0;
        if ((device & 255) == 1 && index == 0) return (short) (id == 256 ? input : id >= 0 && id < 16 ? (input >>> id) & 1 : 0);
        // Digital keyboard directions also drive the left stick; I/J/K/L drive the right stick.
        if ((device & 255) == 5 && id >= 0 && id < 2 && index >= 0 && index < 2) {
            int negative = index == 0 ? (id == 0 ? 6 : 4) : (id == 0 ? 14 : 12);
            int positive = index == 0 ? (id == 0 ? 7 : 5) : (id == 0 ? 15 : 13);
            return (short) ((((input >>> positive) & 1) - ((input >>> negative) & 1)) * 32767);
        }
        return 0;
    }

    private WorkerMain(String corePath, Path statePath) throws IOException {
        this.statePath = statePath;
        Files.createDirectories(statePath.getParent());
        directory = cString(statePath.getParent().toAbsolutePath().toString());
        core = Native.load(corePath, Libretro.class);
        if (core.retro_api_version() != 1) throw new IOException("Unsupported libretro ABI");
    }

    public static void main(String[] args) {
        if (args.length == 3 && args[0].equals("--probe")) {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(Path.of(args[2])))) {
                Libretro library = Native.load(args[1], Libretro.class);
                if (library.retro_api_version() != 1) throw new IOException("Unsupported libretro ABI");
                Libretro.SystemInfo info = new Libretro.SystemInfo();
                library.retro_get_system_info(info);
                out.writeInt(WorkerProtocol.MAGIC); out.writeUTF(info.name); out.writeUTF(info.version);
            } catch (Throwable ex) { ex.printStackTrace(); System.exit(1); }
            return;
        }
        // Never use stdout for IPC: native cores can print to it. Loopback works on all three OSes.
        if (args.length != 5) { System.err.println("Expected core, ROM, snapshot, port, token"); System.exit(2); }
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(args[3]));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            socket.setTcpNoDelay(true);
            out.writeUTF(args[4]);
            out.flush();
            new WorkerMain(args[0], Path.of(args[2])).run(Path.of(args[1]), in, out);
        } catch (Throwable ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run(Path rom, DataInputStream in, DataOutputStream out) throws IOException {
        boolean initialized = false, loaded = false;
        Memory content = null;
        try {
            core.retro_set_environment(environment);
            core.retro_set_video_refresh(video);
            core.retro_set_audio_sample(sample);
            core.retro_set_audio_sample_batch(batch);
            core.retro_set_input_poll(poll);
            core.retro_set_input_state(buttons);
            core.retro_init();
            initialized = true;
            Libretro.SystemInfo system = new Libretro.SystemInfo();
            core.retro_get_system_info(system);
            Libretro.GameInfo game = new Libretro.GameInfo();
            game.path = rom.toAbsolutePath().toString();
            if (system.needFullpath == 0) {
                byte[] bytes = Files.readAllBytes(rom);
                content = new Memory(bytes.length);
                content.write(0, bytes, 0, bytes.length);
                game.data = content;
                game.size = new Libretro.SizeT(bytes.length);
            }
            if (core.retro_load_game(game) == 0) throw new IOException("Core rejected ROM");
            loaded = true;
            core.retro_set_controller_port_device(0, 1);
            restore();
            Libretro.AvInfo av = new Libretro.AvInfo();
            core.retro_get_system_av_info(av);
            if (!Double.isFinite(av.timing.fps) || av.timing.fps < 40 || av.timing.fps > 120
                    || !Double.isFinite(av.timing.sampleRate) || av.timing.sampleRate < 8000 || av.timing.sampleRate > 192000)
                throw new IOException("Invalid core AV timing");
            out.writeInt(WorkerProtocol.MAGIC);
            out.writeDouble(av.timing.fps);
            out.writeInt((int) Math.round(av.timing.sampleRate));
            out.flush();
            while (true) {
                int command;
                try { command = in.readUnsignedByte(); } catch (EOFException ex) { break; }
                if (command == WorkerProtocol.QUIT) break;
                if (command == WorkerProtocol.SAVE) {
                    save();
                    out.writeBoolean(true);
                } else if (command == WorkerProtocol.RUN) {
                    input = in.readUnsignedShort();
                    int flags = in.readUnsignedByte();
                    captureVideo = (flags & 1) != 0;
                    captureAudio = (flags & 2) != 0;
                    if (shutdown) throw new IOException("Core requested shutdown");
                    audio.reset();
                    core.retro_run();
                    out.writeShort(captureVideo ? frame.width() : 0);
                    out.writeShort(captureVideo ? frame.height() : 0);
                    if (captureVideo) out.write(frame.rgb());
                    out.writeInt(audio.size());
                    audio.writeTo(out);
                } else throw new IOException("Unknown worker command");
                out.flush();
            }
        } finally {
            if (loaded) core.retro_unload_game();
            if (initialized) core.retro_deinit();
            if (content != null) content.close();
        }
    }

    private byte environment(int command, Pointer data) {
        switch (command & ~0x10000) {
            case 2 -> data.setByte(0, (byte) 0); // cropped overscan
            case 3 -> data.setByte(0, (byte) 1); // duplicate frame support
            case 7 -> shutdown = true;
            case 9, 31 -> data.setPointer(0, directory);
            case 10 -> {
                int format = data.getInt(0);
                if (format < 0 || format > 2) return 0;
                pixelFormat = format;
            }
            case 11, 18, 35, 37 -> { }
            case 27 -> data.setPointer(0, logPointer); // RETRO_ENVIRONMENT_GET_LOG_INTERFACE
            case 15 -> {
                String key = data.getPointer(0).getString(0);
                Memory value = variables.get(key);
                data.setPointer(Native.POINTER_SIZE, value);
                if (value == null) return 0;
            }
            case 16 -> {
                for (int i = 0; i < 1024; i++) {
                    Pointer entry = data.share((long) i * Native.POINTER_SIZE * 2);
                    Pointer key = entry.getPointer(0);
                    if (key == null) break;
                    String definition = entry.getPointer(Native.POINTER_SIZE).getString(0);
                    int separator = definition.indexOf(';');
                    if (separator >= 0) {
                        String name = key.getString(0), choices = definition.substring(separator + 1).trim();
                        String selected = switch (name) {
                            case "parallel-n64-gfxplugin" -> "angrylion";
                            case "parallel-n64-rspplugin" -> "cxd4";
                            case "parallel-n64-angrylion-multithread" -> "2";
                            default -> choices.split("\\|", 2)[0];
                        };
                        if (!Arrays.asList(choices.split("\\|")).contains(selected)) selected = choices.split("\\|", 2)[0];
                        variables.put(name, cString(selected));
                    }
                }
            }
            case 17, 49 -> data.setByte(0, (byte) 0);
            case 39, 52 -> data.setInt(0, 0); // English / legacy core options
            case 47 -> data.setInt(0, (captureVideo ? 1 : 0) | (captureAudio ? 2 : 0));
            case 50 -> data.setFloat(0, 60);
            case 51 -> { } // input bitmask support
            default -> { return 0; }
        }
        return 1;
    }

    private void video(Pointer pixels, int width, int height, Libretro.SizeT pitch) {
        if (!captureVideo) return;
        if (pixels == null) return; // libretro frame duplication
        if (width < 1 || height < 1 || width > 4096 || height > 4096)
            throw new IllegalStateException("Unsupported video geometry");
        int bpp = pixelFormat == 1 ? 4 : 2;
        if (pitch.longValue() < (long) width * bpp || pitch.longValue() > 32768) throw new IllegalStateException("Invalid video pitch");
        double scale = Math.min(1, Math.min((double) FrameCodec.MAX_WIDTH / width, (double) FrameCodec.MAX_HEIGHT / height));
        int outputWidth = Math.max(1, (int) Math.floor(width * scale)), outputHeight = Math.max(1, (int) Math.floor(height * scale));
        byte[] rgb = new byte[outputWidth * outputHeight * 3];
        for (int y = 0; y < outputHeight; y++) {
            for (int x = 0; x < outputWidth; x++) {
                long offset = (long) ((int) ((long) y * height / outputHeight)) * pitch.longValue() + (long) ((int) ((long) x * width / outputWidth)) * bpp;
                int p = bpp == 4 ? pixels.getInt(offset) : Short.toUnsignedInt(pixels.getShort(offset));
                int r, g, b;
                if (pixelFormat == 1) { r = (p >>> 16) & 255; g = (p >>> 8) & 255; b = p & 255; }
                else {
                    int greenBits = pixelFormat == 2 ? 6 : 5;
                    r = ((p >>> (greenBits + 5)) & 31) * 255 / 31;
                    g = ((p >>> 5) & ((1 << greenBits) - 1)) * 255 / ((1 << greenBits) - 1);
                    b = (p & 31) * 255 / 31;
                }
                int target = (y * outputWidth + x) * 3;
                rgb[target] = (byte) r; rgb[target + 1] = (byte) g; rgb[target + 2] = (byte) b;
            }
        }
        frame = new FrameCodec.Frame(outputWidth, outputHeight, rgb);
    }

    private void sample(short left, short right) {
        if (!captureAudio) return;
        if (audio.size() + 4 > WorkerProtocol.MAX_AUDIO) return;
        audio.write(left & 255); audio.write((left >>> 8) & 255);
        audio.write(right & 255); audio.write((right >>> 8) & 255);
    }

    private Libretro.SizeT batch(Pointer samples, Libretro.SizeT frames) {
        if (!captureAudio) return frames;
        long count = frames.longValue();
        if (count < 0 || count > WorkerProtocol.MAX_AUDIO / 4) return new Libretro.SizeT(0);
        int bytes = (int) count * 4;
        if (audio.size() + bytes <= WorkerProtocol.MAX_AUDIO) audio.writeBytes(samples.getByteArray(0, bytes));
        return frames;
    }

    private void save() throws IOException {
        long length = core.retro_serialize_size().longValue();
        if (length <= 0 || length > WorkerProtocol.MAX_STATE) throw new IOException("Core does not support bounded snapshots");
        Path temporary = statePath.resolveSibling(statePath.getFileName() + ".tmp");
        try (Memory state = new Memory(length);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            if (core.retro_serialize(state, new Libretro.SizeT(length)) == 0) throw new IOException("retro_serialize failed");
            out.writeInt(WorkerProtocol.MAGIC);
            out.writeInt((int) length);
            out.write(state.getByteArray(0, (int) length));
            long ramLength = core.retro_get_memory_size(0).longValue();
            Pointer ram = core.retro_get_memory_data(0);
            if (ramLength < 0 || ramLength > WorkerProtocol.MAX_STATE) throw new IOException("Invalid SRAM size");
            if (ram == null) ramLength = 0;
            out.writeInt((int) ramLength);
            if (ramLength > 0) out.write(ram.getByteArray(0, (int) ramLength));
        }
        try { Files.move(temporary, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, statePath, StandardCopyOption.REPLACE_EXISTING); }
    }

    private void restore() throws IOException {
        if (!Files.exists(statePath)) return;
        if (Files.size(statePath) > WorkerProtocol.MAX_STATE * 2L + 12) throw new IOException("Snapshot too large");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(statePath)))) {
            if (in.readInt() != WorkerProtocol.MAGIC) throw new IOException("Invalid snapshot header");
            int length = in.readInt();
            if (length < 1 || length > WorkerProtocol.MAX_STATE) throw new IOException("Invalid snapshot size");
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            try (Memory state = new Memory(length)) {
                state.write(0, bytes, 0, length);
                if (core.retro_unserialize(state, new Libretro.SizeT(length)) == 0) throw new IOException("Core rejected snapshot");
            }
            int ramLength = in.readInt();
            if (ramLength < 0 || ramLength > WorkerProtocol.MAX_STATE) throw new IOException("Invalid SRAM size");
            byte[] ram = new byte[ramLength];
            in.readFully(ram);
            if (ramLength > 0) {
                Pointer target = core.retro_get_memory_data(0);
                if (target == null || core.retro_get_memory_size(0).longValue() != ramLength) throw new IOException("SRAM geometry changed");
                target.write(0, ram, 0, ramLength);
            }
            if (in.read() != -1) throw new IOException("Trailing snapshot bytes");
        }
    }

    private static Memory cString(String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Memory memory = new Memory(bytes.length + 1L);
        memory.write(0, bytes, 0, bytes.length);
        memory.setByte(bytes.length, (byte) 0);
        return memory;
    }
}
