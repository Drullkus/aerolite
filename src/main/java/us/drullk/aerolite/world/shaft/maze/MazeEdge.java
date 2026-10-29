package us.drullk.aerolite.world.shaft.maze;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A tree edge of the maze, which carves a maze path.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public record MazeEdge(MazeNode a, MazeNode b) {

    /** Maps a virtual node to the real node that its edge reaches in the neighbor tree. */
    @FunctionalInterface
    interface VirtualTarget {
        MazeNode target(int virtualNode, MazeNode partner);
    }

    /** Collects the tree edges of a sampled graph. A node without a name is a virtual node. */
    static List<MazeEdge> treeEdges(int[] parent, @Nullable MazeNode[] names, VirtualTarget virtualTarget) {
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
    static List<MazeEdge> pruneCornerLeaves(List<MazeEdge> edges) {
        Map<MazeNode, Integer> degree = new HashMap<>();
        for (MazeEdge edge : edges) {
            degree.merge(edge.a, 1, Integer::sum);
            degree.merge(edge.b, 1, Integer::sum);
        }
        List<MazeEdge> kept = new ArrayList<>(edges.size());
        for (MazeEdge edge : edges) {
            if (!isCornerLeaf(edge.a, degree) && !isCornerLeaf(edge.b, degree)) {
                kept.add(edge);
            }
        }
        return kept;
    }

    private static boolean isCornerLeaf(MazeNode node, Map<MazeNode, Integer> degree) {
        return node instanceof MazeNode.Ruling ruling && ruling.isCorner() && degree.get(node) == 1;
    }

}
