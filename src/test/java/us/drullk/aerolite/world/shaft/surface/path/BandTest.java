package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the band grid, its wrap, its corner rule and its crop.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class BandTest {

    @Test
    void ringWrapsAndAxisDoesNot() {
        Band band = Band.pillar(16, 16);
        assertEquals(band.pos(47, 20), band.pos(-1, 20));
        assertEquals(band.pos(0, 20), band.step(band.pos(47, 20), BandDirection.RING_PLUS));
        assertEquals(BandPos.NONE, band.step(band.pos(5, 31), BandDirection.AXIS_PLUS));
        assertEquals(BandPos.NONE, band.step(band.pos(5, 16), BandDirection.AXIS_MINUS));
        assertEquals(3, band.neighbors(band.pos(5, 16)).size());
        assertEquals(4, band.neighbors(band.pos(5, 20)).size());
    }

    @Test
    void positionsSortByAxisThenRing() {
        Band band = new Band(28, -40, 80);
        assertTrue(band.pos(27, -3) < band.pos(0, -2));
        assertTrue(band.pos(1, 5) < band.pos(2, 5));
        assertEquals(-3, BandPos.axis(band.pos(27, -3)));
        assertEquals(27, BandPos.ring(band.pos(27, -3)));
    }

    @Test
    void axisStepOnCornerDoesNotLink() {
        Band band = Band.pillar(0, 16);
        long corner = band.pos(12, 5);
        assertTrue(band.isCorner(corner));
        assertFalse(band.linked(corner, band.pos(12, 6)));
        assertTrue(band.linked(corner, band.pos(13, 5)));
        assertTrue(band.linked(band.pos(13, 5), band.pos(13, 6)));
    }

    @Test
    void customRingKeepsItsCorners() {
        Band band = new Band(64, 0, 16, 0, 7, 32);
        assertTrue(band.isCornerRing(7));
        assertFalse(band.isCornerRing(12));
        assertEquals(3, band.corners().length);
        assertThrows(IllegalArgumentException.class, () -> new Band(28, 0, 16, 28));
    }

    @Test
    void cropCopiesKindsAndNodesOfTheRange() {
        Band band = Band.pillar(0, 32);
        band.set(4, 20, BandKind.GROOVE);
        band.set(4, 3, BandKind.SURFACE);
        band.grooveNodes().add(band.pos(4, 20));
        band.faceNodes().add(band.pos(4, 3));
        Band crop = band.crop(16, 16);
        assertEquals(BandKind.GROOVE, crop.get(4, 20));
        assertTrue(crop.grooveNodes().contains(band.pos(4, 20)));
        assertTrue(crop.faceNodes().isEmpty());
        crop.set(4, 20, BandKind.WALL);
        assertEquals(BandKind.GROOVE, band.get(4, 20));
        assertThrows(IndexOutOfBoundsException.class, () -> band.crop(20, 16));
        assertThrows(IndexOutOfBoundsException.class, () -> crop.get(4, 3));
    }

    @Test
    void directionOfNeighborSteps() {
        Band band = Band.pillar(0, 16);
        assertEquals(BandDirection.RING_MINUS, band.direction(band.pos(0, 4), band.pos(47, 4)));
        assertEquals(BandDirection.AXIS_PLUS, band.direction(band.pos(0, 4), band.pos(0, 5)));
        assertNull(band.direction(band.pos(0, 4), band.pos(2, 4)));
    }

    @Test
    void blocksOfKindComeSorted() {
        Band band = Band.pillar(0, 16);
        band.set(30, 2, BandKind.BOX);
        band.set(3, 9, BandKind.BOX);
        band.set(1, 2, BandKind.BOX);
        LongList boxes = band.blocksOf(BandKind.BOX);
        assertEquals(LongList.of(band.pos(1, 2), band.pos(30, 2), band.pos(3, 9)), boxes);
    }

}
