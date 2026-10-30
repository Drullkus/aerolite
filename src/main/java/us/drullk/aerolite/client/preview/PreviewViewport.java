package us.drullk.aerolite.client.preview;

import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Component;

/**
 * The part of the window that the preview panel covers. While it is active, the window reports a width without the panel.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PreviewViewport {

    private boolean active;
    /** The panel width in window screen coordinates, which are also ImGui units. */
    private int panelUnits;

    public PreviewViewport() {
    }

    public boolean active() {
        return this.active;
    }

    public int panelUnits() {
        return this.panelUnits;
    }

    public void activate(int panelUnits) {
        this.active = true;
        this.panelUnits = panelUnits;
    }

    public void setPanelUnits(int panelUnits) {
        this.panelUnits = panelUnits;
    }

    public void deactivate() {
        this.active = false;
    }

    /** The panel width in framebuffer pixels: the ImGui units times the framebuffer scale. */
    public int panelPixels(int framebufferWidth, int screenWidth) {
        return screenWidth <= 0 ? this.panelUnits : Math.round(this.panelUnits * (framebufferWidth / (float) screenWidth));
    }

    /** The framebuffer width left of the panel. */
    public int framebufferWidth(int framebufferWidth, int screenWidth) {
        return Math.max(1, framebufferWidth - this.panelPixels(framebufferWidth, screenWidth));
    }

    /** The screen width left of the panel. */
    public int screenWidth(int framebufferWidth, int screenWidth) {
        return Math.max(1, screenWidth - this.panelUnits);
    }

    /** The GUI scaled width left of the panel, rounded up as {@code Window#setGuiScale} does. */
    public int guiScaledWidth(int framebufferWidth, int screenWidth, double guiScale) {
        double scaled = this.framebufferWidth(framebufferWidth, screenWidth) / guiScale;
        int floor = (int) scaled;
        return scaled > floor ? floor + 1 : floor;
    }

}
