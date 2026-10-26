package com.sammwy.retrocraft.roms;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import com.sammwy.retrocraft.roms.discovery.ArcadeSets;

/** The BIOS manager, sync protocol and native core must use the same firmware directory. */
public final class BiosFiles {
    public static final class MissingException extends IOException {
        private final String coreId;
        private final String biosId;
        private final String biosName;

        public MissingException(String message, String coreId, String biosId, String biosName) {
            super(message);
            this.coreId = coreId;
            this.biosId = biosId;
            this.biosName = biosName;
        }

        public String coreId() {
            return coreId;
        }

        public String biosId() {
            return biosId;
        }

        public String biosName() {
            return biosName;
        }

        public String packetTarget() {
            return biosId == null || biosId.isBlank() ? coreId : coreId + "/" + biosId;
        }
    }

    private static final List<String> NEOGEO_BIOS_FALLBACKS =
            List.of(
                    "sp-s2.sp1",
                    "sp-s.sp1",
                    "sp-e.sp1",
                    "asia-s3.rom",
                    "japan-j3.bin",
                    "vs-bios.rom",
                    "sp-j2.sp1",
                    "sp-j2.rom",
                    "sp-45.sp1",
                    "uni-bios_3_3.rom",
                    "uni-bios_2_3.rom",
                    "uni-bios_2_0.rom",
                    "uni-bios_1_3.rom",
                    "uni-bios_1_1.rom",
                    "uni-bios_1_0.rom");

    public static String directory(String coreId) {
        return coreId.equals("pcsx2") ? "pcsx2/bios" : coreId;
    }

    /** Accept older UI requests as well as the canonical paths sent by current clients. */
    public static String canonical(String relative) {
        if (relative.equals("pcsx2")) return directory("pcsx2");
        if (relative.startsWith("pcsx2/")
                && !relative.equals("pcsx2/bios")
                && !relative.startsWith("pcsx2/bios/"))
            return "pcsx2/bios/" + relative.substring("pcsx2/".length());
        return relative;
    }

