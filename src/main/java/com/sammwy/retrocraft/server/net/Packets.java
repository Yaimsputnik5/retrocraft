package com.sammwy.retrocraft.server.net;

import java.util.*;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.worker.PadInput;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.roms.RomCatalog;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class Packets {
    private static PadInput readPad(RegistryByteBuf b) {
        return new PadInput(
                b.readUnsignedShort(),
                b.readShort(),
                b.readShort(),
                b.readShort(),
                b.readShort(),
                b.readShort(),
                b.readShort(),
                b.readBoolean());
    }

    private static void writePad(RegistryByteBuf b, PadInput input) {
        b.writeShort(input.buttons());
        b.writeShort(input.leftX());
        b.writeShort(input.leftY());
        b.writeShort(input.rightX());
        b.writeShort(input.rightY());
        b.writeShort(input.pointerX());
        b.writeShort(input.pointerY());
        b.writeBoolean(input.touching());
    }

    public static final int CLOSE = 1,
            INPUT = 2,
            POWER_OFF = 3,
            RESYNC = 4,
            CLAIM_INPUT = 6,
            AUDIO_ACK = 7,
            EJECT = 8,
            VIDEO_ACK = 9;
    public static final int PLAY = 1, STOP = 2, ROLE = 3;

    /** Personal subscriptions only. Server permissions and emulation limits are unaffected. */
    public record Preferences(boolean audio, boolean worldVideo, boolean thumbnails)
            implements CustomPayload {
        public static final Preferences DEFAULT = new Preferences(true, true, true);
        public static final Id<Preferences> ID =
                new Id<>(Identifier.of("retrocraft", "preferences"));
        public static final PacketCodec<RegistryByteBuf, Preferences> CODEC =
                new PacketCodec<>() {
                    public Preferences decode(RegistryByteBuf b) {
                        return new Preferences(b.readBoolean(), b.readBoolean(), b.readBoolean());
                    }

                    public void encode(RegistryByteBuf b, Preferences p) {
                        b.writeBoolean(p.audio);
                        b.writeBoolean(p.worldVideo);
                        b.writeBoolean(p.thumbnails);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Request(int action, UUID instance, PadInput input, long acknowledgedSequence)
            implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("retrocraft", "request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC =
                new PacketCodec<>() {
                    public Request decode(RegistryByteBuf b) {
                        return new Request(
                                b.readVarInt(), b.readUuid(), readPad(b), b.readVarLong());
                    }

                    public void encode(RegistryByteBuf b, Request p) {
                        b.writeVarInt(p.action);
                        b.writeUuid(p.instance);
                        writePad(b, p.input);
                        b.writeVarLong(p.acknowledgedSequence);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public Request(int action, UUID instance, int buttons, long sequence) {
            this(action, instance, PadInput.digital(buttons), sequence);
        }

        public static Request of(int action, UUID id) {
            return new Request(action, id, 0, 0);
        }
    }

    /**
     * {@code inWorld}: the picture is shown on a block (arcade cabinet or bound television) at
     * {@code screen}, so the client draws no overlay.
     */
    public record Ui(
            int mode,
            UUID instance,
            String console,
            String title,
            boolean controller,
            boolean canManage,
            boolean inWorld,
            long screen)
            implements CustomPayload {
        public static final Id<Ui> ID = new Id<>(Identifier.of("retrocraft", "ui"));
        public static final PacketCodec<RegistryByteBuf, Ui> CODEC =
                new PacketCodec<>() {
                    public Ui decode(RegistryByteBuf b) {
                        return new Ui(
                                b.readVarInt(),
                                b.readUuid(),
                                b.readString(64),
                                b.readString(200),
                                b.readBoolean(),
                                b.readBoolean(),
                                b.readBoolean(),
                                b.readLong());
                    }

                    public void encode(RegistryByteBuf b, Ui p) {
                        b.writeVarInt(p.mode);
                        b.writeUuid(p.instance);
                        b.writeString(p.console, 64);
                        b.writeString(p.title, 200);
                        b.writeBoolean(p.controller);
                        b.writeBoolean(p.canManage);
                        b.writeBoolean(p.inWorld);
                        b.writeLong(p.screen);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Video(UUID instance, FrameCodec.Encoded frame) implements CustomPayload {
        public static final Id<Video> ID = new Id<>(Identifier.of("retrocraft", "video"));
        public static final PacketCodec<RegistryByteBuf, Video> CODEC =
                new PacketCodec<>() {
                    public Video decode(RegistryByteBuf b) {
                        return new Video(
                                b.readUuid(),
                                new FrameCodec.Encoded(
                                        b.readVarLong(),
                                        b.readVarLong(),
                                        b.readBoolean(),
                                        b.readByteArray(FrameCodec.MAX_PACKET)));
                    }

                    public void encode(RegistryByteBuf b, Video p) {
                        b.writeUuid(p.instance);
                        b.writeVarLong(p.frame.sequence());
                        b.writeVarLong(p.frame.base());
                        b.writeBoolean(p.frame.keyframe());
                        b.writeByteArray(p.frame.data());
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Audio(UUID instance, long sequence, int sampleRate, float gain, byte[] pcm)
            implements CustomPayload {
        public static final Id<Audio> ID = new Id<>(Identifier.of("retrocraft", "audio"));
        public static final PacketCodec<RegistryByteBuf, Audio> CODEC =
                new PacketCodec<>() {
                    public Audio decode(RegistryByteBuf b) {
                        return new Audio(
                                b.readUuid(),
                                b.readVarLong(),
                                b.readVarInt(),
                                b.readFloat(),
                                b.readByteArray(WorkerProtocol.MAX_AUDIO));
                    }

                    public void encode(RegistryByteBuf b, Audio p) {
                        b.writeUuid(p.instance);
                        b.writeVarLong(p.sequence);
                        b.writeVarInt(p.sampleRate);
                        b.writeFloat(p.gain);
                        b.writeByteArray(p.pcm);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Catalog(
            int generation, boolean first, boolean last, List<RomCatalog.Entry> entries)
            implements CustomPayload {
        public static final Id<Catalog> ID = new Id<>(Identifier.of("retrocraft", "catalog"));
        public static final PacketCodec<RegistryByteBuf, Catalog> CODEC =
                new PacketCodec<>() {
                    public Catalog decode(RegistryByteBuf b) {
                        int generation = b.readVarInt();
                        boolean first = b.readBoolean(), last = b.readBoolean();
                        int size = b.readVarInt();
                        if (size < 0 || size > 128)
                            throw new IllegalArgumentException("Oversized catalog chunk");
                        List<RomCatalog.Entry> entries = new ArrayList<>(size);
                        for (int i = 0; i < size; i++)
                            entries.add(
                                    new RomCatalog.Entry(
                                            b.readString(200),
                                            b.readString(64),
                                            b.readString(200)));
                        return new Catalog(generation, first, last, List.copyOf(entries));
                    }

                    public void encode(RegistryByteBuf b, Catalog p) {
                        b.writeVarInt(p.generation);
                        b.writeBoolean(p.first);
                        b.writeBoolean(p.last);
                        b.writeVarInt(p.entries.size());
                        for (var entry : p.entries) {
                            b.writeString(entry.cartridge(), 200);
                            b.writeString(entry.gameId(), 64);
                            b.writeString(entry.name(), 200);
                        }
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** A tiny PNG of the latest frame on a handheld, fanned out to whoever can see the item. */
    public record Thumbnail(UUID instance, byte[] png) implements CustomPayload {
        public static final Id<Thumbnail> ID = new Id<>(Identifier.of("retrocraft", "thumbnail"));
        public static final PacketCodec<RegistryByteBuf, Thumbnail> CODEC =
                new PacketCodec<>() {
                    public Thumbnail decode(RegistryByteBuf b) {
                        return new Thumbnail(b.readUuid(), b.readByteArray(1 << 20));
                    }

                    public void encode(RegistryByteBuf b, Thumbnail p) {
                        b.writeUuid(p.instance);
                        b.writeByteArray(p.png);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Hybrid owners push their own handheld preview so the server can fan it out to nearby
     * watchers.
     */
    public record ThumbnailUpload(UUID instance, byte[] png) implements CustomPayload {
        public static final int MAX_BYTES = 256 * 1024;
        public static final Id<ThumbnailUpload> ID =
                new Id<>(Identifier.of("retrocraft", "thumbnail_upload"));
        public static final PacketCodec<RegistryByteBuf, ThumbnailUpload> CODEC =
                new PacketCodec<>() {
                    public ThumbnailUpload decode(RegistryByteBuf b) {
                        return new ThumbnailUpload(b.readUuid(), b.readByteArray(MAX_BYTES));
                    }

                    public void encode(RegistryByteBuf b, ThumbnailUpload p) {
                        b.writeUuid(p.instance);
                        b.writeByteArray(p.png);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record AudioStop(UUID instance) implements CustomPayload {
        public static final Id<AudioStop> ID = new Id<>(Identifier.of("retrocraft", "audio_stop"));
        public static final PacketCodec<RegistryByteBuf, AudioStop> CODEC =
                new PacketCodec<>() {
                    public AudioStop decode(RegistryByteBuf b) {
                        return new AudioStop(b.readUuid());
                    }

                    public void encode(RegistryByteBuf b, AudioStop p) {
                        b.writeUuid(p.instance);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Full controller roster resync: which player occupies each pad slot (0-3) and who is currently
     * emulating ({@code host}, false when the server runs the core itself). Sent on every roster or
     * ownership change; a resend beats incremental enter/leave packets.
     */
    public record Players(UUID instance, List<Entry> entries) implements CustomPayload {
        public record Entry(int slot, UUID player, boolean host) {}

        public static final Id<Players> ID = new Id<>(Identifier.of("retrocraft", "players"));
        public static final PacketCodec<RegistryByteBuf, Players> CODEC =
                new PacketCodec<>() {
                    public Players decode(RegistryByteBuf b) {
                        int count = b.readVarInt();
                        if (count < 0 || count > 4)
                            throw new IllegalArgumentException("Oversized player roster");
                        List<Entry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            entries.add(new Entry(b.readVarInt(), b.readUuid(), b.readBoolean()));
                        return new Players(b.readUuid(), List.copyOf(entries));
                    }

                    public void encode(RegistryByteBuf b, Players p) {
                        b.writeVarInt(p.entries.size());
                        for (var entry : p.entries) {
                            b.writeVarInt(entry.slot);
                            b.writeUuid(entry.player);
                            b.writeBoolean(entry.host);
                        }
                        b.writeUuid(p.instance);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Virtual buttons and both sticks of one roster slot, broadcast on change. */
    public record Pad(UUID instance, int slot, PadInput input) implements CustomPayload {
        public Pad(UUID instance, int slot, int buttons) {
            this(instance, slot, PadInput.digital(buttons));
        }

        public static final Id<Pad> ID = new Id<>(Identifier.of("retrocraft", "pad"));
        public static final PacketCodec<RegistryByteBuf, Pad> CODEC =
                new PacketCodec<>() {
                    public Pad decode(RegistryByteBuf b) {
                        return new Pad(b.readUuid(), b.readVarInt(), readPad(b));
                    }

                    public void encode(RegistryByteBuf b, Pad p) {
                        b.writeUuid(p.instance);
                        b.writeVarInt(p.slot);
                        writePad(b, p.input);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playC2S().register(Preferences.ID, Preferences.CODEC);
        PayloadTypeRegistry.playC2S().register(ThumbnailUpload.ID, ThumbnailUpload.CODEC);
        PayloadTypeRegistry.playS2C().register(Ui.ID, Ui.CODEC);
        PayloadTypeRegistry.playS2C().register(Video.ID, Video.CODEC);
        PayloadTypeRegistry.playS2C().register(Audio.ID, Audio.CODEC);
        PayloadTypeRegistry.playS2C().register(AudioStop.ID, AudioStop.CODEC);
        PayloadTypeRegistry.playS2C().register(Thumbnail.ID, Thumbnail.CODEC);
        PayloadTypeRegistry.playS2C().register(Catalog.ID, Catalog.CODEC);
        PayloadTypeRegistry.playS2C().register(Players.ID, Players.CODEC);
        PayloadTypeRegistry.playS2C().register(Pad.ID, Pad.CODEC);
    }

    private Packets() {}
}
