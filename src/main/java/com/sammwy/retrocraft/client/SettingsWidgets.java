package com.sammwy.retrocraft.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.Consumer;

/** Hand-drawn controls for the settings screen, matching the flat/rounded look of the console HUDs. */
final class SettingsWidgets {
    static final int BG = 0xFF151020, PANEL = 0xFF1E1930, PANEL_HI = 0xFF282142, EDGE = 0xFF2E2848;
    static final int CONTROL = 0xC02A243E, CONTROL_HOVER = 0xE0453A60, ACCENT = 0xFFC45CF0, ACCENT_HI = 0xFFDC8CFF;
    static final int ON_ACCENT = 0xFF240D33, TEXT = 0xFFECE8F8, MUTED = 0xFF9C92B4, GOOD = 0xFF63C76C, BAD = 0xFFE0655C;
    private SettingsWidgets() { }

    static void rounded(DrawContext context, int x, int y, int w, int h, int color, int r) {
        context.fill(x + r, y, x + w - r, y + h, color);
        context.fill(x, y + r, x + r, y + h - r, color);
        context.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int k = 1; k < r; k++) {
            context.fill(x + r - k, y + k, x + r, y + k + 1, color); context.fill(x + w - r, y + k, x + w - r + k, y + k + 1, color);
            context.fill(x + r - k, y + h - k - 1, x + r, y + h - k, color); context.fill(x + w - r, y + h - k - 1, x + w - r + k, y + h - k, color);
        }
    }

    /** A flat label button: accent fill when armed, quiet dark otherwise. */
    static final class Flat extends ClickableWidget {
        private final Runnable action;
        private boolean accent;
        Flat(int x, int y, int w, int h, Text label, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
        }
        Flat accent(boolean value) { accent = value; return this; }
        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered() && active;
            int bg = !active ? 0x60242034 : accent ? (hot ? ACCENT_HI : ACCENT) : (hot ? CONTROL_HOVER : CONTROL);
            rounded(context, getX(), getY(), getWidth(), getHeight(), bg, 3);
            if (!accent) context.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + 1, hot ? 0x30FFFFFF : 0x14FFFFFF);
            int fg = !active ? 0xB0A898C4 : accent ? ON_ACCENT : TEXT;
            var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            // No text shadow on the accent fill — it muddies the dark label.
            context.drawText(tr, getMessage(), getX() + (getWidth() - tr.getWidth(getMessage())) / 2, getY() + (getHeight() - 8) / 2, fg, !accent);
        }
        @Override public void onClick(double mouseX, double mouseY) { action.run(); }
        @Override public void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
    }

    /** On/off pill with a sliding knob. */
    static final class Toggle extends ClickableWidget {
        private boolean value;
        private final Consumer<Boolean> onChange;
        Toggle(int x, int y, boolean value, Consumer<Boolean> onChange) {
            super(x, y, 34, 14, Text.empty());
            this.value = value; this.onChange = onChange;
        }
        void value(boolean v) { value = v; }
        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int track = value ? (isHovered() ? ACCENT_HI : ACCENT) : (isHovered() ? 0xFF3A3352 : 0xFF282138);
            rounded(context, getX(), getY() + 2, 30, 10, track, 5);
            int kx = value ? getX() + 18 : getX() + 2;
            rounded(context, kx, getY(), 10, 14, value ? ON_ACCENT : 0xFFCDC4E4, 4);
            if (!value) context.fill(kx + 3, getY() + 5, kx + 7, getY() + 9, 0xFF181226);
        }
        @Override public void onClick(double mouseX, double mouseY) { value = !value; onChange.accept(value); }
        @Override public void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
    }

    /** `‹ value ›` control cycling through enum/core choices. Left click steps forward, right click back. */
    static final class Cycle extends ClickableWidget {
        private final List<String> options;
        private int index;
        private final Consumer<String> onChange;
        Cycle(int x, int y, int w, List<String> options, String value, Consumer<String> onChange) {
            super(x, y, w, 16, Text.empty());
            this.options = options; this.onChange = onChange;
            index = Math.max(0, options.indexOf(value));
        }
        void value(String v) { index = Math.max(0, options.indexOf(v)); }
        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered() && options.size() > 1;
            rounded(context, getX(), getY(), getWidth(), getHeight(), hot ? CONTROL_HOVER : CONTROL, 3);
            context.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + 1, hot ? 0x30FFFFFF : 0x14FFFFFF);
            var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            String label = options.isEmpty() ? "—" : options.get(index);
            context.drawCenteredTextWithShadow(tr, Text.literal(tr.trimToWidth(label, getWidth() - 20)), getX() + getWidth() / 2, getY() + 4, options.size() > 1 ? TEXT : MUTED);
            if (options.size() > 1) {
                context.drawTextWithShadow(tr, "‹", getX() + 4, getY() + 4, hot ? ACCENT : MUTED);
                context.drawTextWithShadow(tr, "›", getX() + getWidth() - 9, getY() + 4, hot ? ACCENT : MUTED);
            }
        }
        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (options.size() <= 1 || !active || !visible || !isMouseOver(mouseX, mouseY) || (button != 0 && button != 1)) return false;
            playDownSound(net.minecraft.client.MinecraftClient.getInstance().getSoundManager());
            step(button == 1 || mouseX < getX() + getWidth() / 3.0 ? -1 : 1);
            return true;
        }
        @Override public void onClick(double mouseX, double mouseY) { }
        @Override public void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
        private void step(int dir) {
            if (options.isEmpty()) return;
            index = Math.floorMod(index + dir, options.size());
            onChange.accept(options.get(index));
        }
    }

    /** Tiny square action button with a pixel glyph and a tooltip, like {@link IconButton}. */
    static final class Glyph extends ClickableWidget {
        private final String[] art;
        private final Runnable action;
        Glyph(int x, int y, String[] art, Runnable action, Text tooltip) {
            super(x, y, 14, 14, tooltip);
            this.art = art; this.action = action;
            setTooltip(Tooltip.of(tooltip));
        }
        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered() && active;
            rounded(context, getX(), getY(), 14, 14, hot ? CONTROL_HOVER : CONTROL, 3);
            context.fill(getX() + 3, getY(), getX() + 11, getY() + 1, hot ? 0x30FFFFFF : 0x18FFFFFF);
            draw(context, art, getX() + 3, getY() + 4, hot ? 0xFFFFFFFF : (active ? 0xBFE4E8F0 : 0x50E4E8F0));
        }
        @Override public void onClick(double mouseX, double mouseY) { action.run(); }
        @Override public void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
    }

    static void draw(DrawContext context, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++)
            for (int col = 0; col < rows[r].length(); col++)
                if (rows[r].charAt(col) == 'X') context.fill(x + col, y + r, x + col + 1, y + r + 1, color);
    }

    /** Styles a vanilla text field to match the flat controls; caller supplies commit logic. */
    static TextFieldWidget field(net.minecraft.client.font.TextRenderer tr, int x, int y, int w, String value) {
        var field = new TextFieldWidget(tr, x, y, w, 16, Text.empty()) {
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                rounded(context, getX(), getY(), getWidth(), getHeight(), isFocused() ? 0xFF322850 : CONTROL, 3);
                context.fill(getX() + 2, getY() + getHeight() - 1, getX() + getWidth() - 2, getY() + getHeight(), isFocused() ? ACCENT : 0x30FFFFFF);
                if (isFocused()) super.renderWidget(context, mouseX, mouseY, delta);
                else context.drawCenteredTextWithShadow(tr, getText(), getX() + getWidth() / 2, getY() + 4, TEXT);
            }
        };
        field.setDrawsBackground(false);
        field.setEditableColor(0xFFE6E9F0);
        field.setText(value);
        return field;
    }

    static void click(net.minecraft.client.MinecraftClient client) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
