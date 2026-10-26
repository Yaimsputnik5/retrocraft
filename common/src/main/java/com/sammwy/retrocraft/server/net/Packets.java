package com.sammwy.retrocraft.server.net;

import java.util.*;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.cores.worker.PadInput;
import com.sammwy.retrocraft.cores.worker.WorkerProtocol;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Network;
import com.sammwy.retrocraft.roms.RomCatalog;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class Packets {
    private static PadInput readPad(RegistryFriendlyByteBuf b) {
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

    private static void writePad(RegistryFriendlyByteBuf b, PadInput input) {
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
            implements CustomPacketPayload {
        public static final Preferences DEFAULT = new Preferences(true, true, true);
        public static final Type<Preferences> ID = new Type<>(Ids.of("retrocraft", "preferences"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Preferences> CODEC =
                new StreamCodec<>() {
                    public Preferences decode(RegistryFriendlyByteBuf b) {
                        return new Preferences(b.readBoolean(), b.readBoolean(), b.readBoolean());
                    }

                    public void encode(RegistryFriendlyByteBuf b, Preferences p) {
                        b.writeBoolean(p.audio);
                        b.writeBoolean(p.worldVideo);
                        b.writeBoolean(p.thumbnails);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Request(int action, UUID instance, PadInput input, long acknowledgedSequence)
            implements CustomPacketPayload {
        public static final Type<Request> ID = new Type<>(Ids.of("retrocraft", "request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                new StreamCodec<>() {
                    public Request decode(RegistryFriendlyByteBuf b) {
                        return new Request(
                                b.readVarInt(), b.readUUID(), readPad(b), b.readVarLong());
                    }

                    public void encode(RegistryFriendlyByteBuf b, Request p) {
                        b.writeVarInt(p.action);
                        b.writeUUID(p.instance);
                        writePad(b, p.input);
                        b.writeVarLong(p.acknowledgedSequence);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
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
            long screen,
            boolean aims)
            implements CustomPacketPayload {
        public static final Type<Ui> ID = new Type<>(Ids.of("retrocraft", "ui"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ui> CODEC =
                new StreamCodec<>() {
                    public Ui decode(RegistryFriendlyByteBuf b) {
                        return new Ui(
                                b.readVarInt(),
                                b.readUUID(),
                                b.readUtf(64),
                                b.readUtf(200),
                                b.readBoolean(),
                                b.readBoolean(),
                                b.readBoolean(),
                                b.readLong(),
                                b.readBoolean());
                    }

                    public void encode(RegistryFriendlyByteBuf b, Ui p) {
                        b.writeVarInt(p.mode);
                        b.writeUUID(p.instance);
                        b.writeUtf(p.console, 64);
                        b.writeUtf(p.title, 200);
                        b.writeBoolean(p.controller);
                        b.writeBoolean(p.canManage);
                        b.writeBoolean(p.inWorld);
                        b.writeLong(p.screen);
                        b.writeBoolean(p.aims);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Video(UUID instance, FrameCodec.Encoded frame) implements CustomPacketPayload {
        public static final Type<Video> ID = new Type<>(Ids.of("retrocraft", "video"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Video> CODEC =
                new StreamCodec<>() {
                    public Video decode(RegistryFriendlyByteBuf b) {
                        return new Video(
                                b.readUUID(),
                                new FrameCodec.Encoded(
                                        b.readVarLong(),
                                        b.readVarLong(),
                                        b.readBoolean(),
                                        b.readByteArray(FrameCodec.MAX_PACKET)));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Video p) {
                        b.writeUUID(p.instance);
                        b.writeVarLong(p.frame.sequence());
                        b.writeVarLong(p.frame.base());
                        b.writeBoolean(p.frame.keyframe());
                        b.writeByteArray(p.frame.data());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Audio(UUID instance, long sequence, int sampleRate, float gain, byte[] pcm)
            implements CustomPacketPayload {
        public static final Type<Audio> ID = new Type<>(Ids.of("retrocraft", "audio"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Audio> CODEC =
                new StreamCodec<>() {
                    public Audio decode(RegistryFriendlyByteBuf b) {
                        return new Audio(
                                b.readUUID(),
                                b.readVarLong(),
                                b.readVarInt(),
                                b.readFloat(),
                                b.readByteArray(WorkerProtocol.MAX_AUDIO));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Audio p) {
                        b.writeUUID(p.instance);
                        b.writeVarLong(p.sequence);
                        b.writeVarInt(p.sampleRate);
                        b.writeFloat(p.gain);
                        b.writeByteArray(p.pcm);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Catalog(
            int generation, boolean first, boolean last, List<RomCatalog.Entry> entries)
            implements CustomPacketPayload {
        public static final Type<Catalog> ID = new Type<>(Ids.of("retrocraft", "catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> CODEC =
                new StreamCodec<>() {
                    public Catalog decode(RegistryFriendlyByteBuf b) {
                        int generation = b.readVarInt();
                        boolean first = b.readBoolean(), last = b.readBoolean();
                        int size = b.readVarInt();
                        if (size < 0 || size > 128)
                            throw new IllegalArgumentException("Oversized catalog chunk");
                        List<RomCatalog.Entry> entries = new ArrayList<>(size);
                        for (int i = 0; i < size; i++)
                            entries.add(
                                    new RomCatalog.Entry(
                                            b.readUtf(200), b.readUtf(64), b.readUtf(200)));
                        return new Catalog(generation, first, last, List.copyOf(entries));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Catalog p) {
                        b.writeVarInt(p.generation);
                        b.writeBoolean(p.first);
                        b.writeBoolean(p.last);
                        b.writeVarInt(p.entries.size());
                        for (var entry : p.entries) {
                            b.writeUtf(entry.cartridge(), 200);
                            b.writeUtf(entry.gameId(), 64);
                            b.writeUtf(entry.name(), 200);
                        }
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** A tiny PNG of the latest frame on a handheld, fanned out to whoever can see the item. */
    public record Thumbnail(UUID instance, byte[] png) implements CustomPacketPayload {
        public static final Type<Thumbnail> ID = new Type<>(Ids.of("retrocraft", "thumbnail"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Thumbnail> CODEC =
                new StreamCodec<>() {
                    public Thumbnail decode(RegistryFriendlyByteBuf b) {
                        return new Thumbnail(b.readUUID(), b.readByteArray(1 << 20));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Thumbnail p) {
                        b.writeUUID(p.instance);
                        b.writeByteArray(p.png);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Hybrid owners push their own handheld preview so the server can fan it out to nearby
     * watchers.
     */
    public record ThumbnailUpload(UUID instance, byte[] png) implements CustomPacketPayload {
        public static final int MAX_BYTES = 256 * 1024;
        public static final Type<ThumbnailUpload> ID =
                new Type<>(Ids.of("retrocraft", "thumbnail_upload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ThumbnailUpload> CODEC =
                new StreamCodec<>() {
                    public ThumbnailUpload decode(RegistryFriendlyByteBuf b) {
                        return new ThumbnailUpload(b.readUUID(), b.readByteArray(MAX_BYTES));
                    }

                    public void encode(RegistryFriendlyByteBuf b, ThumbnailUpload p) {
                        b.writeUUID(p.instance);
                        b.writeByteArray(p.png);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record AudioStop(UUID instance) implements CustomPacketPayload {
        public static final Type<AudioStop> ID = new Type<>(Ids.of("retrocraft", "audio_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AudioStop> CODEC =
                new StreamCodec<>() {
                    public AudioStop decode(RegistryFriendlyByteBuf b) {
                        return new AudioStop(b.readUUID());
                    }

                    public void encode(RegistryFriendlyByteBuf b, AudioStop p) {
                        b.writeUUID(p.instance);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Full controller roster resync: which player occupies each pad slot (0-3) and who is currently
     * emulating ({@code host}, false when the server runs the core itself). Sent on every roster or
     * ownership change; a resend beats incremental enter/leave packets.
     */
    public record Players(UUID instance, List<Entry> entries) implements CustomPacketPayload {
        public record Entry(int slot, UUID player, boolean host) {}

        public static final Type<Players> ID = new Type<>(Ids.of("retrocraft", "players"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Players> CODEC =
                new StreamCodec<>() {
                    public Players decode(RegistryFriendlyByteBuf b) {
                        int count = b.readVarInt();
                        if (count < 0 || count > 4)
                            throw new IllegalArgumentException("Oversized player roster");
                        List<Entry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            entries.add(new Entry(b.readVarInt(), b.readUUID(), b.readBoolean()));
                        return new Players(b.readUUID(), List.copyOf(entries));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Players p) {
                        b.writeVarInt(p.entries.size());
                        for (var entry : p.entries) {
                            b.writeVarInt(entry.slot);
                            b.writeUUID(entry.player);
                            b.writeBoolean(entry.host);
                        }
                        b.writeUUID(p.instance);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Virtual buttons and both sticks of one roster slot, broadcast on change. */
    public record Pad(UUID instance, int slot, PadInput input) implements CustomPacketPayload {
        public Pad(UUID instance, int slot, int buttons) {
            this(instance, slot, PadInput.digital(buttons));
        }

        public static final Type<Pad> ID = new Type<>(Ids.of("retrocraft", "pad"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pad> CODEC =
                new StreamCodec<>() {
                    public Pad decode(RegistryFriendlyByteBuf b) {
                        return new Pad(b.readUUID(), b.readVarInt(), readPad(b));
                    }

                    public void encode(RegistryFriendlyByteBuf b, Pad p) {
                        b.writeUUID(p.instance);
                        b.writeVarInt(p.slot);
                        writePad(b, p.input);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Rumble for the receiving player's controller; strengths are unsigned 16-bit values. */
    public record Rumble(UUID instance, int strong, int weak) implements CustomPacketPayload {
        public static final Type<Rumble> ID = new Type<>(Ids.of("retrocraft", "rumble"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rumble> CODEC =
                new StreamCodec<>() {
                    public Rumble decode(RegistryFriendlyByteBuf b) {
                        return new Rumble(
                                b.readUUID(), b.readUnsignedShort(), b.readUnsignedShort());
                    }

                    public void encode(RegistryFriendlyByteBuf b, Rumble p) {
                        b.writeUUID(p.instance);
                        b.writeShort(p.strong);
                        b.writeShort(p.weak);
                    }
                };

        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void register() {
        Network.register(Network.Direction.TO_SERVER, Request.ID, Request.CODEC);
        Network.register(Network.Direction.TO_SERVER, Preferences.ID, Preferences.CODEC);
        Network.register(Network.Direction.TO_SERVER, ThumbnailUpload.ID, ThumbnailUpload.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Ui.ID, Ui.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Video.ID, Video.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Audio.ID, Audio.CODEC);
        Network.register(Network.Direction.TO_CLIENT, AudioStop.ID, AudioStop.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Thumbnail.ID, Thumbnail.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Catalog.ID, Catalog.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Players.ID, Players.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Pad.ID, Pad.CODEC);
        Network.register(Network.Direction.TO_CLIENT, Rumble.ID, Rumble.CODEC);
    }

    private Packets() {}
}
