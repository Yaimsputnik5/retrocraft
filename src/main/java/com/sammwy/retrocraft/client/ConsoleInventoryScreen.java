package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class ConsoleInventoryScreen extends HandledScreen<ConsoleScreenHandler> {
    private ButtonWidget power, eject;
    public ConsoleInventoryScreen(ConsoleScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176; backgroundHeight = 176; playerInventoryTitleY = 82;
    }
    @Override protected void init() {
        super.init();
        power = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> button(ConsoleScreenHandler.POWER)).dimensions(x + 8, y + 60, 78, 18).build());
        eject = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.retrocraft.eject"), b -> button(ConsoleScreenHandler.EJECT)).dimensions(x + 90, y + 60, 78, 18).build());
        updateButtons();
    }
    private void button(int action) { if (client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, action); }
    private void updateButtons() {
        power.setMessage(Text.translatable(handler.powered() ? "screen.retrocraft.power_off" : "screen.retrocraft.power_on"));
        power.active = handler.powered() || handler.getSlot(0).hasStack();
        eject.active = handler.getSlot(0).hasStack();
    }
    @Override protected void handledScreenTick() {
        super.handledScreenTick(); updateButtons();
    }
    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xff242831);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xff353a45);
        for (var slot : handler.slots) {
            context.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff727986);
            context.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff181c24);
        }
    }
    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, 8, 6, 0xffffff, false);
        context.drawText(textRenderer, Text.translatable("screen.retrocraft.cartridge"), 8, 23, 0xb8c0cc, false);
        context.drawText(textRenderer, playerInventoryTitle, 8, playerInventoryTitleY, 0xb8c0cc, false);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    @Override public boolean shouldPause() { return false; }
}
