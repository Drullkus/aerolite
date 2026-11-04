package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the spanning forest sampler.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class MazeGraphTest {

    /** Two rings of 6 nodes with axis edges to a third ring of 6 nodes, plus an isolated node. */
    private static MazeGraph threeComponents() {
        MazeGraph graph = new MazeGraph();
        for (int k = 0; k < 19; k++) {
            graph.addNode();
        }
        for (int k = 0; k < 6; k++) {
            graph.addEdge(k, (k + 1) % 6, false);
            graph.addEdge(6 + k, 6 + (k + 1) % 6, false);
            graph.addEdge(k, 6 + k, true);
            graph.addEdge(12 + k, 12 + (k + 1) % 6, false);
        }
        return graph;
    }

    @Test
    void forestHasOneRootPerComponent() {
        for (long seed = 0; seed < 32; seed++) {
            int[] parent = threeComponents().sampleForest(new XoroshiroRandomSource(seed), 0.5F);
            int[] roots = new int[parent.length];
            for (int node = 0; node < parent.length; node++) {
                int root = node;
                int steps = 0;
                while (parent[root] >= 0) {
                    root = parent[root];
                    assertTrue(++steps <= parent.length, "The parents hold a loop");
                }
                roots[node] = root;
            }
            for (int node = 0; node < 12; node++) {
                assertEquals(roots[0], roots[node], "The first component is not one tree");
                assertTrue(roots[0] < 12);
            }
            for (int node = 12; node < 18; node++) {
                assertEquals(roots[12], roots[node], "The second component is not one tree");
                assertTrue(roots[12] >= 12 && roots[12] < 18);
            }
            assertEquals(-1, parent[18], "The isolated node is not a root");
        }
    }

    /** A connected graph draws the root and the walks like the single tree sampler. */
    @Test
    void connectedGraphDrawsOneRootFromTheWholeGraph() {
        MazeGraph graph = new MazeGraph();
        for (int k = 0; k < 10; k++) {
            graph.addNode();
        }
        for (int k = 0; k < 10; k++) {
            graph.addEdge(k, (k + 1) % 10, false);
        }
        for (long seed = 0; seed < 16; seed++) {
            int root = new XoroshiroRandomSource(seed).nextInt(10);
            int[] parent = graph.sampleForest(new XoroshiroRandomSource(seed), 0.5F);
            assertEquals(-1, parent[root]);
            for (int node = 0; node < 10; node++) {
                assertTrue(node == root || parent[node] >= 0);
            }
        }
    }

}
