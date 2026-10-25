package com.sammwy.retrocraft.cores.worker;

import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/** A private, invisible GL context in the isolated worker; never touches Minecraft's GL state. */
final class HardwareVideo implements AutoCloseable {
    private long window;
    private Pointer eglDisplay, eglContext, eglSurface;
    private Egl egl;

    private interface Egl extends Library {
        Pointer eglGetPlatformDisplay(int platform, Pointer display, Pointer attributes);

        int eglInitialize(Pointer display, IntByReference major, IntByReference minor);

        int eglBindAPI(int api);

        int eglChooseConfig(
                Pointer display,
                int[] attributes,
                Pointer[] configs,
                int size,
                IntByReference count);

        Pointer eglCreateContext(Pointer display, Pointer config, Pointer shared, int[] attributes);

        Pointer eglCreatePbufferSurface(Pointer display, Pointer config, int[] attributes);

        int eglMakeCurrent(Pointer display, Pointer draw, Pointer read, Pointer context);

        Pointer eglGetProcAddress(String name);

        int eglDestroySurface(Pointer display, Pointer surface);

        int eglDestroyContext(Pointer display, Pointer context);

        int eglTerminate(Pointer display);
    }

    private GLFWErrorCallback errors;
    private int framebuffer, color, depth;
    private int bufferWidth, bufferHeight;
    private Libretro.HardwareRender request;
    private ByteBuffer pixels;
    private boolean reset;
    private final Libretro.Framebuffer getFramebuffer = () -> new Libretro.SizeT(framebuffer);
    private final Libretro.ProcAddress getProcAddress =
            name -> {
                if (eglDisplay != null) return egl.eglGetProcAddress(name);
                long address = GLFW.glfwGetProcAddress(name);
                return address == 0 ? null : new Pointer(address);
            };

