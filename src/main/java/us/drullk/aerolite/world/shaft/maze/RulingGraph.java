package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * The nodes of a set of rulings in one graph, with the names of all nodes.
 * Ring edges join neighbor nodes of one ruling. Axis edges join two rulings two blocks apart.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
final class RulingGraph {

    final MazeGraph graph = new MazeGraph();
    private final List<MazeNode> names = new ArrayList<>();
    private final Int2IntMap firstNodeOfRuling = new Int2IntOpenHashMap();

    RulingGraph(int[] rulings) {
        for (int along : rulings) {
            this.firstNodeOfRuling.put(along, this.graph.size());
            for (int v = 0; v < Ring.NODES; v++) {
                this.add(new MazeNode.Ruling(along, v));
            }
        }
        for (int along : rulings) {
            for (int v = 0; v < Ring.NODES; v++) {
                this.graph.addEdge(this.node(along, v), this.node(along, (v + 1) % Ring.NODES));
                if (this.firstNodeOfRuling.containsKey(along + 2) && !Ring.isCorner(v)) {
                    this.graph.addEdge(this.node(along, v), this.node(along + 2, v));
                }
            }
        }
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

    /** Adds a virtual node that touches every interior node of one ruling. */
    int addVirtual(int along) {
        int virtual = this.addVirtual();
        for (int v = 0; v < Ring.NODES; v++) {
            if (!Ring.isCorner(v)) {
                this.graph.addEdge(virtual, this.node(along, v));
            }
        }
        return virtual;
    }

    /** Adds the 7x7 patch nodes of one junction box and their grid edges. Returns the node of (-3, -3). */
    int addPatch(int owner) {
        int first = this.graph.size();
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                this.add(new MazeNode.Patch(owner, i, j));
            }
        }
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                int n = patchNode(first, i, j);
                if (i < 3) {
                    this.graph.addEdge(n, patchNode(first, i + 1, j));
                }
                if (j < 3) {
                    this.graph.addEdge(n, patchNode(first, i, j + 1));
                }
            }
        }
        return first;
    }

    static int patchNode(int first, int i, int j) {
        return first + (i + 3) * 7 + (j + 3);
    }

    MazeNode[] names() {
        return this.names.toArray(MazeNode[]::new);
    }

}
