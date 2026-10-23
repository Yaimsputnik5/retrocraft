package com.sammwy.retrocraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

final class InputControls {
    private static final int[] KEYS = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_UP,
                GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_X,
                GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_L};
    private static final int[] IDS = {0, 2, 3, 4, 5, 6, 7, 8, 1, 9, 10, 11, 12, 13, 14, 15};

    static int buttons(MinecraftClient client) {
        if (!client.isWindowFocused()) return 0;
        long window = client.getWindow().getHandle();
        int mask = 0;
        for (int i = 0; i < KEYS.length; i++) if (InputUtil.isKeyPressed(window, KEYS[i])) mask |= 1 << IDS[i];
        return mask;
    }
    private InputControls() {}
}
