package com.sammwy.retrocraft.client.hud;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.sammwy.retrocraft.client.config.DualScreenLayout;
import com.sammwy.retrocraft.cores.stream.DualScreen;

import net.minecraft.util.Identifier;

class ConsoleHudLayoutTest {
    private HudFrame frame(HudInsets border) {
        return new HudFrame(
                Identifier.of("retrocraft", "test/background.png"),
                Identifier.of("retrocraft", "test/overlay.png"),
                64,
                64,
                border);
    }

    private ConsoleHudContext context(
            int width, int height, DualScreenLayout arrangement, int fw, int fh) {
        return new ConsoleHudContext(
                UUID.randomUUID(),
                "test",
                new HudRect(8, 26, width, height),
                fw,
                fh,
                arrangement,
                true,
                0,
                0,
                0);
    }

    @Test
    void singleScreensResizePreserveAspectAndKeepFixedBorders() {
        var renderer =
                new TexturedConsoleHudRenderer(
                        ConsoleHudConfig.single(480, 272, 0xff101218),
                        frame(new HudInsets(12, 12, 12, 12)));
        for (int[] size : new int[][] {{624, 340}, {411, 130}, {1904, 900}}) {
            var ctx = context(size[0], size[1], DualScreenLayout.VERTICAL, 480, 272);
            var layout = renderer.layout(ctx);
            assertEquals(1, layout.panels().size());
            var panel = layout.panels().getFirst();
            assertFits(ctx.viewport(), panel.frame());
            assertEquals(480.0 / 272, (double) panel.video().width() / panel.video().height(), .03);
            assertEquals(12, panel.video().x() - panel.frame().x());
            assertEquals(12, panel.video().y() - panel.frame().y());
            assertTrue(layout.touchAt(panel.video().x(), panel.video().y()).isEmpty());
        }
    }

    @Test
    void allDualModesShareVideoCropsAndTouchCoordinatesAfterResize() {
        for (var system : DualScreen.values()) {
            var renderer =
                    new TexturedConsoleHudRenderer(
                            new ConsoleHudConfig(
                                    system.width,
                                    system.screenHeight * 2,
                                    0xff101218,
                                    system,
                                    "test:dual"),
                            frame(new HudInsets(14, 14, 14, 14)));
            for (var mode : DualScreenLayout.values())
                for (int[] size : new int[][] {{624, 340}, {411, 130}, {1904, 900}}) {
                    var ctx =
                            context(size[0], size[1], mode, system.width, system.screenHeight * 2);
                    var layout = renderer.layout(ctx);
                    var top = layout.panels().get(0);
                    var bottom = layout.panels().get(1);
                    assertEquals(system.crop(false), top.crop());
                    assertEquals(system.crop(true), bottom.crop());
                    assertFits(ctx.viewport(), top.frame());
                    assertFits(ctx.viewport(), bottom.frame());
                    assertFalse(overlaps(top.frame(), bottom.frame()));
                    assertEquals(
                            (double) system.width / system.screenHeight,
                            (double) top.video().width() / top.video().height(),
                            .15);
                    assertEquals(
                            (double) system.touchWidth / system.screenHeight,
                            (double) bottom.video().width() / bottom.video().height(),
                            .15);
                    assertTrue(layout.touchAt(top.video().x(), top.video().y()).isEmpty());
                    assertTrue(layout.touchAt(bottom.frame().x(), bottom.frame().y()).isEmpty());
                    assertTrue(
                            layout.touchAt(bottom.video().x() - 1, bottom.video().y()).isEmpty());
                    var rect = bottom.video();
                    double cx = rect.x() + rect.width() / 2.0, cy = rect.y() + rect.height() / 2.0;
                    assertSame(bottom, layout.touchAt(cx, cy).orElseThrow());
                    assertEquals(system.pointerX(.5), bottom.pointerX(cx));
                    assertEquals(system.pointerY(.5), bottom.pointerY(cy));
                    assertEquals(system.pointerX(0), bottom.pointerX(rect.x()));
                    assertEquals(system.pointerY(0), bottom.pointerY(rect.y()));
                    if (mode == DualScreenLayout.TOP_LARGE)
                        assertTrue(top.video().height() > bottom.video().height() * 2);
                    if (mode == DualScreenLayout.BOTTOM_LARGE)
                        assertTrue(bottom.video().height() > top.video().height() * 2);
                }
        }
    }

