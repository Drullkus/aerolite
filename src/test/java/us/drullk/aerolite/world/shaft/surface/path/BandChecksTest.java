package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the whole-band rules on hand-made bands.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class BandChecksTest {

    @Test
    void twoWideSquareAcrossTheRingSeam() {
        Band band = Band.pillar(0, 16);
        band.set(47, 4, BandKind.GROOVE);
        band.set(0, 4, BandKind.SURFACE);
        band.set(47, 5, BandKind.GROOVE);
        band.set(0, 5, BandKind.GROOVE);
        assertEquals(LongList.of(band.pos(47, 4)), BandChecks.twoWide(band));
    }

    @Test
    void plusCrossing() {
        Band band = Band.pillar(0, 16);
        for (int i = 3; i <= 7; i++) {
            band.set(i, 5, BandKind.GROOVE);
            band.set(5, i, BandKind.GROOVE);
        }
        assertEquals(LongList.of(band.pos(5, 5)), BandChecks.crossings(band));
        band.set(5, 6, BandKind.WALL);
        assertTrue(BandChecks.crossings(band).isEmpty());
    }

    @Test
    void crossingAtTheBandEndDoesNotCount() {
        Band band = Band.pillar(0, 16);
        band.set(4, 0, BandKind.GROOVE);
        band.set(5, 0, BandKind.GROOVE);
        band.set(6, 0, BandKind.GROOVE);
        band.set(5, 1, BandKind.GROOVE);
        assertTrue(BandChecks.crossings(band).isEmpty());
    }

    @Test
    void cornerRun() {
        Band band = Band.pillar(0, 16);
        band.set(24, 3, BandKind.GROOVE);
        band.set(24, 4, BandKind.GROOVE);
        band.set(25, 8, BandKind.GROOVE);
        band.set(25, 9, BandKind.GROOVE);
        assertEquals(LongList.of(band.pos(24, 3)), BandChecks.cornerRuns(band));
    }

    @Test
    void orphanWallBlock() {
        Band band = Band.pillar(0, 16);
        band.set(4, 5, BandKind.GROOVE);
        band.set(6, 5, BandKind.GROOVE);
        band.set(5, 4, BandKind.GROOVE);
        band.set(5, 6, BandKind.GROOVE);
        assertEquals(LongList.of(band.pos(5, 5)), BandChecks.orphans(band));
        band.set(5, 6, BandKind.SURFACE);
        assertTrue(BandChecks.orphans(band).isEmpty());
    }

    @Test
    void narrowFaceInsideTheBand() {
        Band band = Band.pillar(0, 16);
        for (int ring = 0; ring < 48; ring++) {
            band.set(ring, 4, BandKind.GROOVE);
            band.set(ring, 6, BandKind.GROOVE);
        }
        assertEquals(LongList.of(band.pos(0, 5)), BandChecks.narrowFaces(band));
        band.set(0, 6, BandKind.WALL);
        band.set(1, 6, BandKind.WALL);
        assertTrue(BandChecks.narrowFaces(band).isEmpty());
    }

}
