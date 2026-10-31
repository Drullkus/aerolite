package us.drullk.aerolite.world.level;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.TestSettings;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the hand-over of the world creation settings to the server.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
class PendingShaftSettingsTest {

    @Test
    void newWorldClaimsTheOffer() {
        PendingShaftSettings pending = new PendingShaftSettings();
        pending.offer(TestSettings.SETTINGS);
        assertEquals(Optional.of(TestSettings.SETTINGS), pending.claim(true));
        assertEquals(Optional.of(TestSettings.SETTINGS), pending.claimed());
        assertTrue(pending.offered().isEmpty());
    }

    @Test
    void oldWorldDropsTheOffer() {
        PendingShaftSettings pending = new PendingShaftSettings();
        pending.offer(TestSettings.SETTINGS);
        assertTrue(pending.claim(false).isEmpty());
        assertTrue(pending.claimed().isEmpty());
        assertTrue(pending.offered().isEmpty());
    }

    @Test
    void clearDropsTheOfferAndKeepsTheClaim() {
        PendingShaftSettings pending = new PendingShaftSettings();
        pending.offer(TestSettings.SETTINGS);
        pending.claim(true);
        pending.offer(TestSettings.SETTINGS.withCloudHeight(0));
        pending.clear();
        assertTrue(pending.offered().isEmpty());
        assertEquals(Optional.of(TestSettings.SETTINGS), pending.claimed());
    }

    @Test
    void releaseClearsBoth() {
        PendingShaftSettings pending = new PendingShaftSettings();
        pending.offer(TestSettings.SETTINGS);
        pending.claim(true);
        pending.offer(TestSettings.SETTINGS);
        pending.release();
        assertTrue(pending.offered().isEmpty());
        assertTrue(pending.claimed().isEmpty());
    }

    @Test
    void noOfferClaimsNothing() {
        PendingShaftSettings pending = new PendingShaftSettings();
        assertTrue(pending.claim(true).isEmpty());
    }

}
