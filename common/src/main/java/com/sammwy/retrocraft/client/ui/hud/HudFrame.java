package com.sammwy.retrocraft.client.ui.hud;

import java.util.*;

import com.sammwy.retrocraft.compat.ClientGraphics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Two resource-pack textures sharing source dimensions and a fixed nine-slice border. */
public record HudFrame(
        ResourceLocation background,
        ResourceLocation overlay,
        int width,
        int height,
        HudInsets border) {
    public HudFrame {
        Objects.requireNonNull(background);
        Objects.requireNonNull(overlay);
        Objects.requireNonNull(border);
        if (width <= border.left() + border.right() || height <= border.top() + border.bottom())
            throw new IllegalArgumentException("Texture needs a centre between its borders");
    }

    public record Slice(HudRect destination, HudRect source) {}

    /** Preserve corners and border thickness, including on targets smaller than the source. */
    public List<Slice> slices(HudRect target) {
        int l = Math.min(border.left(), target.width() / 2),
                r = Math.min(border.right(), target.width() - l),
                t = Math.min(border.top(), target.height() / 2),
                b = Math.min(border.bottom(), target.height() - t);
        int[]
                dx =
                        {
                            target.x(),
                            target.x() + l,
                            target.x() + target.width() - r,
                            target.x() + target.width()
                        },
                dy =
                        {
                            target.y(),
                            target.y() + t,
                            target.y() + target.height() - b,
                            target.y() + target.height()
                        },
                sx = {0, border.left(), width - border.right(), width},
                sy = {0, border.top(), height - border.bottom(), height};
        List<Slice> result = new ArrayList<>(9);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) {
                int dw = dx[col + 1] - dx[col],
                        dh = dy[row + 1] - dy[row],
                        sw = sx[col + 1] - sx[col],
                        sh = sy[row + 1] - sy[row];
                if (dw > 0 && dh > 0 && sw > 0 && sh > 0)
                    result.add(
                            new Slice(
                                    new HudRect(dx[col], dy[row], dw, dh),
                                    new HudRect(sx[col], sy[row], sw, sh)));
            }
        return List.copyOf(result);
    }

    public void draw(GuiGraphics graphics, ResourceLocation texture, HudRect target) {
        com.sammwy.retrocraft.compat.TextureImages.beginBlend();
        try {
            for (var slice : slices(target)) {
                var d = slice.destination();
                var s = slice.source();
                ClientGraphics.blit(
                        graphics,
                        texture,
                        d.x(),
                        d.y(),
                        d.width(),
                        d.height(),
                        s.x(),
                        s.y(),
                        s.width(),
                        s.height(),
                        width,
                        height);
            }
        } finally {
            com.sammwy.retrocraft.compat.TextureImages.endBlend();
        }
    }
}
