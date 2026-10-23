package com.sammwy.retrocraft.client;

import static com.sammwy.retrocraft.client.SettingsWidgets.*;

import com.sammwy.retrocraft.roms.discovery.ArcadeSets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Gives the player an actionable firmware error using the existing BIOS manager. */
final class BiosRequiredScreen extends AnimatedScreen {
    private final String coreId;
    private final String biosId;
    private final boolean administrator;
    private int x, y, w, h;

    BiosRequiredScreen(String target, boolean administrator) {
        super(Text.translatable("bios.retrocraft.title"));
        int slash = target.indexOf('/');
        this.coreId = slash >= 0 ? target.substring(0, slash) : target;
        this.biosId = slash >= 0 ? target.substring(slash + 1) : null;
        this.administrator = administrator;
    }

    private Text explanation() {
        var bios = biosId != null ? ArcadeSets.bios(biosId) : null;
        if (bios != null) {
            return Text.translatable(
                    "message.retrocraft.arcade_bios_missing" + (administrator ? "_admin" : ""),
                    bios.zip(),
                    bios.title());
        }
        return Text.translatable(
                "message.retrocraft.bios_missing" + (administrator ? "_admin" : ""));
    }

    @Override
    protected void init() {
        w = Math.min(300, width - 24);
        h = 68 + textRenderer.wrapLines(explanation(), w - 28).size() * 11;
        x = (width - w) / 2;
        y = (height - h) / 2;
        int gap = administrator ? 8 : 0;
        int buttonWidth = administrator ? (w - 28 - gap) / 2 : w - 28;
        if (administrator)
            addDrawableChild(
                    new Flat(
                                    x + 14,
                                    y + h - 30,
                                    buttonWidth,
                                    18,
                                    Text.translatable("bios.retrocraft.open"),
                                    () -> transitionTo(SettingsScreen.bios(coreId)))
                            .accent(true));
        addDrawableChild(
                new Flat(
                        x + w - 14 - buttonWidth,
                        y + h - 30,
                        buttonWidth,
                        18,
                        Text.translatable("bios.retrocraft.close"),
                        this::close));
    }

    @Override
    protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
        rounded(context, x, y, w, h, PANEL, 5);
        context.drawText(textRenderer, title, x + 14, y + 14, TEXT, false);
        int lineY = y + 31;
        for (var line : textRenderer.wrapLines(explanation(), w - 28)) {
            context.drawText(textRenderer, line, x + 14, lineY, MUTED, false);
            lineY += 11;
        }
        renderWidgets(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    protected void renderDimmer(DrawContext context, float visibility) {
        context.fill(0, 0, width, height, ((int) (160 * visibility) << 24) | 0x100C18);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
