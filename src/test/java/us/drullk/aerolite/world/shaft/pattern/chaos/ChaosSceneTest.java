package us.drullk.aerolite.world.shaft.pattern.chaos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandChecks;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.Grooves;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks of the chaos prototype: no 2x2 groove square, no plus crossing, no orphan wall block, no face under 2 blocks,
 * no unbreakable cycle edge, chunk purity, and a forest at a loop share of 0.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class ChaosSceneTest {

    private static final int CHUNKS = 12;
    private static final int TREE_END = CHUNKS % 2 == 1 ? CHUNKS : CHUNKS - 1;
    private static final ChaosSettings.Cells VORONOI = ChaosSettings.Cells.VORONOI;
    private static final ChaosSettings.Cells DELAUNAY = ChaosSettings.Cells.DELAUNAY;

    /** The parameter sets of the prototype check, from the slider minimum 6, for seeds 1 and 2. */
    static Stream<ChaosChecks.Case> cases() {
        List<ChaosChecks.Case> out = new ArrayList<>();
        for (long seed = 1; seed <= 2; seed++) {
            ChaosChecks.Case base = ChaosChecks.Case.voronoi(6, 0.5, seed);
            List<ChaosChecks.Case> group = new ArrayList<>();
            for (int spacing : new int[] {6, 8, 12}) {
                for (double jitter : new double[] {0, 0.5, 1}) {
                    group.add(ChaosChecks.Case.voronoi(spacing, jitter, seed));
                }
                group.add(ChaosChecks.Case.voronoi(spacing, 0.5, seed).withCells(DELAUNAY));
            }
            group.add(base.withRadius(0));
            group.add(base.withRadius(2));
            for (ChaosSettings.Cells cells : ChaosSettings.Cells.values()) {
                for (boolean cut : new boolean[] {false, true}) {
                    group.add(base.withCells(cells).withBox().withPaths(cut, cut));
                }
                group.add(base.withCells(cells).withBox().withPaths(true, false));
                for (int ring : new int[] {64, 28}) {
                    group.add(base.withCells(cells).withRing(ring).withPaths(true, false));
                }
            }
            for (ChaosChecks.Case c : group) {
                out.add(c);
                out.add(c.withShare(0));
            }
            for (ChaosSettings.Cells cells : ChaosSettings.Cells.values()) {
                for (int spacing : new int[] {6, 8}) {
                    for (boolean cut : new boolean[] {false, true}) {
                        for (double share : new double[] {1, 0.5, 0}) {
                            out.add(ChaosChecks.Case.voronoi(spacing, 0.5, seed).withCells(cells).withPaths(true, cut).withShare(share));
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void sceneKeepsTheRules(ChaosChecks.Case c) {
        ChaosScene.Result scene = ChaosChecks.scene(c, 0, CHUNKS);
        Band band = scene.band();
        assertEquals(List.of(), BandChecks.twoWide(band), "2-wide path");
        assertEquals(List.of(), BandChecks.crossings(band), "plus crossing");
        assertEquals(List.of(), ChaosChecks.orphans(band), "orphan wall block");
        assertEquals(0, ChaosChecks.facesUnder(band, 2), "face under 2 blocks");
        if (scene.result() != null) {
            assertEquals(0, scene.result().unbreakable(), "unbreakable cycle edge");
        }
        for (int chunk = 0; chunk < CHUNKS; chunk++) {
            Band alone = ChaosChecks.scene(c, chunk, chunk + 1).band();
            assertTrue(alone.sameKinds(band.crop(chunk * ChaosChecks.CHUNK, ChaosChecks.CHUNK)), "chunk " + chunk + " differs alone");
        }
        if (c.share() == 0) {
            assertNotNull(scene.result());
            int[] forest = ChaosChecks.vertexForest(scene.result().graph(), scene.result().removed(), 0, TREE_END);
            assertEquals(0, forest[0], "kept vertex graph has a cycle");
            assertEquals(forest[1], forest[2], "kept vertex graph joins other vertices than the full graph");
            assertEquals(0, Grooves.cycleRank(band.crop(0, TREE_END * ChaosChecks.CHUNK)), "groove blocks have a cycle");
        }
    }

    /** With the jitter cap, every Voronoi stone stays 3 blocks across at every spacing of the slider. */
    @ParameterizedTest
    @MethodSource("widths")
    void voronoiStonesStayThreeAcross(ChaosChecks.Case c) {
        Band band = ChaosChecks.scene(c, 0, 6).band();
        assertEquals(0, ChaosChecks.facesUnder(band, 3), "stone under 3 blocks");
    }

    static Stream<ChaosChecks.Case> widths() {
        List<ChaosChecks.Case> out = new ArrayList<>();
        for (int ring : new int[] {48, 64}) {
            for (int spacing = ChaosSettings.MIN_SPACING; spacing <= ChaosSettings.MAX_SPACING; spacing++) {
                for (double jitter : new double[] {0, 0.5, 1}) {
                    for (long seed = 1; seed <= 2; seed++) {
                        out.add(ChaosChecks.Case.voronoi(spacing, jitter, seed).withRing(ring));
                    }
                }
            }
        }
        return out.stream();
    }

    /** Every face keeps a 2x2 square at every spacing, for both cell types and the three rings. */
    @ParameterizedTest
    @MethodSource("faces")
    void everyFaceKeepsASquare(ChaosChecks.Case c) {
        Band band = ChaosChecks.scene(c, 0, 6).band();
        assertEquals(0, ChaosChecks.facesUnder(band, 2), "face under 2 blocks");
        assertEquals(List.of(), BandChecks.twoWide(band), "2-wide path");
        assertEquals(List.of(), BandChecks.crossings(band), "plus crossing");
    }

    static Stream<ChaosChecks.Case> faces() {
        List<ChaosChecks.Case> out = new ArrayList<>();
        for (int ring : new int[] {48, 64, 28}) {
            for (ChaosSettings.Cells cells : ChaosSettings.Cells.values()) {
                for (int spacing = ChaosSettings.MIN_SPACING; spacing <= ChaosSettings.MAX_SPACING; spacing++) {
                    out.add(ChaosChecks.Case.voronoi(spacing, 1, 3).withRing(ring).withCells(cells));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void sameSeedGivesSameBand(ChaosChecks.Case c) {
        assertTrue(ChaosChecks.scene(c, 2, 5).band().sameKinds(ChaosChecks.scene(c, 2, 5).band()));
    }

    @Test
    void otherSeedGivesOtherBand() {
        ChaosChecks.Case one = ChaosChecks.Case.voronoi(6, 0.5, 1);
        ChaosChecks.Case two = ChaosChecks.Case.voronoi(6, 0.5, 2);
        assertTrue(!ChaosChecks.scene(one, 0, 2).band().sameKinds(ChaosChecks.scene(two, 0, 2).band()));
    }

    /** At a loop share of 1, no groove goes. A lower share removes a superset. */
    @Test
    void higherShareRemovesASubset() {
        ChaosChecks.Case c = ChaosChecks.Case.voronoi(8, 0.5, 1).withPaths(true, true);
        ChaosScene.Result zero = ChaosChecks.scene(c.withShare(0), 0, 6);
        ChaosScene.Result half = ChaosChecks.scene(c.withShare(0.5), 0, 6);
        assertNotNull(zero.result());
        assertNotNull(half.result());
        assertTrue(zero.result().removed().containsAll(half.result().removed()));
        assertTrue(zero.result().removed().size() > half.result().removed().size());
        ChaosScene.Result full = ChaosChecks.scene(c.withShare(1), 0, 6);
        assertEquals(null, full.result());
    }

    /** Cut paths are grooves. Plain surface paths are surface path blocks on the wall layer. */
    @Test
    void surfacePathsTakeTheirKind() {
        ChaosChecks.Case c = ChaosChecks.Case.voronoi(8, 0.5, 1);
        ChaosScene.Result surface = ChaosChecks.scene(c.withPaths(true, false), 0, 6);
        ChaosScene.Result cut = ChaosChecks.scene(c.withPaths(true, true), 0, 6);
        assertTrue(!surface.paths().isEmpty() && !cut.paths().isEmpty(), "no surface path");
        assertTrue(surface.band().blocksOf(BandKind.SURFACE).size() > 0, "no surface path block");
        assertEquals(0, cut.band().blocksOf(BandKind.SURFACE).size(), "a cut path left a surface path block");
    }

    /** The table of the prototype README: the cells around the ring and the jitter cap. */
    @Test
    void gridAndCapFollowTheTables() {
        int[][] grid = {{48, 6, 8}, {64, 6, 11}, {64, 12, 5}, {28, 6, 5}, {28, 8, 4}};
        for (int[] row : grid) {
            ChaosParams params = ChaosChecks.Case.voronoi(row[1], 0, 1).withRing(row[0]).params();
            assertEquals(row[2], params.cellsAround(), "cells around ring " + row[0] + " at spacing " + row[1]);
            assertEquals((double) row[0] / row[2], params.cellWidth(), 1e-9);
        }
        assertEquals(0.25, ChaosParams.jitterCap(6));
        assertEquals(0.6, ChaosParams.jitterCap(7));
        assertEquals(0.7, ChaosParams.jitterCap(8));
        assertEquals(0.85, ChaosParams.jitterCap(9));
        assertEquals(0.85, ChaosParams.jitterCap(10));
        assertEquals(1, ChaosParams.jitterCap(11));
        assertEquals(1, ChaosParams.jitterCap(12));
        ChaosParams params = ChaosParams.of(ChaosSettings.DEFAULT.withSpacing(4).withJitter(1), 48);
        assertEquals(ChaosSettings.MIN_SPACING, params.spacing(), "the spacing does not clamp to the slider minimum");
        assertEquals(0.25, params.jitter(), "the jitter does not take the cap");
        assertEquals(LoopRationing.margin(LoopRationing.DEFAULT_LOOK) + 2 + 1, params.margin());
    }

    /** Loops off gives the behavior of a loop share of 0. */
    @Test
    void loopsOffIsShareZero() {
        ChaosSettings settings = ChaosSettings.DEFAULT.withPaths(ChaosSettings.DEFAULT.paths().withAllowLoops(false).withLoopShare(0.8F));
        assertEquals(0, ChaosParams.of(settings, 48).share());
    }

}
