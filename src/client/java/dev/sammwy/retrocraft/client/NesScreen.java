package dev.sammwy.retrocraft.client;

import dev.sammwy.retrocraft.Retrocraft;
import dev.sammwy.retrocraft.net.Packets;
import dev.sammwy.retrocraft.stream.FrameCodec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.util.UUID;

public final class NesScreen extends Screen {
    final UUID instance;
    private boolean controller, canManage, subscribed = true;
    private final FrameCodec.Decoder decoder = new FrameCodec.Decoder();
    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private int frameWidth = 256, frameHeight = 240;
    private long lastResync;

    NesScreen(Packets.Ui packet) {
        super(Text.literal(packet.title()));
        instance = packet.instance(); controller = packet.controller(); canManage = packet.canManage();
    }

    void update(Packets.Ui packet) {
        controller = packet.controller();
        if (canManage != packet.canManage()) { canManage = packet.canManage(); clearAndInit(); }
    }

    @Override protected void init() {
        int bottom = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.close"), b -> close()).dimensions(width / 2 - 158, bottom, 76, 20).build());
        ButtonWidget inventory = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.inventory"), b -> {
            ClientPlayNetworking.send(Packets.Request.of(Packets.OPEN_INVENTORY, instance));
        }).dimensions(width / 2 - 78, bottom, 76, 20).build());
        inventory.active = canManage;
        ButtonWidget eject = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.eject"), b -> {
            ClientPlayNetworking.send(Packets.Request.of(Packets.EJECT, instance));
        }).dimensions(width / 2 + 2, bottom, 76, 20).build());
        eject.active = canManage;
        if (canManage) addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.power_off"), b -> {
            ClientPlayNetworking.send(Packets.Request.of(Packets.POWER_OFF, instance));
        }).dimensions(width / 2 + 82, bottom, 76, 20).build());
    }

    void video(FrameCodec.Encoded packet) {
        try {
            FrameCodec.Frame frame = decoder.decode(packet);
            if (frame == null) {
                if (System.nanoTime() - lastResync > 1_000_000_000L) {
                    ClientPlayNetworking.send(Packets.Request.of(Packets.RESYNC, instance));
                    lastResync = System.nanoTime();
                }
                return;
            }
            if (texture == null || frame.width() != frameWidth || frame.height() != frameHeight) {
                releaseTexture();
                frameWidth = frame.width(); frameHeight = frame.height();
                texture = new NativeImageBackedTexture(new NativeImage(frameWidth, frameHeight, false));
                texture.setFilter(false, false);
                textureId = client.getTextureManager().registerDynamicTexture("retrocraft/nes", texture);
            }
            NativeImage image = texture.getImage();
            byte[] rgb = frame.rgb();
            for (int y = 0; y < frameHeight; y++) {
                for (int x = 0; x < frameWidth; x++) {
                    int i = (y * frameWidth + x) * 3;
                    image.setColor(x, y, 0xff000000 | (rgb[i] & 255) | ((rgb[i + 1] & 255) << 8) | ((rgb[i + 2] & 255) << 16));
                }
            }
            texture.upload();
        } catch (IOException | IllegalArgumentException ex) {
            Retrocraft.LOG.warn("Invalid NES frame: {}", ex.toString());
            if (System.nanoTime() - lastResync > 1_000_000_000L) {
                ClientPlayNetworking.send(Packets.Request.of(Packets.RESYNC, instance)); lastResync = System.nanoTime();
            }
        }
    }

    @Override public void tick() {
        if (!subscribed || !ClientPlayNetworking.canSend(Packets.Request.ID)) return;
        int mask = controller ? InputControls.buttons(client) : 0;
        ClientPlayNetworking.send(new Packets.Request(Packets.INPUT, instance, mask, Math.max(0, decoder.sequence())));
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        // Game input must not activate focused GUI buttons (Enter/Space).
        return true;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xee11131a);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xffffff);
        Text role = Text.translatable(controller ? "screen.retrocraft.controller" : "screen.retrocraft.spectator");
        context.drawCenteredTextWithShadow(textRenderer, role, width / 2, 21, 0xa8b0c0);
        float scale = Math.min((width - 28f) / frameWidth, (height - 88f) / frameHeight);
        int w = Math.max(1, (int) (frameWidth * scale)), h = Math.max(1, (int) (frameHeight * scale));
        int x = (width - w) / 2, y = 36 + Math.max(0, (height - 88 - h) / 2);
        context.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xff50545e);
        context.fill(x, y, x + w, y + h, 0xff000000);
        if (textureId != null) context.drawTexture(textureId, x, y, w, h, 0, 0, frameWidth, frameHeight, frameWidth, frameHeight);
        else context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.retrocraft.connecting"), width / 2, y + h / 2, 0xffffff);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.retrocraft.controls"), width / 2, height - 42, 0xa8b0c0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    void serverClosed() { subscribed = false; close(); }

    @Override public void removed() {
        if (subscribed && ClientPlayNetworking.canSend(Packets.Request.ID)) ClientPlayNetworking.send(Packets.Request.of(Packets.CLOSE, instance));
        subscribed = false;
        releaseTexture();
        super.removed();
    }

    private void releaseTexture() {
        if (textureId != null && client != null) client.getTextureManager().destroyTexture(textureId);
        else if (texture != null) texture.close();
        texture = null; textureId = null;
    }
}
