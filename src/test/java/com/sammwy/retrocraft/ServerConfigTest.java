package com.sammwy.retrocraft;

import com.sammwy.retrocraft.server.config.RetrocraftConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @TempDir Path root;
    @Test void invalidMappingsAndMalformedDocumentsReportIoErrors() throws Exception {
        for (String value : new String[]{"null", "[]", "{\"cores\":null}", "{\"cores\":{\"retrocraft:nes\":null}}", "{\"streamFps\":0}"}) {
            Files.writeString(root.resolve("server.json"), value);
            assertThrows(IOException.class, () -> RetrocraftConfig.load(root), value);
        }
    }
    @Test void incompatibleAndLegacyCoreMappingsFallBackToTheCorrectPlatform() throws Exception {
        Files.writeString(root.resolve("server.json"), "{\"cores\":{\"retrocraft:nes\":\"dolphin\",\"retrocraft:snes\":\"/old/core.so\"}}");
        var config = RetrocraftConfig.load(root);
        assertEquals("fceumm", config.coreId("retrocraft:nes"));
        assertEquals("snes9x", config.coreId("retrocraft:snes"));
    }
}
