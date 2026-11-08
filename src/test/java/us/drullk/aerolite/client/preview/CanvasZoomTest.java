package us.drullk.aerolite.client.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the zoom math of the node canvas: the bounds, the wheel step, the position scale and the panning around the cursor.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class CanvasZoomTest {

    private static final float EPSILON = 1.0E-3F;

    @Test
    void wheelStaysInsideTheBounds() {
        assertEquals(CanvasZoom.MAX, CanvasZoom.wheel(1, 1000));
        assertEquals(CanvasZoom.MIN, CanvasZoom.wheel(1, -1000));
        assertEquals(CanvasZoom.STEP, CanvasZoom.wheel(1, 1), EPSILON);
        assertEquals(1 / CanvasZoom.STEP, CanvasZoom.wheel(1, -1), EPSILON);
    }

    @Test
    void storedPositionRoundTripsThroughTheGrid() {
        for (float zoom : new float[]{CanvasZoom.MIN, 0.7F, 1, 1.3F, CanvasZoom.MAX}) {
            float stored = 1234.5F;
            float grid = CanvasZoom.toGrid(stored, zoom);
            assertEquals(stored, CanvasZoom.toStored(grid, zoom), EPSILON);
            assertTrue(CanvasZoom.shows(grid, stored, zoom), "The grid position does not show the stored position");
        }
        assertEquals(1, CanvasZoom.toGrid(1, 1));
    }

    @Test
    void zoomKeepsTheCanvasPointUnderTheCursor() {
        float pan = -150;
        float cursor = 420;
        float zoom = 1;
        float stored = CanvasZoom.toStored(cursor - pan, zoom);
        for (float newZoom : new float[]{CanvasZoom.MIN, 0.5F, 1.21F, CanvasZoom.MAX}) {
            float newPan = CanvasZoom.panAround(pan, cursor, zoom, newZoom);
            assertEquals(cursor, newPan + CanvasZoom.toGrid(stored, newZoom), EPSILON, "The point under the cursor moves");
        }
        assertEquals(pan, CanvasZoom.panAround(pan, cursor, zoom, zoom), EPSILON);
    }

    @Test
    void paletteClickAtHalfZoomPlacesTheFunctionNodeUnderTheClick() {
        float zoom = 0.5F;
        float pan = 37;
        float click = 300;
        float stored = CanvasZoom.toStored(click - pan, zoom);
        assertEquals(2 * (click - pan), stored, EPSILON, "The stored position is not the grid point divided by the zoom");
        assertEquals(click, pan + CanvasZoom.toGrid(stored, zoom), EPSILON, "The function node does not sit under the click");
    }

    @Test
    void dragAtHalfZoomStoresTheUnscaledPosition() {
        float zoom = 0.5F;
        float grid = 210;
        float stored = CanvasZoom.toStored(grid, zoom);
        assertEquals(420, stored, EPSILON, "The drag does not store the grid position divided by the zoom");
        assertEquals(grid, CanvasZoom.toGrid(stored, zoom), EPSILON, "The stored position does not return to the grid position");
    }

}
