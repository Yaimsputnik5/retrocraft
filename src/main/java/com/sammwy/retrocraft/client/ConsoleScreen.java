package com.sammwy.retrocraft.client;

import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.server.net.Packets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ConsoleScreen extends Screen {
    private static final int HEADER = 18, HUD = 52, MARGIN = 8;
    final UUID instance;
    private final ConsoleTheme theme;
    private final Text consoleName, displayTitle;
    private boolean controller, canManage, inWorld, subscribed = true;
    private long screen;
    private Identifier textureId;
    private int frameWidth = 256, frameHeight = 240;
    private final ItemStack consoleStack;
    private boolean hudWasHidden, hudHiddenByUs;

    ConsoleScreen(Packets.Ui packet) {
        super(Text.literal(packet.title()));
        instance = packet.instance();
        controller = packet.controller();
        canManage = packet.canManage();
        inWorld = packet.inWorld();
        screen = packet.screen();
        theme = ConsoleTheme.of(packet.console());
        consoleName = Text.translatable("console.retrocraft." + packet.console());
        displayTitle = Text.empty().append(consoleName).append(" · ").append(title);
        consoleStack =
                new ItemStack(Registries.ITEM.get(Identifier.of("retrocraft", packet.console())));
    }

    void update(Packets.Ui packet) {
        boolean changed = controller != packet.controller() || canManage != packet.canManage();
        controller = packet.controller();
        canManage = packet.canManage();
        inWorld = packet.inWorld();
        screen = packet.screen();
        if (changed) clearAndInit();
    }

    @Override
    protected void init() {
        if (client != null && !hudHiddenByUs) {
            hudWasHidden = client.options.hudHidden;
            client.options.hudHidden = true;
            hudHiddenByUs = true;
        }
        if (inWorld
                && ClientPreferences.get().cameraFocus
                && ClientPreferences.get().renderWorldScreens)
            CameraRig.focus(BlockPos.fromLong(screen));
        // Miniature actions in the top-right corner; Escape closes the screen.
        int x = width - MARGIN;
        if (canManage) {
            x -= 14;
            addDrawableChild(
                    new IconButton(
                            x,
                            4,
                            IconButton.Icon.POWER,
                            b ->
                                    ClientPlayNetworking.send(
                                            Packets.Request.of(Packets.POWER_OFF, instance)),
                            Text.translatable("screen.retrocraft.power_off")));
            x -= 16;
            addDrawableChild(
                    new IconButton(
                            x,
                            4,
                            IconButton.Icon.EJECT,
                            b ->
                                    ClientPlayNetworking.send(
                                            Packets.Request.of(Packets.EJECT, instance)),
                            Text.translatable("screen.retrocraft.eject")));
        }
        if (!controller) {
            x -= 74;
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.translatable("screen.retrocraft.take_control"),
                                    b ->
                                            ClientPlayNetworking.send(
                                                    Packets.Request.of(
                                                            Packets.CLAIM_INPUT, instance)))
                            .dimensions(x, 4, 70, 14)
                            .build());
        }
    }

    @Override
    public void tick() {
        if (!subscribed || !ClientPlayNetworking.canSend(Packets.Request.ID)) return;
        int mask = controller ? InputControls.buttons(client) : 0;
        ClientPlayNetworking.send(
                new Packets.Request(
                        Packets.INPUT, instance, mask, RetrocraftClient.VIDEO.sequence(instance)));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (RetrocraftClient.SETTINGS_KEY.matchesKey(key, scanCode)) {
            client.setScreen(SettingsScreen.modMenu(null));
            return true;
        }
        // Game input must not activate focused GUI buttons (Enter/Space).
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var entry = ScreenTextures.get(instance);
        textureId = entry == null ? null : entry.id();
        if (entry != null) {
            frameWidth = entry.width();
            frameHeight = entry.height();
        }
        if (inWorld && ClientPreferences.get().renderWorldScreens) {
            // The cabinet block shows the video; only the key legend and the miniature actions are
            // drawn.
            context.drawItem(consoleStack, MARGIN, 3);
            context.drawTextWithShadow(
                    textRenderer,
                    textRenderer.trimToWidth(
                            displayTitle.getString(),
                            Math.max(
                                    0,
                                    width
                                            - 2 * MARGIN
                                            - 20
                                            - (canManage ? 32 : 0)
                                            - (controller ? 0 : 74))),
                    MARGIN + 20,
                    7,
                    0xffe6e9f0);
            if (!controller)
                context.drawTextWithShadow(
                        textRenderer,
                        Text.translatable("screen.retrocraft.spectator"),
                        MARGIN + 20,
                        17,
                        0xffa8b0c0);
            if (textureId == null)
                context.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.translatable("screen.retrocraft.connecting"),
                        width / 2,
                        height - HUD - 10,
                        0xffd8dde6);
            PlayerHud.render(context, textRenderer, client, theme, width, height, instance);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        context.fill(0, 0, width, height, 0x70000000);
        context.drawItem(consoleStack, MARGIN, 3);
        context.drawTextWithShadow(
                textRenderer,
                textRenderer.trimToWidth(
                        displayTitle.getString(),
                        Math.max(
                                0,
                                width
                                        - 2 * MARGIN
                                        - 20
                                        - (canManage ? 32 : 0)
                                        - (controller ? 0 : 74))),
                MARGIN + 20,
                7,
                0xffe6e9f0);
        if (!controller)
            context.drawTextWithShadow(
                    textRenderer,
                    Text.translatable("screen.retrocraft.spectator"),
                    MARGIN + 20,
                    17,
                    0xffa8b0c0);

        // The video takes all the room between the top strip and the key legend, keeping its aspect
        // ratio, inside a slim console bezel.
        Bezel bezel = Bezel.of(theme.bezel());
        int areaTop = HEADER + MARGIN, areaHeight = height - HUD - MARGIN - areaTop;
        int availW = width - 2 * MARGIN - bezel.left - bezel.right,
                availH = areaHeight - bezel.top - bezel.bottom;
        float fit = Math.min((float) availW / frameWidth, (float) availH / frameHeight);
        int w = Math.max(1, Math.round(frameWidth * fit)),
                h = Math.max(1, Math.round(frameHeight * fit));
        int x = (width - bezel.left - bezel.right - w) / 2 + bezel.left,
                y = areaTop + (areaHeight - bezel.top - bezel.bottom - h) / 2 + bezel.top;
        drawBezel(context, bezel, x, y, w, h);
        if (textureId != null)
            context.drawTexture(
                    textureId, x, y, w, h, 0, 0, frameWidth, frameHeight, frameWidth, frameHeight);
        else {
            context.fill(x, y, x + w, y + h, theme.screenOff());
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("screen.retrocraft.connecting"),
                    x + w / 2,
                    y + h / 2 - 4,
                    theme.handheld() ? 0xff1f2e1a : 0xffd8dde6);
        }
        PlayerHud.render(context, textRenderer, client, theme, width, height, instance);
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
                for (int sx : new int[] {ox + 10, ox + ow - 40})
                    context.fill(sx, oy - 3, sx + 30, oy + 1, 0xff4b4190);
                led(context, ix + 2, iy + ih - 5, 0xff3ab064);
                context.fill(
                        ix + 8, iy + ih - 4, ix + Math.min(iw - 4, 48), iy + ih - 3, 0xff777788);
            }
            case NES_HANDHELD -> {
                context.fill(ox + 2, oy + 2, ox + ow - 2, oy + oh - 2, 0xff2d2d31);
                context.fill(ox + b.rim, oy + b.rim, ox + ow - b.rim, oy + oh - b.rim, dark);
                context.fill(ix, iy + ih + 1, ix + iw, iy + ih + 3, accent);
                led(context, ox + 4, oy + 4, 0xffd9453a);
            }
            case TV -> {
                rounded(
                        context,
                        ox + b.rim,
                        oy + b.rim,
                        ow - 2 * b.rim,
                        oh - b.rim - b.bottom + b.inner,
                        dark,
                        4);
                int strip = iy + ih + 4;
                context.fill(
                        ox + 10,
                        strip + 3,
                        ox + 10 + Math.min(60, ow / 4),
                        strip + 4,
                        ConsoleTheme.shade(shell, .7f));
                context.fill(
                        ox + 10,
                        strip + 6,
                        ox + 10 + Math.min(60, ow / 4),
                        strip + 7,
                        ConsoleTheme.shade(shell, .7f));
                context.fill(ox + 12, strip + 1, ox + 18, strip + 2, accent);
                for (int k = 0; k < 3; k++)
                    context.fill(
                            ox + ow - 40 + k * 9,
                            strip + 2,
                            ox + ow - 35 + k * 9,
                            strip + 7,
                            0xff8a8a92);
                led(context, ox + ow - 12, strip + 3, 0xffd9453a);
            }
            case ARCADE -> {
                context.fill(ox + 2, oy + 2, ox + 5, oy + oh - 2, 0xffc04f8a);
                context.fill(ox + 6, oy + 6, ox + 8, oy + oh - 6, 0xff2b7fc4);
                context.fill(ox + ow - 5, oy + 2, ox + ow - 2, oy + oh - 2, 0xffc04f8a);
                context.fill(ox + ow - 8, oy + 6, ox + ow - 6, oy + oh - 6, 0xff2b7fc4);
                context.fill(ox + 9, oy + 3, ox + ow - 9, oy + 13, 0xff1b1d3f);
                context.fill(ox + 9, oy + 3, ox + ow - 9, oy + 4, 0xff2b7fc4);
                context.fill(ox + 9, oy + 12, ox + ow - 9, oy + 13, 0xffc04f8a);
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
            context.fill(x + r - k, y + k, x + r, y + k + 1, color);
            context.fill(x + w - r, y + k, x + w - r + k, y + k + 1, color);
            context.fill(x + r - k, y + h - k - 1, x + r, y + h - k, color);
            context.fill(x + w - r, y + h - k - 1, x + w - r + k, y + h - k, color);
        }
    }

    private static void led(DrawContext context, int x, int y, int color) {
        context.fill(x - 1, y - 1, x + 4, y + 4, 0xff0b0d12);
        context.fill(x, y, x + 3, y + 3, color);
        context.fill(x, y, x + 1, y + 1, ConsoleTheme.shade(color, 1.4f));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    void serverClosed() {
        subscribed = false;
        close();
    }

    @Override
    public void removed() {
        if (hudHiddenByUs && client != null) {
            client.options.hudHidden = hudWasHidden;
            hudHiddenByUs = false;
        }
        CameraRig.release();
        PlayerHud.drop(instance);
        if (RetrocraftClient.EMULATION != null) RetrocraftClient.EMULATION.release(instance);
        if (subscribed && ClientPlayNetworking.canSend(Packets.Request.ID))
            ClientPlayNetworking.send(Packets.Request.of(Packets.CLOSE, instance));
        subscribed = false;
        super.removed();
    }
}
