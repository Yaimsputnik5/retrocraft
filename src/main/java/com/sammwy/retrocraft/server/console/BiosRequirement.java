package com.sammwy.retrocraft.server.console;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

import com.sammwy.retrocraft.roms.BiosFiles;
import com.sammwy.retrocraft.roms.RomIndex;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;

/** Firmware a system needs before power-on, checked in the selected core's BIOS directory. */
@FunctionalInterface
public interface BiosRequirement {
    BiosRequirement NONE = (core, game) -> {};

    /** Arcade boards: the romset's parent BIOS (e.g. neogeo.zip) must be installed. */
    BiosRequirement ARCADE_SET =
            (core, game) -> {
                if (game != null)
                    BiosFiles.ensureArcadeBios(RetrocraftConfig.biosDir(core), game.materialize());
            };

    /** At least one firmware image with this extension, e.g. a PS2 BIOS dump. */
    static BiosRequirement anyFile(String label, String extension) {
        String suffix = extension.toLowerCase(Locale.ROOT);
        return (core, game) -> {
            BiosFiles.migrateLegacy(RetrocraftConfig.root().resolve("bios"));
            Path bios = RetrocraftConfig.biosDir(core);
            if (Files.isDirectory(bios))
                try (var files = Files.list(bios)) {
                    if (files.anyMatch(
                            path ->
                                    Files.isRegularFile(path)
                                            && path.getFileName()
                                                    .toString()
                                                    .toLowerCase(Locale.ROOT)
                                                    .endsWith(suffix))) return;
                }
            throw new BiosFiles.MissingException(label + " missing", core, null, label);
        };
    }

    void require(String coreId, RomIndex.Game game) throws IOException;
}
