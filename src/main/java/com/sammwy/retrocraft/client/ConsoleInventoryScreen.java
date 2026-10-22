package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public final class ConsoleInventoryScreen extends HandledScreen<ConsoleScreenHandler> {
    private ButtonWidget power, eject;
    private final ConsoleTheme theme;
    private final ItemStack consoleStack;
    public ConsoleInventoryScreen(ConsoleScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176; backgroundHeight = 176; playerInventoryTitleY = 82;
        theme = ConsoleTheme.of(handler.definition().id().getPath());
        consoleStack = new ItemStack(Registries.ITEM.get(handler.definition().id()));
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
        int shell = theme.shell();
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xff14171e);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xff2b303a);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + 80, ConsoleTheme.shade(shell, .28f));
        context.fill(x + 1, y + 22, x + backgroundWidth - 1, y + 23, theme.accent());
        context.fill(x + backgroundWidth - 15, y + 8, x + backgroundWidth - 10, y + 13, handler.powered() ? 0xffd9453a : 0xff5a2a28);
        // Cartridge bay: recessed slot with gold contacts under it.
        var slot = handler.slots.getFirst();
        context.fill(x + slot.x - 5, y + slot.y - 5, x + slot.x + 21, y + slot.y + 22, 0xff0f1116);
        context.fill(x + slot.x - 4, y + slot.y - 4, x + slot.x + 20, y + slot.y + 21, ConsoleTheme.shade(shell, .5f));
        for (int i = 0; i < 6; i++) context.fill(x + slot.x - 2 + i * 4, y + slot.y + 18, x + slot.x + i * 4, y + slot.y + 20, 0xffd7b15a);
        // Player items that cannot go into this console (other cartridges, anything else) get a red backing.
        for (var s : handler.slots) {
            boolean wrong = !s.getStack().isEmpty() && !s.getStack().isOf(handler.definition().cartridge());
            context.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, wrong ? 0xff8a3030 : 0xff727986);
            context.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, wrong ? 0xff5a1f1f : 0xff181c24);
        }
    }
    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawItem(consoleStack, 6, 3);
        context.drawText(textRenderer, title, 26, 8, 0xffffff, false);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    @Override public boolean shouldPause() { return false; }
}
