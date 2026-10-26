package com.sammwy.retrocraft.client.ui;

import java.util.*;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ConsoleInventoryScreen extends HandledScreen<ConsoleScreenHandler> {
    private SettingsWidgets.Flat power, eject;
    private final UiMotion motion = new UiMotion();
    private final ConsoleTheme theme;
    private final ItemStack consoleStack;

    public ConsoleInventoryScreen(
            ConsoleScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 176;
        playerInventoryTitleY = 82;
        theme = ConsoleTheme.of(handler.definition().id().getPath());
        consoleStack = new ItemStack(Registries.ITEM.get(handler.definition().id()));
    }

    @Override
    protected void init() {
        super.init();
        power =
                addDrawableChild(
                        new SettingsWidgets.Flat(
                                x + 8,
                                y + 60,
                                78,
                                18,
                                Text.empty(),
                                () -> button(ConsoleScreenHandler.POWER)));
        eject =
                addDrawableChild(
                        new SettingsWidgets.Flat(
                                x + 90,
                                y + 60,
                                78,
                                18,
                                Text.translatable("screen.retrocraft.eject"),
                                () -> button(ConsoleScreenHandler.EJECT)));
        updateButtons();
    }

    private void button(int action) {
        if (client.interactionManager != null)
            client.interactionManager.clickButton(handler.syncId, action);
    }

    private void updateButtons() {
        power.setMessage(
                Text.translatable(
                        handler.powered()
                                ? "screen.retrocraft.power_off"
                                : "screen.retrocraft.power_on"));
        power.active = handler.powered() || handler.getSlot(0).hasStack();
        eject.visible = !handler.definition().internalRom();
        eject.active = eject.visible && handler.getSlot(0).hasStack();
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        updateButtons();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int shell = theme.shell();
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xff14171e);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xff2b303a);
        context.fill(
                x + 1, y + 1, x + backgroundWidth - 1, y + 80, ConsoleTheme.shade(shell, .28f));
        context.fill(
                x + backgroundWidth - 15,
                y + 8,
                x + backgroundWidth - 10,
                y + 13,
                handler.powered() ? 0xffd9453a : 0xff5a2a28);
        // Cartridge bay: recessed slot with gold contacts under it.
        var slot = handler.slots.getFirst();
        context.fill(x + slot.x - 5, y + slot.y - 5, x + slot.x + 21, y + slot.y + 22, 0xff0f1116);
        context.fill(
                x + slot.x - 4,
                y + slot.y - 4,
                x + slot.x + 20,
                y + slot.y + 21,
                ConsoleTheme.shade(shell, .5f));
        for (int i = 0; i < 6; i++)
            context.fill(
                    x + slot.x - 2 + i * 4,
                    y + slot.y + 18,
                    x + slot.x + i * 4,
                    y + slot.y + 20,
                    0xffd7b15a);
        // Component ports: a smaller dark socket with a pin strip along its top edge.
        for (int i = 1; i < handler.machineSlots(); i++) {
            var port = handler.slots.get(i);
            context.fill(
                    x + port.x - 3, y + port.y - 3, x + port.x + 19, y + port.y + 19, 0xff0f1116);
            context.fill(
                    x + port.x - 2,
                    y + port.y - 2,
                    x + port.x + 18,
                    y + port.y + 18,
                    ConsoleTheme.shade(shell, .4f));
            for (int k = 0; k < 4; k++)
                context.fill(
                        x + port.x + 2 + k * 4,
                        y + port.y - 2,
                        x + port.x + 4 + k * 4,
                        y + port.y - 1,
                        0xffb8bcc6);
        }
        // Player items that cannot go into this console (other cartridges, anything else) get a red
        // backing.
        for (var s : handler.slots) {
            boolean wrong = !s.getStack().isEmpty() && !accepted(s.getStack());
            context.fill(
                    x + s.x - 1,
                    y + s.y - 1,
                    x + s.x + 17,
                    y + s.y + 17,
                    wrong ? 0xff8a3030 : 0xff727986);
            context.fill(
                    x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, wrong ? 0xff5a1f1f : 0xff181c24);
        }
    }

    private boolean accepted(ItemStack stack) {
        if (stack.isOf(handler.definition().cartridge())) return true;
        var component = ComponentStorage.component(stack);
        return handler.definition().componentSlots().stream().anyMatch(s -> s.accepts(component));
    }

    /** Empty component ports name themselves and what they take. */
    private void drawPortTooltip(DrawContext context, int mouseX, int mouseY) {
        if (focusedSlot == null || focusedSlot.hasStack()) return;
        int index = handler.slots.indexOf(focusedSlot);
        if (index < 1 || index >= handler.machineSlots()) return;
        var port = handler.definition().componentSlots().get(index - 1);
        List<Text> lines = new ArrayList<>();
        lines.add(port.displayName());
        for (var component : port.accepts())
            lines.add(
                    Text.literal("  ").append(component.displayName()).formatted(Formatting.GRAY));
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawItem(consoleStack, 6, 3);
        context.drawText(textRenderer, title, 26, 8, 0xffffff, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float visibility = motion.value(now);
        if (!motion.shown() && motion.settled(now)) {
            super.close();
            return;
        }
        context.fill(0, 0, width, height, (int) (160 * visibility) << 24);
        context.getMatrices().push();
        try {
            UiDrawing.pop(context, x + backgroundWidth / 2f, y + backgroundHeight / 2f, visibility);
            int mx = acceptingInput() ? mouseX : -1, my = acceptingInput() ? mouseY : -1;
            super.render(context, mx, my, delta);
            if (acceptingInput()) {
                drawMouseoverTooltip(context, mx, my);
                drawPortTooltip(context, mx, my);
            }
        } finally {
            context.setShaderColor(1, 1, 1, 1);
            context.getMatrices().pop();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        drawBackground(context, delta, mouseX, mouseY);
    }

    private boolean acceptingInput() {
        return motion.shown() && motion.settled(System.nanoTime());
    }

    @Override
    public void close() {
        motion.show(false, System.nanoTime());
        setFocused(null);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return !acceptingInput() || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return !acceptingInput() || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return !acceptingInput() || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        return !acceptingInput() || super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!motion.shown()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE || client.options.inventoryKey.matchesKey(key, scanCode)) {
            close();
            return true;
        }
        return !acceptingInput() || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
