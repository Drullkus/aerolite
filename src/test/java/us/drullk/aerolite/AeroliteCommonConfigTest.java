package us.drullk.aerolite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the common config.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-00_10881e45-2016-40f4-83b8-2c27043cd0f4.md}.
 */
class AeroliteCommonConfigTest {

    @Test
    void defaultPillarsStartOn() {
        assertTrue(new AeroliteCommonConfig().applyDefaultPillars());
    }

}
