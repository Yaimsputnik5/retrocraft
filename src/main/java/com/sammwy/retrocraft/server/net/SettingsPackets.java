package com.sammwy.retrocraft.server.net;

import java.util.*;

import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import com.sammwy.retrocraft.server.config.*;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

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
    public record GuiRequest() implements CustomPayload {
        public static final Id<GuiRequest> ID =
                new Id<>(Identifier.of("retrocraft", "gui_request"));
        public static final PacketCodec<RegistryByteBuf, GuiRequest> CODEC =
                PacketCodec.unit(new GuiRequest());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BiosRequired(String coreId, boolean administrator) implements CustomPayload {
        public static final Id<BiosRequired> ID =
                new Id<>(Identifier.of("retrocraft", "bios_required"));
        public static final PacketCodec<RegistryByteBuf, BiosRequired> CODEC =
                new PacketCodec<>() {
                    public BiosRequired decode(RegistryByteBuf b) {
                        return new BiosRequired(b.readString(64), b.readBoolean());
                    }

                    public void encode(RegistryByteBuf b, BiosRequired p) {
                        b.writeString(p.coreId, 64);
                        b.writeBoolean(p.administrator);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * DoOpenSettingsGUI (S2C): the grant plus the whole typed config schema with current values.
     */
    public record GuiOpen(boolean granted, String reason, List<Entry> entries)
            implements CustomPayload {
        public record Entry(
                String key,
                String value,
                int kind,
                int category,
                int min,
                int max,
                List<String> options) {}

        public static final Id<GuiOpen> ID = new Id<>(Identifier.of("retrocraft", "gui_open"));
        public static final PacketCodec<RegistryByteBuf, GuiOpen> CODEC =
                new PacketCodec<>() {
                    public GuiOpen decode(RegistryByteBuf b) {
                        return new GuiOpen(b.readBoolean(), b.readString(64), readEntries(b));
                    }

                    public void encode(RegistryByteBuf b, GuiOpen p) {
                        b.writeBoolean(p.granted);
                        b.writeString(p.reason, 64);
                        writeEntries(b, p.entries);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
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
    public record ConfigApply(List<Change> changes) implements CustomPayload {
        public record Change(String key, String value) {}

        public static final Id<ConfigApply> ID =
                new Id<>(Identifier.of("retrocraft", "config_apply"));
        public static final PacketCodec<RegistryByteBuf, ConfigApply> CODEC =
                new PacketCodec<>() {
                    public ConfigApply decode(RegistryByteBuf b) {
                        int count = b.readVarInt();
                        if (count < 0 || count > MAX_CHANGES)
                            throw new IllegalArgumentException("Oversized config change set");
                        List<Change> changes = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            changes.add(new Change(b.readString(MAX_NAME), b.readString(MAX_NAME)));
                        return new ConfigApply(List.copyOf(changes));
                    }

                    public void encode(RegistryByteBuf b, ConfigApply p) {
                        b.writeVarInt(p.changes.size());
                        for (var change : p.changes) {
                            b.writeString(change.key, MAX_NAME);
                            b.writeString(change.value, MAX_NAME);
                        }
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Result of an apply: the message is a user-facing reason on failure, plus the resynchronized
     * schema.
     */
    public record ConfigSaved(boolean ok, String message, List<GuiOpen.Entry> entries)
            implements CustomPayload {
        public static final Id<ConfigSaved> ID =
                new Id<>(Identifier.of("retrocraft", "config_saved"));
        public static final PacketCodec<RegistryByteBuf, ConfigSaved> CODEC =
                new PacketCodec<>() {
                    public ConfigSaved decode(RegistryByteBuf b) {
                        return new ConfigSaved(
                                b.readBoolean(), b.readString(MAX_PATH), readEntries(b));
                    }

                    public void encode(RegistryByteBuf b, ConfigSaved p) {
                        b.writeBoolean(p.ok);
                        b.writeString(p.message, MAX_PATH);
                        writeEntries(b, p.entries);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
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
            implements CustomPayload {
        public static final Id<RomAction> ID = new Id<>(Identifier.of("retrocraft", "rom_action"));
        public static final PacketCodec<RegistryByteBuf, RomAction> CODEC =
                new PacketCodec<>() {
                    public RomAction decode(RegistryByteBuf b) {
                        return new RomAction(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readString(MAX_PATH),
                                b.readString(MAX_NAME));
                    }

                    public void encode(RegistryByteBuf b, RomAction p) {
                        b.writeVarInt(p.request);
                        b.writeVarInt(p.action);
                        b.writeVarInt(p.area);
                        b.writeString(p.path, MAX_PATH);
                        b.writeString(p.target, MAX_NAME);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static RomAction of(int request, int action, String path) {
            return new RomAction(request, action, AREA_ROMS, path, "");
        }
    }

    /** A directory listing of one roms/ folder (S2C, answer to {@link RomAction#LIST}). */
    public record RomList(int request, String path, boolean truncated, List<Entry> entries)
            implements CustomPayload {
        public record Entry(String name, boolean directory, long size) {}

        public static final Id<RomList> ID = new Id<>(Identifier.of("retrocraft", "rom_list"));
        public static final PacketCodec<RegistryByteBuf, RomList> CODEC =
                new PacketCodec<>() {
                    public RomList decode(RegistryByteBuf b) {
                        int request = b.readVarInt();
                        String path = b.readString(MAX_PATH);
                        boolean truncated = b.readBoolean();
                        int count = b.readVarInt();
                        if (count < 0 || count > MAX_ENTRIES)
                            throw new IllegalArgumentException("Oversized ROM listing");
                        List<Entry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            entries.add(
                                    new Entry(
                                            b.readString(MAX_NAME),
                                            b.readBoolean(),
                                            b.readVarLong()));
                        return new RomList(request, path, truncated, List.copyOf(entries));
                    }

                    public void encode(RegistryByteBuf b, RomList p) {
                        b.writeVarInt(p.request);
                        b.writeString(p.path, MAX_PATH);
                        b.writeBoolean(p.truncated);
                        b.writeVarInt(p.entries.size());
                        for (var entry : p.entries) {
                            b.writeString(entry.name, MAX_NAME);
                            b.writeBoolean(entry.directory);
                            b.writeVarLong(entry.size);
                        }
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Outcome of a {@link RomAction} or an upload milestone (S2C). */
    public record RomEvent(int request, int action, boolean ok, String message)
            implements CustomPayload {
        public static final Id<RomEvent> ID = new Id<>(Identifier.of("retrocraft", "rom_event"));
        public static final PacketCodec<RegistryByteBuf, RomEvent> CODEC =
                new PacketCodec<>() {
                    public RomEvent decode(RegistryByteBuf b) {
                        return new RomEvent(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readBoolean(),
                                b.readString(MAX_PATH));
                    }

                    public void encode(RegistryByteBuf b, RomEvent p) {
                        b.writeVarInt(p.request);
                        b.writeVarInt(p.action);
                        b.writeBoolean(p.ok);
                        b.writeString(p.message, MAX_PATH);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
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
            implements CustomPayload {
        public static final Id<RomUpload> ID = new Id<>(Identifier.of("retrocraft", "rom_upload"));
        public static final PacketCodec<RegistryByteBuf, RomUpload> CODEC =
                new PacketCodec<>() {
                    public RomUpload decode(RegistryByteBuf b) {
                        return new RomUpload(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readString(MAX_PATH),
                                b.readString(MAX_NAME),
                                b.readVarLong(),
                                b.readVarInt(),
                                b.readString(64),
                                b.readByteArray(BlobTransfer.CHUNK));
                    }

                    public void encode(RegistryByteBuf b, RomUpload p) {
                        b.writeVarInt(p.id);
                        b.writeVarInt(p.phase);
                        b.writeVarInt(p.area);
                        b.writeString(p.dir, MAX_PATH);
                        b.writeString(p.name, MAX_NAME);
                        b.writeVarLong(p.total);
                        b.writeVarInt(p.offset);
                        b.writeString(p.sha256, 64);
                        b.writeByteArray(p.data);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Library rescan lifecycle broadcast so admin UIs can show who triggered it (S2C). */
    public record ScanEvent(String actor, boolean scanning) implements CustomPayload {
        public static final Id<ScanEvent> ID = new Id<>(Identifier.of("retrocraft", "scan_event"));
        public static final PacketCodec<RegistryByteBuf, ScanEvent> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeString(value.actor, MAX_NAME);
                            buf.writeBoolean(value.scanning);
                        },
                        buf -> new ScanEvent(buf.readString(MAX_NAME), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * One chunk of a BIOS file pushed server→client so client-emulated cores find their firmware
     * (S2C).
     */
    public record BiosChunk(
            String coreId, String name, int offset, long total, String sha256, byte[] data)
            implements CustomPayload {
        public static final Id<BiosChunk> ID = new Id<>(Identifier.of("retrocraft", "bios_chunk"));
        public static final PacketCodec<RegistryByteBuf, BiosChunk> CODEC =
                new PacketCodec<>() {
                    public BiosChunk decode(RegistryByteBuf b) {
                        return new BiosChunk(
                                b.readString(MAX_NAME),
                                b.readString(MAX_NAME),
                                b.readVarInt(),
                                b.readVarLong(),
                                b.readString(64),
                                b.readByteArray(BlobTransfer.CHUNK));
                    }

                    public void encode(RegistryByteBuf b, BiosChunk p) {
                        b.writeString(p.coreId, MAX_NAME);
                        b.writeString(p.name, MAX_NAME);
                        b.writeVarInt(p.offset);
                        b.writeVarLong(p.total);
                        b.writeString(p.sha256, 64);
                        b.writeByteArray(p.data);
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BiosManifest(int generation, boolean first, boolean last, List<Entry> entries)
            implements CustomPayload {
        public record Entry(String coreId, String name, long size, String sha256) {}

        public static final Id<BiosManifest> ID =
                new Id<>(Identifier.of("retrocraft", "bios_manifest"));
        public static final PacketCodec<RegistryByteBuf, BiosManifest> CODEC =
                new PacketCodec<>() {
                    public BiosManifest decode(RegistryByteBuf b) {
                        int generation = b.readVarInt();
                        boolean first = b.readBoolean(), last = b.readBoolean();
                        int count = b.readVarInt();
                        if (count < 0 || count > 128)
                            throw new IllegalArgumentException("Oversized BIOS manifest");
                        List<Entry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            entries.add(
                                    new Entry(
                                            b.readString(64),
                                            b.readString(MAX_NAME),
                                            b.readVarLong(),
                                            b.readString(64)));
                        return new BiosManifest(generation, first, last, List.copyOf(entries));
                    }

                    public void encode(RegistryByteBuf b, BiosManifest p) {
                        b.writeVarInt(p.generation);
                        b.writeBoolean(p.first);
                        b.writeBoolean(p.last);
                        b.writeVarInt(p.entries.size());
                        for (Entry entry : p.entries) {
                            b.writeString(entry.coreId, 64);
                            b.writeString(entry.name, MAX_NAME);
                            b.writeVarLong(entry.size);
                            b.writeString(entry.sha256, 64);
                        }
                    }
                };

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BiosRequest(String coreId, String name, String sha256) implements CustomPayload {
        public static final Id<BiosRequest> ID =
                new Id<>(Identifier.of("retrocraft", "bios_request"));
        public static final PacketCodec<RegistryByteBuf, BiosRequest> CODEC =
                PacketCodec.of(
                        (p, b) -> {
                            b.writeString(p.coreId, 64);
                            b.writeString(p.name, MAX_NAME);
                            b.writeString(p.sha256, 64);
                        },
                        b ->
                                new BiosRequest(
                                        b.readString(64),
                                        b.readString(MAX_NAME),
                                        b.readString(64)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BiosUnavailable(String coreId, String name, String sha256)
            implements CustomPayload {
        public static final Id<BiosUnavailable> ID =
                new Id<>(Identifier.of("retrocraft", "bios_unavailable"));
        public static final PacketCodec<RegistryByteBuf, BiosUnavailable> CODEC =
                PacketCodec.of(
                        (p, b) -> {
                            b.writeString(p.coreId, 64);
                            b.writeString(p.name, MAX_NAME);
                            b.writeString(p.sha256, 64);
                        },
                        b ->
                                new BiosUnavailable(
                                        b.readString(64),
                                        b.readString(MAX_NAME),
                                        b.readString(64)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BiosReady(int generation) implements CustomPayload {
        public static final Id<BiosReady> ID = new Id<>(Identifier.of("retrocraft", "bios_ready"));
        public static final PacketCodec<RegistryByteBuf, BiosReady> CODEC =
                PacketCodec.of(
                        (p, b) -> b.writeVarInt(p.generation), b -> new BiosReady(b.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static List<GuiOpen.Entry> readEntries(RegistryByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 256)
            throw new IllegalArgumentException("Oversized settings schema");
        List<GuiOpen.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String key = b.readString(MAX_NAME), value = b.readString(MAX_NAME);
            int kind = b.readVarInt(),
                    category = b.readVarInt(),
                    min = b.readVarInt(),
                    max = b.readVarInt();
            int options = b.readVarInt();
            if (options < 0 || options > MAX_OPTIONS)
                throw new IllegalArgumentException("Oversized option list");
            List<String> choices = new ArrayList<>(options);
            for (int j = 0; j < options; j++) choices.add(b.readString(64));
            entries.add(
                    new GuiOpen.Entry(key, value, kind, category, min, max, List.copyOf(choices)));
        }
        return List.copyOf(entries);
    }

    private static void writeEntries(RegistryByteBuf b, List<GuiOpen.Entry> entries) {
        b.writeVarInt(entries.size());
        for (var entry : entries) {
            b.writeString(entry.key, MAX_NAME);
            b.writeString(entry.value, MAX_NAME);
            b.writeVarInt(entry.kind);
            b.writeVarInt(entry.category);
            b.writeVarInt(entry.min);
            b.writeVarInt(entry.max);
            b.writeVarInt(entry.options.size());
            for (String option : entry.options) b.writeString(option, 64);
        }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(GuiRequest.ID, GuiRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigApply.ID, ConfigApply.CODEC);
        PayloadTypeRegistry.playC2S().register(RomAction.ID, RomAction.CODEC);
        PayloadTypeRegistry.playC2S().register(RomUpload.ID, RomUpload.CODEC);
        PayloadTypeRegistry.playC2S().register(BiosRequest.ID, BiosRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(BiosReady.ID, BiosReady.CODEC);
        PayloadTypeRegistry.playS2C().register(GuiOpen.ID, GuiOpen.CODEC);
        PayloadTypeRegistry.playS2C().register(BiosRequired.ID, BiosRequired.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSaved.ID, ConfigSaved.CODEC);
        PayloadTypeRegistry.playS2C().register(RomList.ID, RomList.CODEC);
        PayloadTypeRegistry.playS2C().register(RomEvent.ID, RomEvent.CODEC);
        PayloadTypeRegistry.playS2C().register(BiosChunk.ID, BiosChunk.CODEC);
        PayloadTypeRegistry.playS2C().register(BiosManifest.ID, BiosManifest.CODEC);
        PayloadTypeRegistry.playS2C().register(BiosUnavailable.ID, BiosUnavailable.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanEvent.ID, ScanEvent.CODEC);
    }

    private SettingsPackets() {}
}