    @Test
    void nineSliceCoversExactlyTheTargetAndPreservesCornerSizes() {
        var texture = frame(new HudInsets(12, 14, 10, 8));
        for (var target : List.of(new HudRect(3, 7, 400, 250), new HudRect(0, 0, 9, 7))) {
            var slices = texture.slices(target);
            assertEquals(
                    target.width() * target.height(),
                    slices.stream()
                            .mapToInt(s -> s.destination().width() * s.destination().height())
                            .sum());
            for (var s : slices) {
                assertFits(target, s.destination());
                assertFits(new HudRect(0, 0, 64, 64), s.source());
            }
            for (int a = 0; a < slices.size(); a++)
                for (int b = a + 1; b < slices.size(); b++)
                    assertFalse(overlaps(slices.get(a).destination(), slices.get(b).destination()));
        }
        var corner = texture.slices(new HudRect(8, 26, 600, 340)).getFirst();
        assertEquals(12, corner.destination().width());
        assertEquals(14, corner.destination().height());
        assertEquals(corner.source().width(), corner.destination().width());
    }

    @Test
    void customPanelCoordinatesDriveTouchAndInvalidCropsAreRejected() {
        var video = new HudRect(71, 93, 320, 200);
        var panel =
                new ConsoleHudLayout.Panel(
                        video,
                        video.expand(new HudInsets(5, 9, 7, 3)),
                        new DualScreen.Crop(.25f, .5f, .75f, 1),
                        true);
        assertEquals(0, panel.pointerX(231));
        assertEquals(16384, panel.pointerY(193));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ConsoleHudLayout.Panel(
                                video, video, new DualScreen.Crop(0, 0, Float.NaN, 1), true));
        assertThrows(IllegalArgumentException.class, () -> frame(new HudInsets(40, 0, 40, 0)));
    }

    @Test
    void rendererUsesOneCompositionForBackgroundVideoAndOverlay() {
        var events = new ArrayList<String>();
        var video = new HudRect(10, 20, 160, 144);
        var composition =
                new ConsoleHudLayout(
                        List.of(
                                new ConsoleHudLayout.Panel(
                                        video, video, new DualScreen.Crop(0, 0, 1, 1), false)));
        ConsoleHudRenderer renderer =
                new ConsoleHudRenderer() {
                    public ConsoleHudConfig config() {
                        return ConsoleHudConfig.single(160, 144, 0);
                    }

                    public ConsoleHudLayout layout(ConsoleHudContext context) {
                        events.add("layout");
                        return composition;
                    }

                    public void renderBackground(
                            net.minecraft.client.gui.DrawContext graphics,
                            ConsoleHudContext context,
                            ConsoleHudLayout layout) {
                        assertSame(composition, layout);
                        events.add("background");
                    }

                    public void renderOverlay(
                            net.minecraft.client.gui.DrawContext graphics,
                            ConsoleHudContext context,
                            ConsoleHudLayout layout) {
                        assertSame(composition, layout);
                        events.add("overlay");
                    }
                };
        assertSame(
                composition,
                renderer.render(
                        null,
                        context(624, 340, DualScreenLayout.VERTICAL, 160, 144),
                        panel -> {
                            assertSame(composition.panels().getFirst(), panel);
                            events.add("video");
                        }));
        assertEquals(List.of("layout", "background", "video", "overlay"), events);
    }

    private boolean overlaps(HudRect a, HudRect b) {
        return a.x() < b.x() + b.width()
                && a.x() + a.width() > b.x()
                && a.y() < b.y() + b.height()
                && a.y() + a.height() > b.y();
    }

    private void assertFits(HudRect outer, HudRect inner) {
        assertTrue(inner.x() >= outer.x());
        assertTrue(inner.y() >= outer.y());
        assertTrue(inner.x() + inner.width() <= outer.x() + outer.width());
        assertTrue(inner.y() + inner.height() <= outer.y() + outer.height());
    }
}
