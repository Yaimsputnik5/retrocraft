package com.sammwy.retrocraft.server.net;

import java.util.*;

import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Network;
import com.sammwy.retrocraft.server.config.*;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Admin settings channel: operator GUI grant, a typed config schema and ROM library management. */
public final class SettingsPackets {
    /** {@link RomAction} verbs. */
    public static final int LIST = 0,
            DELETE = 1,
            RENAME = 2,
            MOVE = 3,
            MKDIR = 4,
            RESCAN = 5,
            RESCAN_NEW = 6;

    /** {@link RomUpload} phases. */
    public static final int BEGIN = 0, CHUNK = 1, ABORT = 2;

    /**
     * {@link RomEvent} actions: ACTION mirrors the failed/finished {@link RomAction}; the rest
     * report upload progress.
     */
    public static final int ACTION = 0, UPLOAD_ACCEPT = 1, UPLOAD_DONE = 2, UPLOAD_FAIL = 3;

    /**
     * {@link GuiOpen.Entry#kind}: mirrors {@link ConfigKey.Kind} ordinals, plus CORE for cores.*
     * mappings.
     */
    public static final int INT = 0, BOOL = 1, ENUM = 2, CORE = 3;

    public static final int MAX_ENTRIES = 512,
            MAX_PATH = 300,
            MAX_NAME = 200,
            MAX_OPTIONS = 16,
            MAX_CHANGES = 64;

