package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import us.drullk.aerolite.world.shaft.surface.RingShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The nodes of a set of rulings in one graph, with the names of all nodes.
 * A ruling holds the nodes of every ring of the shape.
 * Ring edges join neighbor nodes of one ring on one ruling. Axis edges join two rulings two blocks apart, except at corner nodes.
 * Virtual node edges are axis edges.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class RulingGraph {

    final MazeGraph graph = new MazeGraph();
    private final RingShape ring;
    private final List<MazeNode> names = new ArrayList<>();
    private final Int2IntMap firstNodeOfRuling = new Int2IntOpenHashMap();

    RulingGraph(RingShape ring, int[] rulings) {
        this.ring = ring;
        int nodes = ring.nodeCount();
        for (int along : rulings) {
            this.firstNodeOfRuling.put(along, this.graph.size());
            for (int v = 0; v < nodes; v++) {
                this.add(new MazeNode.Ruling(along, v));
            }
        }
        for (int along : rulings) {
            for (int v = 0; v < nodes; v++) {
                this.graph.addEdge(this.node(along, v), this.node(along, ring.nextNode(v)), false);
                if (this.firstNodeOfRuling.containsKey(along + 2) && ring.hasAxisEdge(v)) {
                    this.graph.addEdge(this.node(along, v), this.node(along + 2, v), true);
                }
            }
        }
    }

    boolean has(int along) {
        return this.firstNodeOfRuling.containsKey(along);
    }

    int node(int along, int v) {
        return this.firstNodeOfRuling.get(along) + v;
    }

    int add(MazeNode name) {
        this.names.add(name);
        return this.graph.addNode();
    }

    int addVirtual() {
        this.names.add(null);
        return this.graph.addNode();
    }

    /** Adds one virtual node per ring. Each one touches every node of its ring on the ruling that has an axis edge. */
    int[] addVirtuals(int along) {
        int[] virtuals = new int[this.ring.rings()];
        for (int r = 0; r < virtuals.length; r++) {
            virtuals[r] = this.addVirtual();
            int first = this.ring.firstNode(r);
            for (int v = first; v < first + this.ring.length(r) / 2; v++) {
                if (this.ring.hasAxisEdge(v)) {
                    this.graph.addEdge(virtuals[r], this.node(along, v), true);
                }
            }
        }
        return virtuals;
    }

    /**
     * Adds the patch nodes of one junction box and their grid edges. Returns the node of the lowest indices.
     *
     * @param iAlongAxis true when patch index i runs along the shaft axis, false when index j does
     */
    int addPatch(int owner, boolean iAlongAxis) {
        int first = this.graph.size();
        for (int i = -Ring.PATCH_INDEX; i <= Ring.PATCH_INDEX; i++) {
            for (int j = -Ring.PATCH_INDEX; j <= Ring.PATCH_INDEX; j++) {
                this.add(new MazeNode.Patch(owner, i, j));
            }
        }
        for (int i = -Ring.PATCH_INDEX; i <= Ring.PATCH_INDEX; i++) {
            for (int j = -Ring.PATCH_INDEX; j <= Ring.PATCH_INDEX; j++) {
                int n = this.patchNode(first, i, j);
                if (i < Ring.PATCH_INDEX) {
                    this.graph.addEdge(n, this.patchNode(first, i + 1, j), iAlongAxis);
                }
                if (j < Ring.PATCH_INDEX) {
                    this.graph.addEdge(n, this.patchNode(first, i, j + 1), !iAlongAxis);
                }
            }
        }
        return first;
    }

    int patchNode(int first, int i, int j) {
        return first + (i + Ring.PATCH_INDEX) * Ring.PATCH_NODES + (j + Ring.PATCH_INDEX);
    }

    MazeNode[] names() {
        return this.names.toArray(MazeNode[]::new);
    }

}
