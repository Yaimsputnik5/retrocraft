package com.sammwy.retrocraft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sammwy.retrocraft.cores.stream.DualScreen;

class DualScreenLayoutTest {
    @TempDir Path root;

    @Test
    void allArrangementsFitKeepNativeRatiosAndDoNotOverlap() {
        for (var system : DualScreen.values())
            for (var mode : DualScreenLayout.values()) {
                for (int[] size : new int[][] {{854, 380}, {360, 170}, {1920, 900}}) {
                    var pair = mode.fit(system, 8, 26, size[0], size[1], 14);
                    for (var rect : new DualScreenLayout.Rect[] {pair.top(), pair.bottom()}) {
                        assertTrue(rect.x() >= 22);
                        assertTrue(rect.y() >= 40);
                        assertTrue(rect.x() + rect.width() <= 8 + size[0] - 14);
                        assertTrue(rect.y() + rect.height() <= 26 + size[1] - 14);
                    }
                    assertFalse(pair.top().contains(pair.bottom().x(), pair.bottom().y()));
                    assertEquals(
                            (double) system.width / system.screenHeight,
                            (double) pair.top().width() / pair.top().height(),
                            .05);
                    assertEquals(
                            (double) system.touchWidth / system.screenHeight,
                            (double) pair.bottom().width() / pair.bottom().height(),
                            .05);
                    assertEquals(
                            mode == DualScreenLayout.VERTICAL,
                            pair.top().y() + pair.top().height() < pair.bottom().y());
                    if (mode == DualScreenLayout.TOP_LARGE)
                        assertTrue(pair.top().height() > pair.bottom().height() * 2);
                    if (mode == DualScreenLayout.BOTTOM_LARGE)
                        assertTrue(pair.bottom().height() > pair.top().height() * 2);
                }
            }
    }

    @Test
    void bottomCropAndPointerUseTheSameNativeMargins() {
        assertEquals(new DualScreen.Crop(0, 0, 1, .5f), DualScreen.NDS.crop(false));
        assertEquals(new DualScreen.Crop(.1f, .5f, .9f, 1), DualScreen.THREEDS.crop(true));
        for (var system : DualScreen.values()) {
            assertEquals(0, system.pointerX(.5));
            assertEquals(16384, system.pointerY(.5));
            assertEquals(0, system.pointerY(0));
            assertEquals(32767, system.pointerY(1));
            assertTrue(system.pointerX(0) < -26000);
            assertTrue(system.pointerX(1) > 26000);
        }
    }

    @Test
    void layoutIsPersistedIndependentlyPerSystemAndInvalidValuesAreRejected() throws Exception {
        var file = root.resolve("client.json");
        new ClientConfig()
                .changed(Map.of("ndsLayout", "horizontal", "threedsLayout", "bottom_large"))
                .save(file);
        var restored = ClientConfig.load(file);
        assertEquals("horizontal", restored.ndsLayout);
        assertEquals("bottom_large", restored.threedsLayout);
        assertThrows(
                IllegalArgumentException.class,
                () -> restored.changed(Map.of("ndsLayout", "invalid")));
        assertEquals(DualScreenLayout.VERTICAL, DualScreenLayout.BOTTOM_LARGE.next());
    }

    @Test
    void registeredHudPreferenceKeysPersistAndPreserveLegacyLayouts() throws Exception {
        var original = new ClientConfig().changed(Map.of("ndsLayout", "horizontal"));
        assertEquals(DualScreenLayout.HORIZONTAL, original.hudLayout("retrocraft:nds_handheld"));
        var config =
                original.withHudLayout("othermod:portable", DualScreenLayout.BOTTOM_LARGE)
                        .withHudLayout("retrocraft:nds_handheld", DualScreenLayout.TOP_LARGE);
        var file = root.resolve("registered-huds.json");
        config.save(file);
        var loaded = ClientConfig.load(file);
        assertEquals(DualScreenLayout.BOTTOM_LARGE, loaded.hudLayout("othermod:portable"));
        assertEquals(DualScreenLayout.TOP_LARGE, loaded.hudLayout("retrocraft:nds_handheld"));
        assertEquals(DualScreenLayout.VERTICAL, loaded.hudLayout("othermod:new"));
        assertEquals(
                DualScreenLayout.VERTICAL,
                loaded.changed(Map.of("ndsLayout", "vertical"))
                        .hudLayout("retrocraft:nds_handheld"));
        assertThrows(
                IllegalArgumentException.class,
                () -> loaded.withHudLayout("../ invalid", DualScreenLayout.VERTICAL));
    }
}
