package com.sammwy.retrocraft.client.ui;

import com.sammwy.retrocraft.cores.transfer.Downloads;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Small side cards also render above open console/settings screens. */
public final class DownloadHud {
    public static Downloads DOWNLOADS = new Downloads();
    private static final DownloadNotifications NOTIFICATIONS = new DownloadNotifications();

    public static void register() {
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
        long now = System.nanoTime();
        int width = Math.min(168, context.getScaledWindowWidth() - 16);
        int x = context.getScaledWindowWidth() - width - 8;
        int count =
                Math.min(
                        client.currentScreen == null ? 2 : 1,
                        Math.max(1, (context.getScaledWindowHeight() - 32) / 60));
        var cards = NOTIFICATIONS.update(entries, count, now);
        if (cards.isEmpty()) return;
        var tr = client.textRenderer;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        for (var card : cards) {
            var entry = card.entry;
            int y = 8 + card.slot * 60;
            float visibility = card.motion.value(now);
            context.getMatrices().push();
            context.getMatrices().translate((width + 12) * (1f - visibility), 0, 0);
            context.setShaderColor(1, 1, 1, visibility);
            boolean failed = entry.state() == Downloads.State.FAILED;
            int accent =
                    failed
                            ? SettingsWidgets.BAD
                            : entry.state() == Downloads.State.COMPLETE
                                    ? SettingsWidgets.GOOD
                                    : SettingsWidgets.ACCENT;
            SettingsWidgets.rounded(context, x, y + 2, width, 54, 0x40000000, 4);
            SettingsWidgets.rounded(context, x, y, width, 54, 0xF21E1930, 4);
            String status =
                    entry.state() == Downloads.State.DOWNLOADING
                            ? "Downloading " + entry.kind()
                            : failed ? entry.kind() + " failed" : entry.kind() + " ready";
            String percent =
                    failed
                            ? "!"
                            : entry.state() == Downloads.State.COMPLETE
                                    ? "100%"
                                    : entry.percent() < 0 ? "…" : entry.percent() + "%";
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
            if (entry.state() == Downloads.State.DOWNLOADING) {
                context.fill(x + 10, y + 32, x + width - 10, y + 35, SettingsWidgets.EDGE);
                int track = width - 20;
                if (entry.percent() >= 0)
                    context.fill(
                            x + 10, y + 32, x + 10 + track * entry.percent() / 100, y + 35, accent);
                else {
                    int segment = Math.max(1, track / 4);
                    int offset =
                            Math.round(
                                    (track - segment)
                                            * (1f - (float) Math.cos(now / 650_000_000d))
                                            / 2f);
                    context.fill(
                            x + 10 + offset, y + 32, x + 10 + offset + segment, y + 35, accent);
                }
            }
            context.drawText(
                    tr,
                    tr.trimToWidth(entry.size(), width - 20),
                    x + 10,
                    y + 40,
                    SettingsWidgets.MUTED,
                    false);
            context.setShaderColor(1, 1, 1, 1);
            context.getMatrices().pop();
        }
        if (entries.size() > count)
            context.drawText(
                    tr,
                    "+" + (entries.size() - count) + " downloads",
                    x + 10,
                    8 + count * 60,
                    SettingsWidgets.MUTED,
                    true);
        context.getMatrices().pop();
    }

    private DownloadHud() {}
}
