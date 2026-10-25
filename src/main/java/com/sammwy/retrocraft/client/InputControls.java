package com.sammwy.retrocraft.client;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.client.config.ControllerProfile;
import com.sammwy.retrocraft.client.ui.ConsoleScreen;
import com.sammwy.retrocraft.cores.worker.PadInput;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public final class InputControls {
    public static PadInput sample(MinecraftClient client) {
        if (!client.isWindowFocused()
                || !(client.currentScreen instanceof ConsoleScreen screen)
                || !screen.acceptingInput()) return PadInput.IDLE;
        var profile = ControllerProfile.of(screen.consoleId());
        long window = client.getWindow().getHandle();
        return screen.withTouch(
                profile.sample(
                        ClientPreferences.get().controls(profile.id()),
                        key ->
                                key >= 0
                                        ? InputUtil.isKeyPressed(window, key)
                                        : GLFW.glfwGetMouseButton(window, -key - 2)
                                                == GLFW.GLFW_PRESS));
    }

    public static String keyName(int key) {
        if (key == -1) return "—";
        if (key <= -2)
            return InputUtil.Type.MOUSE.createFromCode(-key - 2).getLocalizedText().getString();
        return switch (key) {
            case GLFW.GLFW_KEY_UP -> "↑";
            case GLFW.GLFW_KEY_DOWN -> "↓";
            case GLFW.GLFW_KEY_LEFT -> "←";
            case GLFW.GLFW_KEY_RIGHT -> "→";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R Ctrl";
            default -> InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
        };
    }

    public static String keyName(String console, ControllerProfile.Action action) {
        var profile = ControllerProfile.of(console);
        return keyName(profile.key(ClientPreferences.get().controls(console), action));
    }

    private InputControls() {}
}
