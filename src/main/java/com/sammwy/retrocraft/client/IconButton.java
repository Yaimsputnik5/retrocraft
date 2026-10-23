package com.sammwy.retrocraft.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Miniature square action button: a drawn pixel glyph with a tooltip instead of a label. */
final class IconButton extends ButtonWidget {
    enum Icon {
        POWER,
        EJECT
    }

    private final Icon icon;

    IconButton(int x, int y, Icon icon, PressAction onPress, Text tooltip) {
        super(x, y, 14, 14, tooltip, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
        setTooltip(Tooltip.of(tooltip));
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hover = isHovered();
        rounded(context, getX(), getY(), 14, 14, hover ? 0xE04A5160 : 0xC0282D38, 3);
        context.fill(getX() + 3, getY(), getX() + 11, getY() + 1, hover ? 0x30FFFFFF : 0x18FFFFFF);
        int fg = hover ? 0xFFFFFFFF : 0xBFE4E8F0;
        switch (icon) {
            case POWER -> glyph(context, POWER_GLYPH, getX() + 3, getY() + 4, fg);
            case EJECT -> glyph(context, EJECT_GLYPH, getX() + 3, getY() + 4, fg);
        }
    }

    // Power symbol: open ring with the 12-o'clock bar. Eject: up triangle over a tray bar.
    private static final String[] POWER_GLYPH = {
        "   XX   ", " X XX X ", "X  XX  X", "X      X", "X      X", " X    X ", "  XXXX  "
    };
    private static final String[] EJECT_GLYPH = {
        "   X   ", "  XXX  ", " XXXXX ", "XXXXXXX", "       ", "XXXXXXX"
    };

    private static void glyph(DrawContext context, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++)
            for (int col = 0; col < rows[r].length(); col++)
                if (rows[r].charAt(col) == 'X')
                    context.fill(x + col, y + r, x + col + 1, y + r + 1, color);
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
}
