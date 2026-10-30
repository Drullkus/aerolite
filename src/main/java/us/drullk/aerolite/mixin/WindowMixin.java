package us.drullk.aerolite.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tamaized.beanification.BeanContext;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.client.preview.PreviewViewport;

import javax.annotation.Nullable;

/**
 * Reports the window width without the preview panel while the preview is open.
 * Minecraft then sizes its main render target, its GUI and its mouse mapping to the part left of the panel.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
// No API changes the window size that the game reports to its render targets, GUI and mouse handler.
@Mixin(Window.class)
abstract class WindowMixin {

    @Shadow
    private int framebufferWidth;
    @Shadow
    private int width;
    @Shadow
    private double guiScale;

    /** A mixin gets no injection. The screen check comes first, so no lookup occurs before the bean context exists. */
    @Unique
    @Nullable
    private static PreviewViewport aerolite$activeViewport() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !(minecraft.screen instanceof PreviewScreen)) {
            return null;
        }
        PreviewViewport viewport = BeanContext.inject(PreviewViewport.class);
        return viewport.active() ? viewport : null;
    }

    @Inject(method = "getWidth", at = @At("RETURN"), cancellable = true)
    private void aerolite$previewWidth(CallbackInfoReturnable<Integer> cir) {
        PreviewViewport viewport = aerolite$activeViewport();
        if (viewport != null) {
            cir.setReturnValue(viewport.framebufferWidth(this.framebufferWidth, this.width));
        }
    }

    @Inject(method = "getScreenWidth", at = @At("RETURN"), cancellable = true)
    private void aerolite$previewScreenWidth(CallbackInfoReturnable<Integer> cir) {
        PreviewViewport viewport = aerolite$activeViewport();
        if (viewport != null) {
            cir.setReturnValue(viewport.screenWidth(this.framebufferWidth, this.width));
        }
    }

    @Inject(method = "getGuiScaledWidth", at = @At("RETURN"), cancellable = true)
    private void aerolite$previewGuiScaledWidth(CallbackInfoReturnable<Integer> cir) {
        PreviewViewport viewport = aerolite$activeViewport();
        if (viewport != null) {
            cir.setReturnValue(viewport.guiScaledWidth(this.framebufferWidth, this.width, this.guiScale));
        }
    }

}
