package com.sammwy.retrocraft.client.ui;

import static com.sammwy.retrocraft.client.ui.SettingsWidgets.*;

import com.sammwy.retrocraft.client.ui.SettingsWidgets.Flat;
import com.sammwy.retrocraft.roms.discovery.ArcadeSets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Gives the player an actionable firmware error using the existing BIOS manager. */
public final class BiosRequiredScreen extends AnimatedScreen {
    private final String coreId;
    private final String biosId;
    private final boolean administrator;
    private int x, y, w, h;

    public BiosRequiredScreen(String target, boolean administrator) {
        super(Component.translatable("bios.retrocraft.title"));
        int slash = target.indexOf('/');
        this.coreId = slash >= 0 ? target.substring(0, slash) : target;
        this.biosId = slash >= 0 ? target.substring(slash + 1) : null;
        this.administrator = administrator;
    }

    private Component explanation() {
        var bios = biosId != null ? ArcadeSets.bios(biosId) : null;
        if (bios != null) {
            return Component.translatable(
                    "message.retrocraft.arcade_bios_missing" + (administrator ? "_admin" : ""),
                    bios.zip(),
                    bios.title());
        }
        return Component.translatable(
                "message.retrocraft.bios_missing" + (administrator ? "_admin" : ""));
    }

    @Override
    protected void init() {
        w = Math.min(300, width - 24);
        h = 68 + font.split(explanation(), w - 28).size() * 11;
        x = (width - w) / 2;
        y = (height - h) / 2;
        int gap = administrator ? 8 : 0;
        int buttonWidth = administrator ? (w - 28 - gap) / 2 : w - 28;
        if (administrator)
            addRenderableWidget(
                    new Flat(
                                    x + 14,
                                    y + h - 30,
                                    buttonWidth,
                                    18,
                                    Component.translatable("bios.retrocraft.open"),
                                    () -> transitionTo(SettingsScreen.bios(coreId)))
                            .accent(true));
        addRenderableWidget(
                new Flat(
                        x + w - 14 - buttonWidth,
                        y + h - 30,
                        buttonWidth,
                        18,
                        Component.translatable("bios.retrocraft.close"),
                        this::onClose));
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        rounded(context, x, y, w, h, PANEL, 5);
        context.drawString(font, title, x + 14, y + 14, TEXT, false);
        int lineY = y + 31;
        for (var line : font.split(explanation(), w - 28)) {
            context.drawString(font, line, x + 14, lineY, MUTED, false);
            lineY += 11;
        }
        renderWidgets(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    @Override
    protected void renderDimmer(GuiGraphics context, float visibility) {
        context.fill(0, 0, width, height, ((int) (160 * visibility) << 24) | 0x100C18);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
