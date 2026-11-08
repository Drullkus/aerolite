package us.drullk.aerolite.client.preview;

/**
 * The zoom math of the node canvas, free of ImGui types. imnodes has no zoom, so the canvas scales the positions, the style and the font.
 * A stored position is in canvas units at zoom 1. The grid position that imnodes holds is the stored position times the zoom.
 * The panning is in screen units, so a screen point is the canvas origin plus the panning plus the grid position.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class CanvasZoom {

    /** The lowest zoom of the node canvas. */
    public static final float MIN = 0.25F;
    /** The highest zoom of the node canvas. */
    public static final float MAX = 2F;
    /** The zoom factor of one wheel notch. */
    public static final float STEP = 1.1F;
    /** The largest grid distance that still counts as the stored position. */
    private static final float SAME = 0.01F;

    private CanvasZoom() {
    }

    /** The zoom kept inside the bounds. */
    public static float clamp(float zoom) {
        return Math.clamp(zoom, MIN, MAX);
    }

    /** The zoom after the given wheel notches. A positive notch zooms in. */
    public static float wheel(float zoom, float notches) {
        return clamp(zoom * (float) Math.pow(STEP, notches));
    }

    /** The grid coordinate of a stored coordinate. */
    public static float toGrid(float stored, float zoom) {
        return stored * zoom;
    }

    /** The stored coordinate of a grid coordinate. */
    public static float toStored(float grid, float zoom) {
        return grid / zoom;
    }

    /** True when a grid coordinate shows the stored coordinate, within the rounding of the scale. */
    public static boolean shows(float grid, float stored, float zoom) {
        return Math.abs(grid - toGrid(stored, zoom)) <= SAME;
    }

    /**
     * The panning after a zoom change that keeps the canvas point under the cursor in place, along one axis.
     *
     * @param pan    the panning before the change
     * @param cursor the cursor distance from the canvas origin
     */
    public static float panAround(float pan, float cursor, float zoom, float newZoom) {
        float stored = toStored(cursor - pan, zoom);
        return cursor - toGrid(stored, newZoom);
    }

}
