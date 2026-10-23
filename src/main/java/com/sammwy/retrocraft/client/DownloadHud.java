package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.cores.transfer.Downloads;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Small side cards also render above open console/settings screens. */
final class DownloadHud {
    static Downloads DOWNLOADS = new Downloads();

    static void register() {
        HudRenderCallback.EVENT.register(
                (context, tick) -> {
                    if (MinecraftClient.getInstance().currentScreen == null) render(context);
                });
        ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) ->
                        ScreenEvents.afterRender(screen)
                                .register((s, context, mx, my, delta) -> render(context)));
    }

    private static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        var entries = DOWNLOADS.entries();
        if (entries.isEmpty()) return;
        int width = Math.min(168, context.getScaledWindowWidth() - 16);
        int x = context.getScaledWindowWidth() - width - 8;
        int count =
                Math.min(
                        client.currentScreen == null ? 2 : 1,
                        Math.min(
                                entries.size(),
                                Math.max(1, (context.getScaledWindowHeight() - 32) / 60)));
        int y = 8;
        int first = (int) ((System.nanoTime() / 3_000_000_000L) % entries.size());
        var tr = client.textRenderer;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        for (int i = 0; i < count; i++, y += 60) {
            var entry = entries.get((first + i) % entries.size());
            boolean failed = entry.state() == Downloads.State.FAILED;
            int accent =
                    failed
                            ? SettingsWidgets.BAD
                            : entry.state() == Downloads.State.COMPLETE
                                    ? SettingsWidgets.GOOD
                                    : SettingsWidgets.ACCENT;
            SettingsWidgets.rounded(context, x, y, width, 54, 0xEE1E1930, 4);
            context.fill(x + 1, y + 7, x + 3, y + 47, accent);
            String status =
                    entry.state() == Downloads.State.DOWNLOADING
                            ? "Downloading " + entry.kind()
                            : failed ? entry.kind() + " failed" : entry.kind() + " ready";
            String percent = entry.percent() < 0 ? "…" : entry.percent() + "%";
            context.drawText(
                    tr,
                    Text.literal(tr.trimToWidth(status, width - 53)),
                    x + 10,
                    y + 7,
                    SettingsWidgets.TEXT,
                    false);
            context.drawText(
                    tr, percent, x + width - 9 - tr.getWidth(percent), y + 7, accent, false);
            context.drawText(
                    tr,
                    tr.trimToWidth(entry.name(), width - 20),
                    x + 10,
                    y + 19,
                    SettingsWidgets.MUTED,
                    false);
            context.fill(x + 10, y + 32, x + width - 10, y + 35, SettingsWidgets.EDGE);
            int track = width - 20;
            if (entry.percent() >= 0)
                context.fill(
                        x + 10, y + 32, x + 10 + track * entry.percent() / 100, y + 35, accent);
            else {
                int segment = Math.max(1, track / 4);
                int offset = (int) ((System.nanoTime() / 15_000_000L) % (track - segment + 1));
                context.fill(x + 10 + offset, y + 32, x + 10 + offset + segment, y + 35, accent);
            }
            context.drawText(
                    tr,
                    tr.trimToWidth(entry.size(), width - 20),
                    x + 10,
                    y + 40,
                    SettingsWidgets.MUTED,
                    false);
        }
        if (entries.size() > count)
            context.drawText(
                    tr,
                    "+" + (entries.size() - count) + " downloads",
                    x + 10,
                    y,
                    SettingsWidgets.MUTED,
                    true);
        context.getMatrices().pop();
    }

    private DownloadHud() {}
}