    boolean configure(Pointer data) {
        var candidate = new Libretro.HardwareRender(data);
        if (candidate.contextType != 1 && candidate.contextType != 3) return false;
        if (window != 0 || eglDisplay != null) return false;
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux")
                && System.getenv().getOrDefault("DISPLAY", "").isBlank()
                && System.getenv().getOrDefault("WAYLAND_DISPLAY", "").isBlank())
            return createHeadless(candidate) && attach(candidate);
        errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        if (!GLFW.glfwInit()) {
            GLFW.glfwTerminate();
            if (!createHeadless(candidate)) return false;
            return attach(candidate);
        }
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        if (candidate.contextType == 3) {
            GLFW.glfwWindowHint(
                    GLFW.GLFW_CONTEXT_VERSION_MAJOR, Math.max(3, candidate.versionMajor));
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, candidate.versionMinor);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        }
        window = GLFW.glfwCreateWindow(1, 1, "Retrocraft core", 0, 0);
        if (window == 0) {
            GLFW.glfwTerminate();
            return false;
        }
        GLFW.glfwMakeContextCurrent(window);
        return attach(candidate);
    }

    private boolean attach(Libretro.HardwareRender candidate) {
        GL.createCapabilities();
        request = candidate;
        candidate.getFramebuffer = CallbackReference.getFunctionPointer(getFramebuffer);
        candidate.getProcAddress = CallbackReference.getFunctionPointer(getProcAddress);
        candidate.write();
        return true;
    }

    /** Mesa surfaceless EGL gives dedicated servers a GL context without a desktop session. */
    private boolean createHeadless(Libretro.HardwareRender candidate) {
        final int NONE = 0x3038;
        try {
            egl = Native.load("EGL", Egl.class);
            // EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY
            Pointer display = egl.eglGetPlatformDisplay(0x31DD, null, null);
            if (display == null
                    || egl.eglInitialize(display, new IntByReference(), new IntByReference()) == 0)
                return false;
            eglDisplay = display;
            if (egl.eglBindAPI(0x30A2) == 0) {
                close();
                return false;
            } // EGL_OPENGL_API
            Pointer[] configs = new Pointer[1];
            var count = new IntByReference();
            if (egl.eglChooseConfig(
                                    display,
                                    new int[] {
                                        0x3033,
                                        1, // EGL_SURFACE_TYPE, EGL_PBUFFER_BIT
                                        0x3024,
                                        8,
                                        0x3023,
                                        8,
                                        0x3022,
                                        8,
                                        0x3021,
                                        8, // RGBA sizes
                                        0x3040,
                                        8,
                                        NONE // EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT
                                    },
                                    configs,
                                    1,
                                    count)
                            == 0
                    || count.getValue() == 0) {
                close();
                return false;
            }
            eglContext =
                    egl.eglCreateContext(
                            display,
                            configs[0],
                            null,
                            candidate.contextType == 3
                                    ? new int[] {
                                        0x3098,
                                        Math.max(3, candidate.versionMajor),
                                        0x30FB,
                                        candidate.versionMinor,
                                        0x30FD,
                                        1,
                                        NONE
                                    }
                                    : new int[] {NONE});
            eglSurface =
                    egl.eglCreatePbufferSurface(
                            display, configs[0], new int[] {0x3057, 1, 0x3056, 1, NONE});
            if (eglContext == null
                    || eglSurface == null
                    || egl.eglMakeCurrent(display, eglSurface, eglSurface, eglContext) == 0) {
                close();
                return false;
            }
            return true;
        } catch (RuntimeException | LinkageError ex) {
            System.err.println("Cannot initialize headless core OpenGL: " + ex);
            close();
            return false;
        }
    }

    /** Called after retro_load_game has returned, before the first retro_run. */
    void start(int width, int height) {
        if (window == 0 && eglDisplay == null) return;
        if (width < 1 || height < 1 || width > 4096 || height > 4096)
            throw new IllegalStateException("Unsupported hardware geometry");
        bufferWidth = width;
        bufferHeight = height;
        framebuffer = GL33C.glGenFramebuffers();
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, framebuffer);
        color = GL33C.glGenTextures();
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, color);
        GL33C.glTexImage2D(
                GL33C.GL_TEXTURE_2D,
                0,
                GL33C.GL_RGBA8,
                width,
                height,
                0,
                GL33C.GL_RGBA,
                GL33C.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
        GL33C.glFramebufferTexture2D(
                GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, color, 0);
        if (request.depth != 0 || request.stencil != 0) {
            depth = GL33C.glGenRenderbuffers();
            GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, depth);
            GL33C.glRenderbufferStorage(
                    GL33C.GL_RENDERBUFFER, GL33C.GL_DEPTH24_STENCIL8, width, height);
            GL33C.glFramebufferRenderbuffer(
                    GL33C.GL_FRAMEBUFFER,
                    GL33C.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL33C.GL_RENDERBUFFER,
                    depth);
        }
        if (GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Core framebuffer is incomplete");
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, 0);
        if (request.contextReset != null)
            Function.getFunction(request.contextReset).invokeVoid(new Object[0]);
        reset = true;
    }

    FrameCodec.Frame read(int width, int height) {
        if (!reset || width < 1 || height < 1 || width > bufferWidth || height > bufferHeight)
            throw new IllegalStateException("Hardware frame exceeds the core framebuffer");
        int bytes = width * height * 4;
        if (pixels == null || pixels.capacity() < bytes) {
            if (pixels != null) MemoryUtil.memFree(pixels);
            pixels = MemoryUtil.memAlloc(bytes);
        }
        int previous = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
        int pack = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = GL33C.glGetInteger(GL33C.GL_PACK_ALIGNMENT);
        int rowLength = GL33C.glGetInteger(GL33C.GL_PACK_ROW_LENGTH);
        int skipRows = GL33C.glGetInteger(GL33C.GL_PACK_SKIP_ROWS);
        int skipPixels = GL33C.glGetInteger(GL33C.GL_PACK_SKIP_PIXELS);
        GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, framebuffer);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, 1);
        GL33C.glPixelStorei(GL33C.GL_PACK_ROW_LENGTH, 0);
        GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_ROWS, 0);
        GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_PIXELS, 0);
        pixels.clear();
        GL33C.glReadPixels(0, 0, width, height, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixels);
        GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, previous);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pack);
        GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, alignment);
        GL33C.glPixelStorei(GL33C.GL_PACK_ROW_LENGTH, rowLength);
        GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_ROWS, skipRows);
        GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_PIXELS, skipPixels);
        double scale =
                Math.min(
                        1,
                        Math.min(
                                (double) FrameCodec.MAX_WIDTH / width,
                                (double) FrameCodec.MAX_HEIGHT / height));
        int w = Math.max(1, (int) (width * scale)), h = Math.max(1, (int) (height * scale));
        byte[] rgb = new byte[w * h * 3];
        for (int y = 0; y < h; y++) {
            int sy = y * height / h;
            if (request.bottomLeftOrigin != 0) sy = height - sy - 1;
            for (int x = 0; x < w; x++) {
                int source = (sy * width + x * width / w) * 4;
                int target = (y * w + x) * 3;
                for (int c = 0; c < 3; c++) rgb[target + c] = pixels.get(source + c);
            }
        }
        return new FrameCodec.Frame(w, h, rgb);
    }

    /** Release native core GL objects while its callback owner is still alive. */
    void releaseCore() {
        if (!reset) return;
        reset = false;
        if (request.contextDestroy != null)
            Function.getFunction(request.contextDestroy).invokeVoid(new Object[0]);
    }

    @Override
    public void close() {
        if (window == 0 && eglDisplay == null) return;
        try {
            releaseCore();
        } finally {
            if (pixels != null) MemoryUtil.memFree(pixels);
            if (depth != 0) GL33C.glDeleteRenderbuffers(depth);
            if (color != 0) GL33C.glDeleteTextures(color);
            if (framebuffer != 0) GL33C.glDeleteFramebuffers(framebuffer);
            GL.setCapabilities(null);
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
                GLFW.glfwTerminate();
                window = 0;
            }
            if (eglDisplay != null) {
                egl.eglMakeCurrent(eglDisplay, null, null, null);
                if (eglSurface != null) egl.eglDestroySurface(eglDisplay, eglSurface);
                if (eglContext != null) egl.eglDestroyContext(eglDisplay, eglContext);
                egl.eglTerminate(eglDisplay);
                eglDisplay = eglSurface = eglContext = null;
            }
            if (errors != null) {
                GLFW.glfwSetErrorCallback(null);
                errors.free();
                errors = null;
            }
        }
    }
}
