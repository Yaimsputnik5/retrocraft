package com.sammwy.retrocraft.client.ui.hud;

import java.util.UUID;

import com.sammwy.retrocraft.client.config.DualScreenLayout;

/** Runtime input to a handheld renderer. All positions and sizes are GUI pixels. */
public record ConsoleHudContext(
        UUID instance,
        String console,
        HudRect viewport,
        int frameWidth,
        int frameHeight,
        DualScreenLayout arrangement,
        boolean controller,
        double mouseX,
        double mouseY,
        float delta) {
    public ConsoleHudContext {
        if (viewport == null || frameWidth < 1 || frameHeight < 1 || arrangement == null)
            throw new IllegalArgumentException("Invalid console HUD context");
    }
}
