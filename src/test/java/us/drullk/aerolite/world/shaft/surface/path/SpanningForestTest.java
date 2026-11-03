package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks Wilson's algorithm on a graph of two components with a loop edge.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class SpanningForestTest {

    // A 4-cycle 1-2-3-4 with the chord 1-3, a triangle 10-11-12, and a loop on 12.
    private static final LongList NODES = LongList.of(1, 2, 3, 4, 10, 11, 12);
    private static final LongList US = LongList.of(1, 2, 3, 4, 1, 10, 11, 12, 12);
    private static final LongList VS = LongList.of(2, 3, 4, 1, 3, 11, 12, 10, 12);

    @Test
    void forestSpansEachComponent() {
        for (long seed = 0; seed < 200; seed++) {
            IntSet tree = SpanningForest.sample(NODES, US, VS, new XoroshiroRandomSource(seed));
            assertEquals(NODES.size() - 2, tree.size());
            assertFalse(tree.contains(8));
            LongUnionFind uf = new LongUnionFind();
            for (int e : tree) {
                assertTrue(uf.union(US.getLong(e), VS.getLong(e)), "the forest has a cycle");
            }
            assertEquals(2, uf.count(NODES));
        }
    }

    @Test
    void sameSeedGivesSameForest() {
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(SpanningForest.sample(NODES, US, VS, new XoroshiroRandomSource(seed)),
                SpanningForest.sample(NODES, US, VS, new XoroshiroRandomSource(seed)));
        }
    }

    @Test
    void everyTreeOfTheCycleComponentAppears() {
        Set<IntSet> seen = new HashSet<>();
        for (long seed = 0; seed < 2000; seed++) {
            IntSet tree = SpanningForest.sample(NODES, US, VS, new XoroshiroRandomSource(seed));
            tree.removeIf(e -> e >= 5);
            seen.add(tree);
        }
        // The 4-cycle with one chord has 8 spanning trees.
        assertEquals(8, seen.size());
    }

}
