package com.sammwy.retrocraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

final class InputControls {
    static int buttons(MinecraftClient client) {
        if (!client.isWindowFocused()) return 0;
        long window = client.getWindow().getHandle();
        int[] keys = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_UP,
                GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_X};
        int[] ids = {0, 2, 3, 4, 5, 6, 7, 8};
        int mask = 0;
        for (int i = 0; i < keys.length; i++) if (InputUtil.isKeyPressed(window, keys[i])) mask |= 1 << ids[i];
        return mask;
    }
    private InputControls() {}
}
