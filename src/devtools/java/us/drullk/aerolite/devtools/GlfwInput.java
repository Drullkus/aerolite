package us.drullk.aerolite.devtools;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

/**
 * Mouse and key input for the probes through the GLFW callbacks of the game window.
 * Each call reads the installed callback, puts it back and invokes it, so the input takes the path of a real event.
 * imgui-mc installs these callbacks and chains the callbacks of Minecraft.
 * The positions are window coordinates in screen units.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
final class GlfwInput {

    private GlfwInput() {
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    /** Moves the cursor, like a cursor position event of GLFW. */
    static void cursor(double x, double y) {
        long window = window();
        GLFWCursorPosCallback callback = GLFW.glfwSetCursorPosCallback(window, null);
        GLFW.glfwSetCursorPosCallback(window, callback);
        if (callback == null) {
            throw new IllegalStateException("The game window has no cursor position callback");
        }
        callback.invoke(window, x, y);
    }

    /** Turns the wheel, like a scroll event of GLFW. A positive value scrolls up. */
    static void scroll(double x, double y) {
        long window = window();
        GLFWScrollCallback callback = GLFW.glfwSetScrollCallback(window, null);
        GLFW.glfwSetScrollCallback(window, callback);
        if (callback == null) {
            throw new IllegalStateException("The game window has no scroll callback");
        }
        callback.invoke(window, x, y);
    }

    /** Presses or releases the left mouse button, like a mouse button event of GLFW. */
    static void leftButton(boolean pressed) {
        mouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, pressed);
    }

    /** Presses or releases the right mouse button, like a mouse button event of GLFW. */
    static void rightButton(boolean pressed) {
        mouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT, pressed);
    }

    private static void mouseButton(int button, boolean pressed) {
        long window = window();
        GLFWMouseButtonCallback callback = GLFW.glfwSetMouseButtonCallback(window, null);
        GLFW.glfwSetMouseButtonCallback(window, callback);
        if (callback == null) {
            throw new IllegalStateException("The game window has no mouse button callback");
        }
        callback.invoke(window, button, pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    /** Presses and releases a key, like two key events of GLFW. */
    static void key(int key) {
        long window = window();
        GLFWKeyCallback callback = GLFW.glfwSetKeyCallback(window, null);
        GLFW.glfwSetKeyCallback(window, callback);
        if (callback == null) {
            throw new IllegalStateException("The game window has no key callback");
        }
        int scancode = GLFW.glfwGetKeyScancode(key);
        callback.invoke(window, key, scancode, GLFW.GLFW_PRESS, 0);
        callback.invoke(window, key, scancode, GLFW.GLFW_RELEASE, 0);
    }

}
