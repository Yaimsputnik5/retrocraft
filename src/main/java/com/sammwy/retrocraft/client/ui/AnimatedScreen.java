package com.sammwy.retrocraft.client.ui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Keeps the screen alive until its closing animation has finished. */
abstract class AnimatedScreen extends Screen {
    private final UiMotion motion = new UiMotion();
    private Runnable afterClose;

    AnimatedScreen(Text title) {
        super(title);
    }

    public final boolean acceptingInput() {
        return motion.shown() && motion.settled(System.nanoTime());
    }

    final boolean closing() {
        return !motion.shown();
    }

    @Override
    public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float visibility = motion.value(now);
        if (closing() && motion.settled(now)) {
            Runnable action = afterClose;
            afterClose = null;
            if (action != null) action.run();
            return;
        }
        renderDimmer(context, visibility);
        context.getMatrices().push();
        try {
            UiDrawing.pop(context, width / 2f, height / 2f, visibility);
            renderContents(
                    context, acceptingInput() ? mouseX : -1, acceptingInput() ? mouseY : -1, delta);
        } finally {
            context.setShaderColor(1, 1, 1, 1);
            context.getMatrices().pop();
        }
    }

    protected abstract void renderContents(
            DrawContext context, int mouseX, int mouseY, float delta);

    protected void renderDimmer(DrawContext context, float visibility) {}

    protected final void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    protected final void transitionTo(Screen next) {
        dismiss(() -> client.setScreen(next));
    }

    private void dismiss(Runnable action) {
        if (closing()) return;
        afterClose = action;
        motion.show(false, System.nanoTime());
        setFocused(null);
    }

    @Override
    public void close() {
        dismiss(this::finishClose);
    }

    protected void finishClose() {
        super.close();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return !acceptingInput() || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return !acceptingInput() || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return !acceptingInput() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return !acceptingInput() || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (closing()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return !acceptingInput() || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return !acceptingInput() || super.charTyped(chr, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
}
