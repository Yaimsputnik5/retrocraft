package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.net.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

public final class ConsoleScreen extends Screen {
    private static final int HEADER = 18, LEGEND = 22, MARGIN = 8;
    final UUID instance;
    private final ConsoleTheme theme;
    private final Text consoleName;
    private boolean controller, canManage, inWorld, subscribed = true;
    private Identifier textureId;
    private int frameWidth = 256, frameHeight = 240;
    private final ItemStack consoleStack;
    private boolean hudWasHidden, hudHiddenByUs;

    ConsoleScreen(Packets.Ui packet) {
        super(Text.literal(packet.title()));
        instance = packet.instance(); controller = packet.controller(); canManage = packet.canManage(); inWorld = packet.inWorld();
        theme = ConsoleTheme.of(packet.console());
        consoleName = Text.translatable("console.retrocraft." + packet.console());
        consoleStack = new ItemStack(Registries.ITEM.get(Identifier.of("retrocraft", packet.console())));
    }

    void update(Packets.Ui packet) {
        boolean changed = controller != packet.controller() || canManage != packet.canManage();
        controller = packet.controller(); canManage = packet.canManage(); inWorld = packet.inWorld();
        if (changed) clearAndInit();
    }

    @Override protected void init() {
        if (client != null && !hudHiddenByUs) { hudWasHidden = client.options.hudHidden; client.options.hudHidden = true; hudHiddenByUs = true; }
        // Miniature actions in the top-right corner; Escape closes the screen.
        int x = width - MARGIN;
        if (canManage) {
            x -= 54; addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.power_off"), b -> ClientPlayNetworking.send(Packets.Request.of(Packets.POWER_OFF, instance))).dimensions(x, 4, 54, 14).build());
            x -= 40; addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.eject"), b -> ClientPlayNetworking.send(Packets.Request.of(Packets.EJECT, instance))).dimensions(x, 4, 36, 14).build());
        }
        if (!controller) { x -= 74; addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.take_control"), b -> ClientPlayNetworking.send(Packets.Request.of(Packets.CLAIM_INPUT, instance))).dimensions(x, 4, 70, 14).build()); }
    }

    @Override public void tick() {
        if (!subscribed || !ClientPlayNetworking.canSend(Packets.Request.ID)) return;
        int mask = controller ? InputControls.buttons(client) : 0;
        ClientPlayNetworking.send(new Packets.Request(Packets.INPUT, instance, mask, RetrocraftClient.VIDEO.sequence(instance)));
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        // Game input must not activate focused GUI buttons (Enter/Space).
        return true;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var entry = ScreenTextures.get(instance);
        textureId = entry == null ? null : entry.id();
        if (entry != null) { frameWidth = entry.width(); frameHeight = entry.height(); }
        if (inWorld) {
            // The cabinet block shows the video; only the key legend and the miniature actions are drawn.
            context.drawItem(consoleStack, MARGIN, 3);
            context.drawTextWithShadow(textRenderer, Text.empty().append(consoleName).append(" · ").append(title), MARGIN + 20, 7, 0xffe6e9f0);
            if (!controller) context.drawTextWithShadow(textRenderer, Text.translatable("screen.retrocraft.spectator"), MARGIN + 20, 17, 0xffa8b0c0);
            if (textureId == null) context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.retrocraft.connecting"), width / 2, height - LEGEND - 14, 0xffd8dde6);
            drawLegend(context, height - LEGEND);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        context.fill(0, 0, width, height, 0x70000000);
        context.drawItem(consoleStack, MARGIN, 3);
        context.drawTextWithShadow(textRenderer, Text.empty().append(consoleName).append(" · ").append(title), MARGIN + 20, 7, 0xffe6e9f0);
        if (!controller) context.drawTextWithShadow(textRenderer, Text.translatable("screen.retrocraft.spectator"), MARGIN + 20, 17, 0xffa8b0c0);

        // The video takes all the room between the top strip and the key legend, keeping its aspect ratio, inside a slim console bezel.
        Bezel bezel = Bezel.of(theme.bezel());
        int areaTop = HEADER + MARGIN, areaHeight = height - LEGEND - MARGIN - areaTop;
        int availW = width - 2 * MARGIN - bezel.left - bezel.right, availH = areaHeight - bezel.top - bezel.bottom;
        float fit = Math.min((float) availW / frameWidth, (float) availH / frameHeight);
        int w = Math.max(1, Math.round(frameWidth * fit)), h = Math.max(1, Math.round(frameHeight * fit));
        int x = (width - bezel.left - bezel.right - w) / 2 + bezel.left, y = areaTop + (areaHeight - bezel.top - bezel.bottom - h) / 2 + bezel.top;
        drawBezel(context, bezel, x, y, w, h);
        if (textureId != null) context.drawTexture(textureId, x, y, w, h, 0, 0, frameWidth, frameHeight, frameWidth, frameHeight);
        else {
            context.fill(x, y, x + w, y + h, theme.screenOff());
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.retrocraft.connecting"), x + w / 2, y + h / 2 - 4, theme.handheld() ? 0xff1f2e1a : 0xffd8dde6);
        }
        drawLegend(context, height - LEGEND);
        super.render(context, mouseX, mouseY, delta);
    }

    /** Padding the bezel needs around the video on each side, in GUI pixels. */
    private record Bezel(int left, int top, int right, int bottom, int rim, int inner) {
        static Bezel of(ConsoleTheme.Bezel kind) {
            return switch (kind) {
                case GB, GBC -> new Bezel(22, 14, 12, 12, 4, 8);
                case GBA -> new Bezel(12, 14, 12, 14, 4, 8);
                case NES_HANDHELD -> new Bezel(12, 12, 12, 14, 4, 8);
                case TV -> new Bezel(14, 12, 14, 22, 6, 8);
                case ARCADE -> new Bezel(14, 22, 14, 12, 6, 8);
            };
        }
    }

    private void drawBezel(DrawContext context, Bezel b, int x, int y, int w, int h) {
        int shell = theme.shell(), dark = theme.bezelColor(), accent = theme.accent();
        int ox = x - b.left, oy = y - b.top, ow = w + b.left + b.right, oh = h + b.top + b.bottom;
        int ix = x - b.inner, iy = y - b.inner, iw = w + 2 * b.inner, ih = h + 2 * b.inner;
        rounded(context, ox, oy, ow, oh, shell, 3);
        context.fill(ox + 3, oy + 1, ox + ow - 3, oy + 2, ConsoleTheme.shade(shell, 1.25f));
        context.fill(ox + 3, oy + oh - 2, ox + ow - 3, oy + oh - 1, ConsoleTheme.shade(shell, .6f));
        switch (theme.bezel()) {
            case GB, GBC -> {
                rounded(context, ox + b.rim, oy + b.rim, ow - 2 * b.rim, oh - 2 * b.rim, dark, 2);
                if (theme.bezel() == ConsoleTheme.Bezel.GB) {
                    context.fill(ix - 10, oy + b.rim + 3, ix + iw, oy + b.rim + 4, 0xff7a2a7c);
                    context.fill(ix - 10, oy + b.rim + 5, ix + iw, oy + b.rim + 6, 0xff2f3c8f);
                    context.fill(ix - 10, oy + b.rim + 7, ix + iw, oy + b.rim + 8, 0xff9b2d5f);
                }
                led(context, ix - 7, y + h / 2 - 1, 0xffd9453a);
                context.fill(ix - 9, y + h / 2 + 5, ix - 3, y + h / 2 + 6, 0xff9c9cae);
            }
            case GBA -> {
                rounded(context, ox + b.rim, oy + b.rim, ow - 2 * b.rim, oh - 2 * b.rim, dark, 2);
                for (int sx : new int[]{ox + 10, ox + ow - 40}) context.fill(sx, oy - 3, sx + 30, oy + 1, 0xff4b4190);
                led(context, ix + 2, iy + ih - 5, 0xff3ab064);
                context.fill(ix + 8, iy + ih - 4, ix + Math.min(iw - 4, 48), iy + ih - 3, 0xff777788);
            }
            case NES_HANDHELD -> {
                context.fill(ox + 2, oy + 2, ox + ow - 2, oy + oh - 2, 0xff2d2d31);
                context.fill(ox + b.rim, oy + b.rim, ox + ow - b.rim, oy + oh - b.rim, dark);
                context.fill(ix, iy + ih + 1, ix + iw, iy + ih + 3, accent);
                led(context, ox + 4, oy + 4, 0xffd9453a);
            }
            case TV -> {
                rounded(context, ox + b.rim, oy + b.rim, ow - 2 * b.rim, oh - b.rim - b.bottom + b.inner, dark, 4);
                int strip = iy + ih + 4;
                context.fill(ox + 10, strip + 3, ox + 10 + Math.min(60, ow / 4), strip + 4, ConsoleTheme.shade(shell, .7f));
                context.fill(ox + 10, strip + 6, ox + 10 + Math.min(60, ow / 4), strip + 7, ConsoleTheme.shade(shell, .7f));
                context.fill(ox + 12, strip + 1, ox + 18, strip + 2, accent);
                for (int k = 0; k < 3; k++) context.fill(ox + ow - 40 + k * 9, strip + 2, ox + ow - 35 + k * 9, strip + 7, 0xff8a8a92);
                led(context, ox + ow - 12, strip + 3, 0xffd9453a);
            }
            case ARCADE -> {
                context.fill(ox + 2, oy + 2, ox + 5, oy + oh - 2, 0xffc04f8a); context.fill(ox + 6, oy + 6, ox + 8, oy + oh - 6, 0xff2b7fc4);
                context.fill(ox + ow - 5, oy + 2, ox + ow - 2, oy + oh - 2, 0xffc04f8a); context.fill(ox + ow - 8, oy + 6, ox + ow - 6, oy + oh - 6, 0xff2b7fc4);
                context.fill(ox + 9, oy + 3, ox + ow - 9, oy + 13, 0xff1b1d3f);
                context.fill(ox + 9, oy + 3, ox + ow - 9, oy + 4, 0xff2b7fc4); context.fill(ox + 9, oy + 12, ox + ow - 9, oy + 13, 0xffc04f8a);
                context.fill(ox + ow / 2 - 24, oy + 6, ox + ow / 2 + 24, oy + 10, accent);
                led(context, ox + ow - 20, oy + 6, 0xff3ab064);
                context.fill(ox + b.rim + 3, oy + 15, ox + ow - b.rim - 3, oy + oh - b.rim, dark);
            }
        }
        context.fill(ix, iy, ix + iw, iy + ih, dark);
        context.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xff000000);
    }

    private static void rounded(DrawContext context, int x, int y, int w, int h, int color, int r) {
        context.fill(x + r, y, x + w - r, y + h, color);
        context.fill(x, y + r, x + r, y + h - r, color);
        context.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int k = 1; k < r; k++) {
            context.fill(x + r - k, y + k, x + r, y + k + 1, color); context.fill(x + w - r, y + k, x + w - r + k, y + k + 1, color);
            context.fill(x + r - k, y + h - k - 1, x + r, y + h - k, color); context.fill(x + w - r, y + h - k - 1, x + w - r + k, y + h - k, color);
        }
    }

