package com.sammwy.retrocraft.client;

import net.minecraft.client.gui.DrawContext;

/** Pixel symbols stay legible at HUD size without relying on a font's Unicode coverage. */
final class PadGlyphs {
    private PadGlyphs() {}

    static boolean draw(DrawContext context, String symbol, int x, int y, boolean held) {
        String[] rows;
        int color;
        switch (symbol) {
            case "cross" -> {
                rows =
                        new String[] {
                            "X     X", " X   X ", "  X X  ", "   X   ", "  X X  ", " X   X ",
                            "X     X"
                        };
                color = 0xFF8BBBF5;
            }
            case "circle" -> {
                rows =
                        new String[] {
                            "  XXX  ", " X   X ", "X     X", "X     X", "X     X", " X   X ",
                            "  XXX  "
                        };
                color = 0xFFF08A88;
            }
            case "square" -> {
                rows =
                        new String[] {
                            "XXXXXXX", "X     X", "X     X", "X     X", "X     X", "X     X",
                            "XXXXXXX"
                        };
                color = 0xFFE5A1CA;
            }
            case "triangle" -> {
                rows =
                        new String[] {
                            "   X   ", "   X   ", "  X X  ", "  X X  ", " X   X ", " X   X ",
                            "XXXXXXX"
                        };
                color = 0xFF81D8BA;
            }
            default -> {
                return false;
            }
        }
        SettingsWidgets.draw(context, rows, x, y, held ? 0xFFFFFFFF : color);
        return true;
    }
}
