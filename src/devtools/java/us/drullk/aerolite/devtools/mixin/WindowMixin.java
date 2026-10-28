package us.drullk.aerolite.devtools.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the game window from taking the focus of the desktop.
 * The system property {@code aerolite.devtools.focusWindow} set to {@code false} turns this on.
 */
// The window exists before any mod constructor or event runs, so only a mixin can set the hint.
@Mixin(Window.class)
abstract class WindowMixin {
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"
            )
    )
    private void aerolite$hintNoFocus(CallbackInfo ci) {
        if (!"false".equals(System.getProperty("aerolite.devtools.focusWindow"))) {
            return;
        }
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }
}