    private static void led(DrawContext context, int x, int y, int color) {
        context.fill(x - 1, y - 1, x + 4, y + 4, 0xff0b0d12);
        context.fill(x, y, x + 3, y + 3, color);
        context.fill(x, y, x + 1, y + 1, ConsoleTheme.shade(color, 1.4f));
    }

    /** Centered key legend; caps light up with the accent colour while the controller holds them. */
    private void drawLegend(DrawContext context, int y) {
        int pressed = controller && client != null ? InputControls.buttons(client) : 0, total = -8;
        for (var binding : theme.bindings()) total += textRenderer.getWidth(binding.key()) + 8 + 4 + textRenderer.getWidth(binding.label()) + 8;
        int x = Math.max(MARGIN, (width - total) / 2);
        for (var binding : theme.bindings()) {
            int keyWidth = textRenderer.getWidth(binding.key()) + 8;
            boolean lit = (pressed & binding.mask()) != 0;
            context.fill(x, y, x + keyWidth, y + 13, lit ? theme.accent() : 0xcc2a2f3b);
            context.fill(x, y + 12, x + keyWidth, y + 13, lit ? ConsoleTheme.shade(theme.accent(), .6f) : 0xff10131a);
            context.fill(x, y, x + keyWidth, y + 1, lit ? ConsoleTheme.shade(theme.accent(), 1.3f) : 0xff4a5160);
            context.drawText(textRenderer, binding.key(), x + 4, y + 3, lit ? 0xff14161c : 0xffe4e8f0, false);
            context.drawTextWithShadow(textRenderer, binding.label(), x + keyWidth + 4, y + 3, lit ? 0xffffffff : 0xffc8cfdb);
            x += keyWidth + 4 + textRenderer.getWidth(binding.label()) + 8;
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    void serverClosed() { subscribed = false; close(); }

    @Override public void removed() {
        if (hudHiddenByUs && client != null) { client.options.hudHidden = hudWasHidden; hudHiddenByUs = false; }
        if (RetrocraftClient.EMULATION != null) RetrocraftClient.EMULATION.release(instance);
        if (subscribed && ClientPlayNetworking.canSend(Packets.Request.ID)) ClientPlayNetworking.send(Packets.Request.of(Packets.CLOSE, instance));
        subscribed = false;
        super.removed();
    }

}
