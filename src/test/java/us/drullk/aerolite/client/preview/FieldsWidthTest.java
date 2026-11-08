package us.drullk.aerolite.client.preview;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.client.AeroliteClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the width rule of the field editor: the config bounds, the half width cap and the splitter drag.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
class FieldsWidthTest {

    private static final float WIDE = 4000;
    private static final float EPSILON = 1.0E-4F;

    @Test
    void storedWidthShowsInsideTheBounds() {
        assertEquals(AeroliteClientConfig.DEFAULT_FIELDS_WIDTH, FieldsWidth.shown(AeroliteClientConfig.DEFAULT_FIELDS_WIDTH, WIDE), EPSILON);
        assertEquals(AeroliteClientConfig.MIN_FIELDS_WIDTH, FieldsWidth.shown(0, WIDE), EPSILON);
        assertEquals(AeroliteClientConfig.MAX_FIELDS_WIDTH, FieldsWidth.shown(WIDE, WIDE), EPSILON);
    }

    @Test
    void halfTheContentWidthCapsTheWidth() {
        assertEquals(150, FieldsWidth.shown(AeroliteClientConfig.MAX_FIELDS_WIDTH, 300), EPSILON);
        // Half the content width also wins over the least width.
        assertEquals(50, FieldsWidth.shown(AeroliteClientConfig.MIN_FIELDS_WIDTH, 100), EPSILON);
        assertEquals(0, FieldsWidth.shown(AeroliteClientConfig.DEFAULT_FIELDS_WIDTH, -10), EPSILON);
    }

    @Test
    void shownWidthNeverPassesHalfTheContentWidth() {
        for (float available = 0; available <= 3000; available += 7) {
            for (float stored : new float[]{0, AeroliteClientConfig.MIN_FIELDS_WIDTH, AeroliteClientConfig.DEFAULT_FIELDS_WIDTH, AeroliteClientConfig.MAX_FIELDS_WIDTH, WIDE}) {
                float shown = FieldsWidth.shown(stored, available);
                assertTrue(shown <= available / 2 + EPSILON);
                assertTrue(shown <= AeroliteClientConfig.MAX_FIELDS_WIDTH + EPSILON);
            }
        }
    }

    @Test
    void leastContentWidthShowsTheLeastFieldsWidth() {
        float least = FieldsWidth.leastContentWidth();
        assertEquals(AeroliteClientConfig.MIN_FIELDS_WIDTH, FieldsWidth.shown(AeroliteClientConfig.MIN_FIELDS_WIDTH, least), EPSILON);
        assertEquals(AeroliteClientConfig.MIN_FIELDS_WIDTH, FieldsWidth.shown(0, least), EPSILON);
        assertTrue(FieldsWidth.shown(AeroliteClientConfig.MIN_FIELDS_WIDTH, least - 1) < AeroliteClientConfig.MIN_FIELDS_WIDTH);
    }

    @Test
    void dragPastTheCanvasStopsAtHalfTheContentWidth() {
        assertEquals(500, FieldsWidth.dragged(1000, -5000, 6, 1000), EPSILON);
        assertEquals(AeroliteClientConfig.MIN_FIELDS_WIDTH, FieldsWidth.dragged(1000, 5000, 6, WIDE), EPSILON);
    }

    @Test
    void dragPutsTheSplitterCenterUnderTheCursor() {
        assertEquals(297, FieldsWidth.dragged(1000, 700, 6, WIDE), EPSILON);
        assertEquals(AeroliteClientConfig.MIN_FIELDS_WIDTH, FieldsWidth.dragged(1000, 990, 6, WIDE), EPSILON);
        assertEquals(400, FieldsWidth.dragged(1000, 0, 6, 800), EPSILON);
    }

}
