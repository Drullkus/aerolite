package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the surface path candidates, their shapes and the fit rules.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class SurfacePathTest {

    /** A pillar band with a groove rectangle from ring {@code r0} to {@code r1} and axis {@code a0} to {@code a1}. Inside is wall. */
    private static Band framed(int r0, int r1, int a0, int a1) {
        Band band = Band.pillar(0, 16);
        for (int ring = r0; ring <= r1; ring++) {
            band.set(ring, a0, BandKind.GROOVE);
            band.set(ring, a1, BandKind.GROOVE);
        }
        for (int axis = a0; axis <= a1; axis++) {
            band.set(r0, axis, BandKind.GROOVE);
            band.set(r1, axis, BandKind.GROOVE);
        }
        return band;
    }

    private static LongSet inside(Band band, int r0, int r1, int a0, int a1) {
        LongSet out = new LongOpenHashSet();
        for (int ring = r0 + 1; ring < r1; ring++) {
            for (int axis = a0 + 1; axis < a1; axis++) {
                out.add(band.pos(ring, axis));
            }
        }
        return out;
    }

    private static SurfacePath path(Band band, int turns, int[] ends, int... blocks) {
        LongList b = new LongArrayList();
        for (int i = 0; i < blocks.length; i += 2) {
            b.add(band.pos(blocks[i], blocks[i + 1]));
        }
        LongList e = new LongArrayList();
        for (int i = 0; i < ends.length; i += 2) {
            e.add(band.pos(ends[i], ends[i + 1]));
        }
        return new SurfacePath(b, e, turns);
    }

    @Test
    void smallTileCandidates() {
        Band band = framed(1, 5, 1, 5);
        LongSet region = inside(band, 1, 5, 1, 5);
        List<SurfacePath> candidates = SurfaceCandidates.of(band, region, LongSet.of(band.pos(3, 3)));
        Map<SurfacePath.Shape, Integer> shapes = new EnumMap<>(SurfacePath.Shape.class);
        int through = 0;
        for (SurfacePath candidate : candidates) {
            shapes.merge(candidate.shape(band), 1, Integer::sum);
            through += candidate.isThrough() ? 1 : 0;
        }
        // 4 dead ends at the center, 2 straight paths, 4 turns at the center.
        assertEquals(10, candidates.size());
        assertEquals(6, through);
        assertEquals(Map.of(SurfacePath.Shape.I, 6, SurfacePath.Shape.L, 4), shapes);
        assertEquals(candidates, SurfaceCandidates.of(band, region, LongSet.of(band.pos(3, 3))));
    }

    @Test
    void largeTileHasEveryShape() {
        Band band = framed(1, 9, 1, 9);
        LongSet region = inside(band, 1, 9, 1, 9);
        LongSet nodes = new LongOpenHashSet();
        for (int ring = 3; ring <= 7; ring += 2) {
            for (int axis = 3; axis <= 7; axis += 2) {
                nodes.add(band.pos(ring, axis));
            }
        }
        Map<SurfacePath.Shape, Integer> shapes = new EnumMap<>(SurfacePath.Shape.class);
        for (SurfacePath candidate : SurfaceCandidates.of(band, region, nodes)) {
            assertTrue(candidate.turns() <= 2);
            shapes.merge(candidate.shape(band), 1, Integer::sum);
        }
        assertEquals(4, shapes.size());
    }

    @Test
    void uWithTouchingLegsFails() {
        Band band = Band.pillar(0, 16);
        for (int ring = 0; ring < 48; ring++) {
            band.set(ring, 1, BandKind.GROOVE);
        }
        SurfacePath touching = path(band, 2, new int[] {2, 1, 3, 1}, 2, 2, 2, 3, 3, 3, 3, 2);
        SurfacePath apart = path(band, 2, new int[] {2, 1, 4, 1}, 2, 2, 2, 3, 3, 3, 4, 3, 4, 2);
        assertEquals(SurfacePath.Shape.U, touching.shape(band));
        assertEquals(SurfacePath.Shape.U, apart.shape(band));
        assertFalse(PathFit.fits(band, touching, PathFit.Rules.SURFACE));
        assertTrue(PathFit.fits(band, apart, PathFit.Rules.SURFACE));
    }

    @Test
    void pathAlongACornerFails() {
        Band band = Band.pillar(0, 16);
        for (int ring = 0; ring < 48; ring++) {
            band.set(ring, 1, BandKind.GROOVE);
        }
        assertFalse(PathFit.fits(band, path(band, 0, new int[] {12, 1}, 12, 2, 12, 3), PathFit.Rules.SURFACE));
        assertTrue(PathFit.fits(band, path(band, 0, new int[] {13, 1}, 13, 2, 13, 3), PathFit.Rules.SURFACE));
    }

    @Test
    void endGrooveWithoutNewPlusCrossing() {
        Band band = framed(1, 5, 1, 5);
        band.set(3, 6, BandKind.GROOVE);
        SurfacePath up = path(band, 0, new int[] {3, 5}, 3, 4, 3, 3);
        SurfacePath left = path(band, 0, new int[] {1, 3}, 2, 3, 3, 3);
        assertFalse(PathFit.fits(band, up, PathFit.Rules.SURFACE));
        assertTrue(PathFit.fits(band, left, PathFit.Rules.SURFACE));
    }

    @Test
    void cutTurnInASmallTileLeavesAnOrphan() {
        Band band = framed(1, 5, 1, 5);
        SurfacePath turn = path(band, 1, new int[] {1, 3, 3, 5}, 2, 3, 3, 3, 3, 4);
        assertEquals(SurfacePath.Shape.L, turn.shape(band));
        assertFalse(PathFit.fits(band, turn, PathFit.Rules.CUT));
        assertTrue(PathFit.fits(band, turn, PathFit.Rules.CUT.withOrphans(true)));
        assertTrue(PathFit.fits(band, turn, PathFit.Rules.SURFACE));
        PathFit.place(band, turn, true);
        assertEquals(LongList.of(band.pos(2, 4)), BandChecks.orphans(band));
    }

    @Test
    void faceWidthKeepsATwoByTwoSquare() {
        Band four = framed(1, 6, 1, 7);
        SurfacePath narrow = path(four, 0, new int[] {3, 1, 3, 7}, 3, 2, 3, 3, 3, 4, 3, 5, 3, 6);
        assertTrue(PathFit.fits(four, narrow, PathFit.Rules.CUT));
        assertFalse(PathFit.fits(four, narrow, PathFit.Rules.CUT.withFaceWidth(true)));
        assertFalse(PathFit.fits(four, narrow, PathFit.Rules.SURFACE.withFaceWidth(true)));
        Band five = framed(1, 7, 1, 7);
        SurfacePath middle = path(five, 0, new int[] {4, 1, 4, 7}, 4, 2, 4, 3, 4, 4, 4, 5, 4, 6);
        assertTrue(PathFit.fits(five, middle, PathFit.Rules.CUT.withFaceWidth(true)));
    }

    @Test
    void exemptTailTouchesTheRingGroovesBesideIt() {
        Band band = Band.pillar(0, 16);
        for (int ring = 0; ring < 48; ring++) {
            band.set(ring, 1, BandKind.GROOVE);
            band.set(ring, 5, BandKind.GROOVE);
        }
        band.set(3, 5, BandKind.WALL);
        for (int axis = 6; axis <= 8; axis++) {
            band.set(3, axis, BandKind.GROOVE);
        }
        SurfacePath overTail = path(band, 0, new int[] {3, 1, 3, 6}, 3, 2, 3, 3, 3, 4, 3, 5);
        assertFalse(PathFit.fits(band, overTail, PathFit.Rules.CUT));
        assertTrue(PathFit.fits(band, overTail, PathFit.Rules.CUT.withExempt(LongSet.of(band.pos(3, 5)))));
    }

    @Test
    void pickFallsBackToTheOtherClassAndRepeats() {
        Band band = framed(1, 5, 1, 5);
        LongSet region = inside(band, 1, 5, 1, 5);
        List<SurfacePath> candidates = SurfaceCandidates.of(band, region, LongSet.of(band.pos(3, 3)));
        List<SurfacePath> deadOnly = candidates.stream().filter(p -> !p.isThrough()).toList();
        for (long seed = 0; seed < 50; seed++) {
            SurfacePath any = PathFit.pick(band, candidates, new XoroshiroRandomSource(seed), PathFit.Rules.SURFACE);
            assertNotNull(any);
            assertEquals(any, PathFit.pick(band, candidates, new XoroshiroRandomSource(seed), PathFit.Rules.SURFACE));
            SurfacePath dead = PathFit.pick(band, deadOnly, new XoroshiroRandomSource(seed), PathFit.Rules.SURFACE, 0);
            assertNotNull(dead);
            assertFalse(dead.isThrough());
        }
    }

}