    /** Recover BIOS files uploaded by the old UI without replacing any installed firmware. */
    public static synchronized void migrateLegacy(Path root) throws IOException {
        Path legacy = root.resolve("pcsx2");
        if (!Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) return;
        RomLibrary library = new RomLibrary(root);
        for (var entry : library.list("pcsx2").entries()) {
            if (entry.directory() || !RomLibrary.safeName(entry.name())) continue;
            Path destination = root.resolve(directory("pcsx2")).resolve(entry.name());
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) continue;
            library.destination(directory("pcsx2"), entry.name());
            library.move("pcsx2/" + entry.name(), directory("pcsx2"));
        }
    }

    /**
     * Ensures Neo-Geo BIOS zips include both MAME 2003 (0.78) and modern MAME member names
     * (sfix.sfx / sfix.sfix, sm1.sm1 / mame.sm1, 000-lo.lo / mamelo.lo, sp-s2.sp1).
     */
    public static byte[] normalizeArcadeBios(String filename, byte[] bytes) {
        ArcadeSets.Bios bios = ArcadeSets.bios(filename);
        if (bios == null || !bios.id().equals("neogeo") || bytes == null || bytes.length < 4)
            return bytes;
        var entries = new LinkedHashMap<String, byte[]>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String base = entry.getName().replace('\\', '/');
                base = base.substring(base.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (!base.isEmpty()) entries.put(base, RomArchives.bounded(in, 16L * 1024 * 1024));
            }
        } catch (IOException ex) {
            return bytes;
        }
        if (entries.isEmpty()) return bytes;
        boolean modified = addNeoGeoAliases(entries);
        return modified ? packZip(entries) : bytes;
    }

    private static boolean addNeoGeoAliases(Map<String, byte[]> entries) {
        boolean modified = false;
        if (!entries.containsKey("sfix.sfx") && entries.containsKey("sfix.sfix")) {
            byte[] sfix = entries.get("sfix.sfix");
            entries.put("sfix.sfx", sfix.length > 65536 ? Arrays.copyOf(sfix, 65536) : sfix);
            modified = true;
        }
        if (!entries.containsKey("sfix.sfix") && entries.containsKey("sfix.sfx")) {
            entries.put("sfix.sfix", entries.get("sfix.sfx"));
            modified = true;
        }
        if (!entries.containsKey("mame.sm1") && entries.containsKey("sm1.sm1")) {
            entries.put("mame.sm1", entries.get("sm1.sm1"));
            modified = true;
        }
        if (!entries.containsKey("sm1.sm1") && entries.containsKey("mame.sm1")) {
            entries.put("sm1.sm1", entries.get("mame.sm1"));
            modified = true;
        }
        if (!entries.containsKey("mamelo.lo") && entries.containsKey("000-lo.lo")) {
            byte[] lo = entries.get("000-lo.lo");
            entries.put("mamelo.lo", lo.length > 65536 ? Arrays.copyOf(lo, 65536) : lo);
            modified = true;
        }
        if (!entries.containsKey("000-lo.lo") && entries.containsKey("mamelo.lo")) {
            entries.put("000-lo.lo", entries.get("mamelo.lo"));
            modified = true;
        }
        if (!entries.containsKey("sp-s2.sp1")) {
            for (String candidate : NEOGEO_BIOS_FALLBACKS) {
                if (entries.containsKey(candidate)) {
                    entries.put("sp-s2.sp1", entries.get(candidate));
                    modified = true;
                    break;
                }
            }
        }
        return modified;
    }

    private static byte[] packZip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (var entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zip.putNextEntry(ze);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    /**
     * Extracts a MAME BIOS archive from a merged ROM or BIOS zip if all required BIOS members are
     * present inside it; returns {@code null} otherwise.
     */
    public static byte[] extractArcadeBios(Path zipFile, ArcadeSets.Bios bios) {
        if (bios == null || zipFile == null || !Files.isRegularFile(zipFile)) return null;
        Set<String> allowed = new HashSet<>(bios.members());
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            Map<String, ZipEntry> found = new LinkedHashMap<>();
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.isDirectory() || ze.getSize() <= 0 || ze.getSize() > 16L * 1024 * 1024)
                    continue;
                String base = ze.getName().replace('\\', '/');
                base = base.substring(base.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (allowed.contains(base)) found.putIfAbsent(base, ze);
            }
            if (!hasRequiredMembers(bios, found.keySet())) return null;
            var extracted = new LinkedHashMap<String, byte[]>();
            for (var entry : found.entrySet()) {
                try (InputStream in = zf.getInputStream(entry.getValue())) {
                    extracted.put(entry.getKey(), RomArchives.bounded(in, 16L * 1024 * 1024));
                }
            }
            if (bios.id().equals("neogeo")) addNeoGeoAliases(extracted);
            return packZip(extracted);
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean hasRequiredMembers(ArcadeSets.Bios bios, Set<String> present) {
        if (present.isEmpty()) return false;
        if (bios.id().equals("neogeo")) {
            boolean hasLo = present.contains("000-lo.lo") || present.contains("mamelo.lo");
            boolean hasSm1 = present.contains("sm1.sm1") || present.contains("mame.sm1");
            boolean hasSfix = present.contains("sfix.sfx") || present.contains("sfix.sfix");
            boolean hasSys = NEOGEO_BIOS_FALLBACKS.stream().anyMatch(present::contains);
            return hasLo && hasSm1 && hasSfix && hasSys;
        }
        return !bios.members().isEmpty() && bios.members().stream().anyMatch(present::contains);
    }

    /**
     * Verifies that any BIOS required by {@code romFile} is installed and staged alongside the ROM
     * so MAME 2003 can open {@code <bios>.zip} from its content directory.
     */
    public static synchronized void ensureArcadeBios(Path biosDir, Path romFile)
            throws IOException {
        if (romFile == null) return;
        ArcadeSets.Bios bios = ArcadeSets.requiredBios(romFile.getFileName().toString());
        if (bios == null) return;
        Path romDir = romFile.getParent();
        Path inBiosDir = biosDir.resolve(bios.zip());
        Path inRomDir = romDir != null ? romDir.resolve(bios.zip()) : null;
        if (Files.isRegularFile(inBiosDir)) {
            byte[] raw = Files.readAllBytes(inBiosDir);
            byte[] normalized = normalizeArcadeBios(bios.zip(), raw);
            if (!Arrays.equals(raw, normalized)) SaveStates.writeAtomic(inBiosDir, normalized);
            if (inRomDir != null
                    && (!Files.isRegularFile(inRomDir)
                            || Files.size(inRomDir) != normalized.length))
                SaveStates.writeAtomic(inRomDir, normalized);
            return;
        }
        if (inRomDir != null && Files.isRegularFile(inRomDir)) {
            byte[] raw = Files.readAllBytes(inRomDir);
            byte[] normalized = normalizeArcadeBios(bios.zip(), raw);
            if (!Arrays.equals(raw, normalized)) SaveStates.writeAtomic(inRomDir, normalized);
            SaveStates.writeAtomic(inBiosDir, normalized);
            return;
        }
        byte[] extracted = extractArcadeBios(romFile, bios);
        if (extracted == null && romDir != null && Files.isDirectory(romDir)) {
            try (var files = Files.list(romDir)) {
                for (Path candidate :
                        files.filter(
                                        p ->
                                                Files.isRegularFile(p)
                                                        && p.getFileName()
                                                                .toString()
                                                                .toLowerCase(Locale.ROOT)
                                                                .endsWith(".zip"))
                                .toList()) {
                    if (candidate.equals(romFile)) continue;
                    extracted = extractArcadeBios(candidate, bios);
                    if (extracted != null) break;
                }
            }
        }
        if (extracted != null) {
            SaveStates.writeAtomic(inBiosDir, extracted);
            if (inRomDir != null) SaveStates.writeAtomic(inRomDir, extracted);
            return;
        }
        throw new MissingException(
                "Arcade BIOS missing: " + bios.zip(),
                "mame2003",
                bios.id(),
                bios.zip() + " (" + bios.title() + ")");
    }

    private BiosFiles() {}
}
