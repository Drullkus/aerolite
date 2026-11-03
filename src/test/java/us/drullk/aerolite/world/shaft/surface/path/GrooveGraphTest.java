package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the 2-core, the groove graph, the cycle rank and the split of long edges.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class GrooveGraphTest {

    /** A 5x5 square ring of grooves at ring 2 to 6 and axis 2 to 6, with a dead-end branch from (6, 4) to (9, 4). */
    private static Band squareWithBranch() {
        Band band = Band.pillar(0, 16);
        for (int i = 2; i <= 6; i++) {
            band.set(i, 2, BandKind.GROOVE);
            band.set(i, 6, BandKind.GROOVE);
            band.set(2, i, BandKind.GROOVE);
            band.set(6, i, BandKind.GROOVE);
        }
        for (int ring = 7; ring <= 9; ring++) {
            band.set(ring, 4, BandKind.GROOVE);
        }
        return band;
    }

    @Test
    void coreDropsDeadEnds() {
        Band band = squareWithBranch();
        LongSet core = Grooves.core(band);
        assertEquals(16, core.size());
        assertFalse(core.contains(band.pos(8, 4)));
        assertEquals(LongSet.of(band.pos(6, 4)), Grooves.attachments(band, core));
        assertEquals(1, Grooves.cycleRank(band));
    }

    @Test
    void cycleWithoutJunctionGetsItsLowestBlockAsVertex() {
        Band band = squareWithBranch();
        GrooveGraph graph = GrooveGraph.of(band);
        assertEquals(LongList.of(band.pos(2, 2)), graph.vertices());
        assertEquals(1, graph.edges().size());
        GrooveEdge loop = graph.edges().get(0);
        assertEquals(loop.u(), loop.v());
        assertEquals(15, loop.chain().size());
        assertEquals(band.pos(3, 2), loop.chain().getLong(0));
    }

    @Test
    void junctionsSplitChains() {
        Band band = squareWithBranch();
        for (int axis = 3; axis <= 5; axis++) {
            band.set(4, axis, BandKind.GROOVE);
        }
        GrooveGraph graph = GrooveGraph.of(band);
        assertEquals(LongList.of(band.pos(4, 2), band.pos(4, 6)), graph.vertices());
        assertEquals(3, graph.edges().size());
        List<GrooveEdge> sorted = new ArrayList<>(graph.edges());
        sorted.sort(GrooveEdge.SORT);
        assertEquals(sorted, graph.edges());
        assertEquals(2, Grooves.cycleRank(band));
    }

    @Test
    void cornerBlocksDoNotLinkAlongTheAxis() {
        assertEquals(1, Grooves.cycleRank(rectangle(2)));
        assertEquals(0, Grooves.cycleRank(rectangle(12)));
    }

    /** The groove perimeter of the 3x3 square at ring {@code ring} to {@code ring + 2} and axis 2 to 4. */
    private static Band rectangle(int ring) {
        Band band = Band.pillar(0, 16);
        for (int i = 0; i <= 2; i++) {
            band.set(ring + i, 2, BandKind.GROOVE);
            band.set(ring + i, 4, BandKind.GROOVE);
            band.set(ring, 2 + i, BandKind.GROOVE);
            band.set(ring + 2, 2 + i, BandKind.GROOVE);
        }
        return band;
    }

    @Test
    void longEdgeGetsAVertexAtEachChunkEntry() {
        Band band = TestPatterns.ladder(0, 6);
        GrooveGraph unsplit = GrooveGraph.unsplit(band);
        assertTrue(unsplit.edges().stream().anyMatch(GrooveGraphTest::spansMoreThanOneSegment));
        GrooveGraph graph = GrooveGraph.of(band);
        assertFalse(graph.edges().stream().anyMatch(GrooveGraphTest::spansMoreThanOneSegment));
        // Split vertices sit where the line enters chunk 2 and chunk 3.
        assertTrue(graph.vertices().contains(band.pos(3, 32)));
        assertTrue(graph.vertices().contains(band.pos(3, 48)));
        assertTrue(graph.vertices().contains(band.pos(9, 32)));
        assertFalse(unsplit.vertices().contains(band.pos(3, 32)));
        int blocks = unsplit.edges().stream().mapToInt(e -> e.chain().size() + 1).sum();
        int splitBlocks = graph.edges().stream().mapToInt(e -> e.chain().size() + 1).sum();
        assertEquals(blocks, splitBlocks);
    }

    private static boolean spansMoreThanOneSegment(GrooveEdge edge) {
        return Math.abs(LoopRationing.segmentOf(edge.u()) - LoopRationing.segmentOf(edge.v())) > 1;
    }

}