    /** RequestOpenSettingsGUI (C2S): asks the server for the settings screen and its schema. */
    public record GuiRequest() implements CustomPacketPayload {
        public static final Type<GuiRequest> ID = new Type<>(Ids.of("retrocraft", "gui_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GuiRequest> CODEC =
                StreamCodec.unit(new GuiRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BiosRequired(String coreId, boolean administrator)
            implements CustomPacketPayload {
        public static final Type<BiosRequired> ID =
                new Type<>(Ids.of("retrocraft", "bios_required"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BiosRequired> CODEC =
                new StreamCodec<>() {
                    public BiosRequired decode(RegistryFriendlyByteBuf b) {
                        return new BiosRequired(b.readUtf(64), b.readBoolean());
                    }

                    public void encode(RegistryFriendlyByteBuf b, BiosRequired p) {
                        b.writeUtf(p.coreId, 64);
                        b.writeBoolean(p.administrator);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * DoOpenSettingsGUI (S2C): the grant plus the whole typed config schema with current values.
     */
    public record GuiOpen(boolean granted, String reason, List<Entry> entries)
            implements CustomPacketPayload {
        public record Entry(
                String key,
                String value,
                int kind,
                int category,
                int min,
                int max,
                List<String> options) {}

        public static final Type<GuiOpen> ID = new Type<>(Ids.of("retrocraft", "gui_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GuiOpen> CODEC =
                new StreamCodec<>() {
                    public GuiOpen decode(RegistryFriendlyByteBuf b) {
                        return new GuiOpen(b.readBoolean(), b.readUtf(64), readEntries(b));
                    }

                    public void encode(RegistryFriendlyByteBuf b, GuiOpen p) {
                        b.writeBoolean(p.granted);
                        b.writeUtf(p.reason, 64);
                        writeEntries(b, p.entries);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }

        public static GuiOpen denied(String reason) {
            return new GuiOpen(false, reason, List.of());
        }

        public static GuiOpen of(RetrocraftConfig config) {
            return new GuiOpen(true, "", schema(config));
        }

        /**
         * Typed descriptor per key, so the client can render the right control without mirroring
         * validation.
         */
        public static List<Entry> schema(RetrocraftConfig config) {
            List<Entry> out = new ArrayList<>();
            for (ConfigKey key : ConfigKey.values())
                out.add(
                        new Entry(
                                key.name(),
                                key.get(config),
                                key.kind().ordinal(),
                                key.category().ordinal(),
                                key.min(),
                                key.max(),
                                key.options()));
            return List.copyOf(out);
        }
    }

    /** Batched key/value edits submitted by the GUI's Apply button. */
    public record ConfigApply(List<Change> changes) implements CustomPacketPayload {
        public record Change(String key, String value) {}

        public static final Type<ConfigApply> ID = new Type<>(Ids.of("retrocraft", "config_apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigApply> CODEC =
                new StreamCodec<>() {
                    public ConfigApply decode(RegistryFriendlyByteBuf b) {
                        int count = b.readVarInt();
                        if (count < 0 || count > MAX_CHANGES)
                            throw new IllegalArgumentException("Oversized config change set");
                        List<Change> changes = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            changes.add(new Change(b.readUtf(MAX_NAME), b.readUtf(MAX_NAME)));
                        return new ConfigApply(List.copyOf(changes));
                    }

                    public void encode(RegistryFriendlyByteBuf b, ConfigApply p) {
                        b.writeVarInt(p.changes.size());
                        for (var change : p.changes) {
                            b.writeUtf(change.key, MAX_NAME);
                            b.writeUtf(change.value, MAX_NAME);
                        }
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Result of an apply: the message is a user-facing reason on failure, plus the resynchronized
     * schema.
     */
    public record ConfigSaved(boolean ok, String message, List<GuiOpen.Entry> entries)
            implements CustomPacketPayload {
        public static final Type<ConfigSaved> ID = new Type<>(Ids.of("retrocraft", "config_saved"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSaved> CODEC =
                new StreamCodec<>() {
                    public ConfigSaved decode(RegistryFriendlyByteBuf b) {
                        return new ConfigSaved(
                                b.readBoolean(), b.readUtf(MAX_PATH), readEntries(b));
                    }

                    public void encode(RegistryFriendlyByteBuf b, ConfigSaved p) {
                        b.writeBoolean(p.ok);
                        b.writeUtf(p.message, MAX_PATH);
                        writeEntries(b, p.entries);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Area for file ops: the ROM library or the per-core BIOS tree. */
    public static final int AREA_ROMS = 0, AREA_BIOS = 1;

    /**
     * RomManagementPacket (C2S): every library mutation except uploads, correlated by {@code
     * request}.
     */
    public record RomAction(int request, int action, int area, String path, String target)
            implements CustomPacketPayload {
        public static final Type<RomAction> ID = new Type<>(Ids.of("retrocraft", "rom_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RomAction> CODEC =
                new StreamCodec<>() {
                    public RomAction decode(RegistryFriendlyByteBuf b) {
                        return new RomAction(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readUtf(MAX_PATH),
                                b.readUtf(MAX_NAME));
                    }

                    public void encode(RegistryFriendlyByteBuf b, RomAction p) {
                        b.writeVarInt(p.request);
                        b.writeVarInt(p.action);
                        b.writeVarInt(p.area);
                        b.writeUtf(p.path, MAX_PATH);
                        b.writeUtf(p.target, MAX_NAME);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }

        public static RomAction of(int request, int action, String path) {
            return new RomAction(request, action, AREA_ROMS, path, "");
        }
    }

    /** A directory listing of one roms/ folder (S2C, answer to {@link RomAction#LIST}). */
    public record RomList(int request, String path, boolean truncated, List<Entry> entries)
            implements CustomPacketPayload {
        public record Entry(String name, boolean directory, long size) {}

        public static final Type<RomList> ID = new Type<>(Ids.of("retrocraft", "rom_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RomList> CODEC =
                new StreamCodec<>() {
                    public RomList decode(RegistryFriendlyByteBuf b) {
                        int request = b.readVarInt();
                        String path = b.readUtf(MAX_PATH);
                        boolean truncated = b.readBoolean();
                        int count = b.readVarInt();
                        if (count < 0 || count > MAX_ENTRIES)
                            throw new IllegalArgumentException("Oversized ROM listing");
                        List<Entry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            entries.add(
                                    new Entry(
                                            b.readUtf(MAX_NAME), b.readBoolean(), b.readVarLong()));
                        return new RomList(request, path, truncated, List.copyOf(entries));
                    }

                    public void encode(RegistryFriendlyByteBuf b, RomList p) {
                        b.writeVarInt(p.request);
                        b.writeUtf(p.path, MAX_PATH);
                        b.writeBoolean(p.truncated);
                        b.writeVarInt(p.entries.size());
                        for (var entry : p.entries) {
                            b.writeUtf(entry.name, MAX_NAME);
                            b.writeBoolean(entry.directory);
                            b.writeVarLong(entry.size);
                        }
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Outcome of a {@link RomAction} or an upload milestone (S2C). */
    public record RomEvent(int request, int action, boolean ok, String message)
            implements CustomPacketPayload {
        public static final Type<RomEvent> ID = new Type<>(Ids.of("retrocraft", "rom_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RomEvent> CODEC =
                new StreamCodec<>() {
                    public RomEvent decode(RegistryFriendlyByteBuf b) {
                        return new RomEvent(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readBoolean(),
                                b.readUtf(MAX_PATH));
                    }

                    public void encode(RegistryFriendlyByteBuf b, RomEvent p) {
                        b.writeVarInt(p.request);
                        b.writeVarInt(p.action);
                        b.writeBoolean(p.ok);
                        b.writeUtf(p.message, MAX_PATH);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }

        public static RomEvent fail(int request, String message) {
            return new RomEvent(request, ACTION, false, message);
        }
    }

    /**
     * Chunked ROM upload (C2S). Chunks fit the 32 KiB serverbound payload limit, same budget as
     * {@link BlobTransfer}.
     */
    public record RomUpload(
            int id,
            int phase,
            int area,
            String dir,
            String name,
            long total,
            int offset,
            String sha256,
            byte[] data)
            implements CustomPacketPayload {
        public static final Type<RomUpload> ID = new Type<>(Ids.of("retrocraft", "rom_upload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RomUpload> CODEC =
                new StreamCodec<>() {
                    public RomUpload decode(RegistryFriendlyByteBuf b) {
                        return new RomUpload(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readUtf(MAX_PATH),
                                b.readUtf(MAX_NAME),
                                b.readVarLong(),
                                b.readVarInt(),
                                b.readUtf(64),
                                b.readByteArray(BlobTransfer.CHUNK));
                    }

                    public void encode(RegistryFriendlyByteBuf b, RomUpload p) {
                        b.writeVarInt(p.id);
                        b.writeVarInt(p.phase);
                        b.writeVarInt(p.area);
                        b.writeUtf(p.dir, MAX_PATH);
                        b.writeUtf(p.name, MAX_NAME);
                        b.writeVarLong(p.total);
                        b.writeVarInt(p.offset);
                        b.writeUtf(p.sha256, 64);
                        b.writeByteArray(p.data);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Library rescan lifecycle broadcast so admin UIs can show who triggered it (S2C). */
    public record ScanEvent(String actor, boolean scanning) implements CustomPacketPayload {
        public static final Type<ScanEvent> ID = new Type<>(Ids.of("retrocraft", "scan_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScanEvent> CODEC =
                StreamCodec.ofMember(
                        (value, buf) -> {
                            buf.writeUtf(value.actor, MAX_NAME);
                            buf.writeBoolean(value.scanning);
                        },
                        buf -> new ScanEvent(buf.readUtf(MAX_NAME), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * One chunk of a BIOS file pushed server→client so client-emulated cores find their firmware
     * (S2C).
     */
    public record BiosChunk(
            String coreId, String name, int offset, long total, String sha256, byte[] data)
            implements CustomPacketPayload {
        public static final Type<BiosChunk> ID = new Type<>(Ids.of("retrocraft", "bios_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BiosChunk> CODEC =
                new StreamCodec<>() {
                    public BiosChunk decode(RegistryFriendlyByteBuf b) {
                        return new BiosChunk(
                                b.readUtf(MAX_NAME),
                                b.readUtf(MAX_NAME),
                                b.readVarInt(),
                                b.readVarLong(),
                                b.readUtf(64),
                                b.readByteArray(BlobTransfer.CHUNK));
                    }

                    public void encode(RegistryFriendlyByteBuf b, BiosChunk p) {
                        b.writeUtf(p.coreId, MAX_NAME);
                        b.writeUtf(p.name, MAX_NAME);
                        b.writeVarInt(p.offset);
                        b.writeVarLong(p.total);
                        b.writeUtf(p.sha256, 64);
                        b.writeByteArray(p.data);
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BiosManifest(int generation, boolean first, boolean last, List<Entry> entries)
            implements CustomPacketPayload {
        public record Entry(String coreId, String name, long size, String sha256) {}

        public static final Type<BiosManifest> ID =
                new Type<>(Ids.of("retrocraft", "bios_manifest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BiosManifest> CODEC =
                new StreamCodec<>() {
                    public BiosManifest decode(RegistryFriendlyByteBuf b) {
                        int generation = b.readVarInt();
                        boolean first = b.readBoolean(), last = b.readBoolean();
                        int count = b.readVarInt();
                        if (count < 0 || count > 128)
                            throw new IllegalArgumentException("Oversized BIOS manifest");
                        List<Entry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            entries.add(
                                    new Entry(
                                            b.readUtf(64),
                                            b.readUtf(MAX_NAME),
                                            b.readVarLong(),
                                            b.readUtf(64)));
                        return new BiosManifest(generation, first, last, List.copyOf(entries));
                    }

                    public void encode(RegistryFriendlyByteBuf b, BiosManifest p) {
                        b.writeVarInt(p.generation);
                        b.writeBoolean(p.first);
                        b.writeBoolean(p.last);
                        b.writeVarInt(p.entries.size());
                        for (Entry entry : p.entries) {
                            b.writeUtf(entry.coreId, 64);
                            b.writeUtf(entry.name, MAX_NAME);
                            b.writeVarLong(entry.size);
                            b.writeUtf(entry.sha256, 64);
                        }
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BiosRequest(String coreId, String name, String sha256)
            implements CustomPacketPayload {
        public static final Type<BiosRequest> ID = new Type<>(Ids.of("retrocraft", "bios_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BiosRequest> CODEC =
                StreamCodec.ofMember(
                        (p, b) -> {
                            b.writeUtf(p.coreId, 64);
                            b.writeUtf(p.name, MAX_NAME);
                            b.writeUtf(p.sha256, 64);
                        },
                        b -> new BiosRequest(b.readUtf(64), b.readUtf(MAX_NAME), b.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BiosUnavailable(String coreId, String name, String sha256)
            implements CustomPacketPayload {
        public static final Type<BiosUnavailable> ID =
                new Type<>(Ids.of("retrocraft", "bios_unavailable"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BiosUnavailable> CODEC =
                StreamCodec.ofMember(
                        (p, b) -> {
                            b.writeUtf(p.coreId, 64);
                            b.writeUtf(p.name, MAX_NAME);
                            b.writeUtf(p.sha256, 64);
                        },
                        b ->
                                new BiosUnavailable(
                                        b.readUtf(64), b.readUtf(MAX_NAME), b.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BiosReady(int generation) implements CustomPacketPayload {
        public static final Type<BiosReady> ID = new Type<>(Ids.of("retrocraft", "bios_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BiosReady> CODEC =
                StreamCodec.ofMember(
                        (p, b) -> b.writeVarInt(p.generation), b -> new BiosReady(b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private static List<GuiOpen.Entry> readEntries(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 256)
            throw new IllegalArgumentException("Oversized settings schema");
        List<GuiOpen.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String key = b.readUtf(MAX_NAME), value = b.readUtf(MAX_NAME);
            int kind = b.readVarInt(),
                    category = b.readVarInt(),
                    min = b.readVarInt(),
                    max = b.readVarInt();
            int options = b.readVarInt();
            if (options < 0 || options > MAX_OPTIONS)
                throw new IllegalArgumentException("Oversized option list");
            List<String> choices = new ArrayList<>(options);
            for (int j = 0; j < options; j++) choices.add(b.readUtf(64));
            entries.add(
                    new GuiOpen.Entry(key, value, kind, category, min, max, List.copyOf(choices)));
        }
        return List.copyOf(entries);
    }

    private static void writeEntries(RegistryFriendlyByteBuf b, List<GuiOpen.Entry> entries) {
        b.writeVarInt(entries.size());
        for (var entry : entries) {
            b.writeUtf(entry.key, MAX_NAME);
            b.writeUtf(entry.value, MAX_NAME);
            b.writeVarInt(entry.kind);
            b.writeVarInt(entry.category);
            b.writeVarInt(entry.min);
            b.writeVarInt(entry.max);
            b.writeVarInt(entry.options.size());
            for (String option : entry.options) b.writeUtf(option, 64);
        }
    }

    public static void register() {
        Network.register(Network.Direction.TO_SERVER, GuiRequest.ID, GuiRequest.CODEC);
        Network.register(Network.Direction.TO_SERVER, ConfigApply.ID, ConfigApply.CODEC);
        Network.register(Network.Direction.TO_SERVER, RomAction.ID, RomAction.CODEC);
        Network.register(Network.Direction.TO_SERVER, RomUpload.ID, RomUpload.CODEC);
        Network.register(Network.Direction.TO_SERVER, BiosRequest.ID, BiosRequest.CODEC);
        Network.register(Network.Direction.TO_SERVER, BiosReady.ID, BiosReady.CODEC);
        Network.register(Network.Direction.TO_CLIENT, GuiOpen.ID, GuiOpen.CODEC);
        Network.register(Network.Direction.TO_CLIENT, BiosRequired.ID, BiosRequired.CODEC);
        Network.register(Network.Direction.TO_CLIENT, ConfigSaved.ID, ConfigSaved.CODEC);
        Network.register(Network.Direction.TO_CLIENT, RomList.ID, RomList.CODEC);
        Network.register(Network.Direction.TO_CLIENT, RomEvent.ID, RomEvent.CODEC);
        Network.register(Network.Direction.TO_CLIENT, BiosChunk.ID, BiosChunk.CODEC);
        Network.register(Network.Direction.TO_CLIENT, BiosManifest.ID, BiosManifest.CODEC);
        Network.register(Network.Direction.TO_CLIENT, BiosUnavailable.ID, BiosUnavailable.CODEC);
        Network.register(Network.Direction.TO_CLIENT, ScanEvent.ID, ScanEvent.CODEC);
    }

    private SettingsPackets() {}
}
