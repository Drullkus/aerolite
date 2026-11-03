package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.shaft.MixedRandomFactory;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the segment rationing: several components per tree segment, the exemptions and the segment owner.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class LoopRationingTest {

    private static LoopRationing.Result ladder(long seed, int look) {
        Band band = TestPatterns.ladder(-10, 22);
        GrooveGraph graph = GrooveGraph.of(band);
        MixedRandomFactory random = new MixedRandomFactory(seed).fork("segment");
        return LoopRationing.sample(graph, -2, 14, s -> random.at(0, s, 0), 0,
            LoopRationing.removable(band, new LongOpenHashSet()), look);
    }

    @Test
    void connectorSeesTheJoinsAboveItsTreeSegment() {
        for (long seed = 1; seed <= 20; seed++) {
            LoopRationing.Result result = ladder(seed, LoopRationing.DEFAULT_LOOK);
            int[] forest = TestPatterns.vertexForest(result.graph(), result.removed(), 0, 11);
            assertEquals(0, forest[0], "kept vertex graph has a cycle");
            assertEquals(forest[1], forest[2]);
            assertEquals(1, forest[2]);
        }
    }

    @Test
    void connectorWithoutLookKeepsCyclesThroughTwoConnectors() {
        int cycles = 0;
        for (long seed = 1; seed <= 20; seed++) {
            LoopRationing.Result result = ladder(seed, 0);
            cycles += TestPatterns.vertexForest(result.graph(), result.removed(), 0, 11)[0];
        }
        assertTrue(cycles > 0, "the ladder needs the look to stay a tree");
    }

    @Test
    void zeroShareLeavesTheSameComponentsAsTheFullGraph() {
        // A second ladder with rungs in every chunk is a second component.
        Band band = TestPatterns.ladder(-10, 22);
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            band.set(27, axis, BandKind.GROOVE);
            band.set(33, axis, BandKind.GROOVE);
            if (Math.floorMod(axis, 8) == 2) {
                for (int ring = 28; ring < 33; ring++) {
                    band.set(ring, axis, BandKind.GROOVE);
                }
            }
        }
        MixedRandomFactory random = new MixedRandomFactory(7).fork("segment");
        LoopRationing.Result result = LoopRationing.ration(band, new LongOpenHashSet(), -2, 14, s -> random.at(0, s, 0), 0);
        int[] forest = TestPatterns.vertexForest(result.graph(), result.removed(), 0, 11);
        assertEquals(0, forest[0]);
        assertEquals(forest[1], forest[2]);
        assertEquals(2, forest[2]);
        assertEquals(0, result.unbreakable());
    }

    @Test
    void fixedBlocksAndAttachmentsNeverGo() {
        // A square groove ring with a dead-end branch that hangs on (6, 4).
        Band band = Band.pillar(0, 16);
        for (int i = 2; i <= 6; i++) {
            band.set(i, 2, BandKind.GROOVE);
            band.set(i, 6, BandKind.GROOVE);
            band.set(2, i, BandKind.GROOVE);
            band.set(6, i, BandKind.GROOVE);
        }
        band.set(7, 4, BandKind.GROOVE);
        band.set(8, 4, BandKind.GROOVE);
        LongSet fixed = new LongOpenHashSet();
        fixed.add(band.pos(3, 2));
        Function<GrooveEdge, LongList> removable = LoopRationing.removable(band, fixed);
        LongSet offered = new LongOpenHashSet();
        GrooveGraph.of(band).edges().forEach(edge -> offered.addAll(removable.apply(edge)));
        assertFalse(offered.contains(band.pos(6, 4)), "attachment block offered");
        assertFalse(offered.contains(band.pos(3, 2)), "fixed block offered");
        assertEquals(13, offered.size());
    }

    @Test
    void removableBlocksAvoidGrooveNodesWhenTheyCan() {
        Band band = TestPatterns.tiles(0, 4);
        Function<GrooveEdge, LongList> removable = LoopRationing.removable(band, new LongOpenHashSet());
        for (GrooveEdge edge : GrooveGraph.of(band).edges()) {
            LongList blocks = removable.apply(edge);
            boolean hasCalm = edge.chain().longStream().anyMatch(pos -> !band.grooveNodes().contains(pos));
            if (hasCalm) {
                assertTrue(blocks.longStream().noneMatch(band.grooveNodes()::contains));
            }
        }
    }

    @Test
    void edgeOwnerIsTheConnector() {
        assertEquals(4, LoopRationing.owner(4, 4));
        assertEquals(5, LoopRationing.owner(4, 5));
        assertEquals(5, LoopRationing.owner(6, 5));
        assertEquals(-1, LoopRationing.owner(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> LoopRationing.owner(4, 6));
        assertEquals(-1, LoopRationing.segmentOf(BandPos.pack(0, -1)));
    }

}
