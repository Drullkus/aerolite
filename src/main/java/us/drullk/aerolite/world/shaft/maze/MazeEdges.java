package us.drullk.aerolite.world.shaft.maze;

import tamaized.beanification.Component;
import us.drullk.aerolite.world.shaft.surface.RingShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects and prunes the tree edges of a sampled maze graph.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class MazeEdges {

    public MazeEdges() {
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

    /** Drops every corner node of degree one together with its edge. A corner node whose edge is a crossing stays. */
    List<MazeEdge> pruneCornerLeaves(List<MazeEdge> edges, RingShape ring) {
        Map<MazeNode, Integer> degree = new HashMap<>();
        for (MazeEdge edge : edges) {
            degree.merge(edge.a(), 1, Integer::sum);
            degree.merge(edge.b(), 1, Integer::sum);
        }
        List<MazeEdge> kept = new ArrayList<>(edges.size());
        for (MazeEdge edge : edges) {
            boolean crossing = !(edge.a() instanceof MazeNode.Ruling && edge.b() instanceof MazeNode.Ruling);
            if (crossing || !this.isCornerLeaf(edge.a(), degree, ring) && !this.isCornerLeaf(edge.b(), degree, ring)) {
                kept.add(edge);
            }
        }
        return kept;
    }

    private boolean isCornerLeaf(MazeNode node, Map<MazeNode, Integer> degree, RingShape ring) {
        return node instanceof MazeNode.Ruling ruling && !ring.hasAxisEdge(ruling.node()) && degree.get(node) == 1;
    }

}
