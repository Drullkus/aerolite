package us.drullk.aerolite.world.shaft.maze;

import tamaized.beanification.Autowired;
import tamaized.beanification.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects and prunes the tree edges of a sampled maze graph.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component
public final class MazeEdges {

    private final Ring ring;

    public MazeEdges(@Autowired Ring ring) {
        this.ring = ring;
    }

    /** Collects the tree edges of a sampled graph. A node without a name is a virtual node. */
    List<MazeEdge> treeEdges(int[] parent, @Nullable MazeNode[] names, MazeEdge.VirtualTarget virtualTarget) {
        List<MazeEdge> edges = new ArrayList<>();
        for (int node = 0; node < parent.length; node++) {
            int p = parent[node];
            if (p < 0) {
                continue;
            }
            MazeNode a = names[node];
            MazeNode b = names[p];
            if (a == null) {
                a = virtualTarget.target(node, b);
            } else if (b == null) {
                b = virtualTarget.target(p, a);
            }
            edges.add(new MazeEdge(a, b));
        }
        return edges;
    }

    /** Drops every corner node of degree one together with its edge. */
    List<MazeEdge> pruneCornerLeaves(List<MazeEdge> edges) {
        Map<MazeNode, Integer> degree = new HashMap<>();
        for (MazeEdge edge : edges) {
            degree.merge(edge.a(), 1, Integer::sum);
            degree.merge(edge.b(), 1, Integer::sum);
        }
        List<MazeEdge> kept = new ArrayList<>(edges.size());
        for (MazeEdge edge : edges) {
            if (!this.isCornerLeaf(edge.a(), degree) && !this.isCornerLeaf(edge.b(), degree)) {
                kept.add(edge);
            }
        }
        return kept;
    }

    private boolean isCornerLeaf(MazeNode node, Map<MazeNode, Integer> degree) {
        return node instanceof MazeNode.Ruling ruling && this.ring.isCorner(ruling.node()) && degree.get(node) == 1;
    }

}
