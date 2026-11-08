package us.drullk.aerolite.client.preview;

import us.drullk.aerolite.client.AeroliteClientConfig;

/**
 * The width rule of the field editor of the island editor, free of ImGui types.
 * The width stays inside the bounds of {@link AeroliteClientConfig}. Half the editor content width caps it, also below the least width.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
public final class FieldsWidth {

    private FieldsWidth() {
    }

    /**
     * The shown width of the field editor.
     *
     * @param width     the stored width
     * @param available the content width of the editor window
     */
    public static float shown(float width, float available) {
        float clamped = Math.clamp(width, AeroliteClientConfig.MIN_FIELDS_WIDTH, AeroliteClientConfig.MAX_FIELDS_WIDTH);
        return Math.max(0, Math.min(available / 2, clamped));
    }

    /** The least content width of the editor window that shows the field editor at its least width. */
    public static float leastContentWidth() {
        return 2F * AeroliteClientConfig.MIN_FIELDS_WIDTH;
    }

    /**
     * The width of the field editor while the splitter follows the cursor.
     *
     * @param right     the right edge of the editor content
     * @param cursor    the cursor X
     * @param splitter  the width of the splitter
     * @param available the content width of the editor window
     */
    public static float dragged(float right, float cursor, float splitter, float available) {
        return shown(right - cursor - splitter / 2, available);
    }

}
