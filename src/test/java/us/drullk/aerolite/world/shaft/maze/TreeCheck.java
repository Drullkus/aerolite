package us.drullk.aerolite.world.shaft.maze;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Confirms that a set of maze edges forms one tree.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class TreeCheck {

    private final Map<MazeNode, MazeNode> parent = new HashMap<>();

    private TreeCheck() {
    }

    private MazeNode find(MazeNode node) {
        MazeNode root = node;
        while (true) {
            MazeNode next = this.parent.getOrDefault(root, root);
            if (next.equals(root)) {
                break;
            }
            root = next;
        }
        this.parent.put(node, root);
        return root;
    }

    /** Asserts no loop and one component. Returns the node count. */
    static int assertOneTree(Collection<MazeEdge> edges) {
        TreeCheck check = new TreeCheck();
        Set<MazeNode> nodes = new HashSet<>();
        for (MazeEdge edge : edges) {
            nodes.add(edge.a());
            nodes.add(edge.b());
            MazeNode a = check.find(edge.a());
            MazeNode b = check.find(edge.b());
            assertNotEquals(a, b, () -> "The edge " + edge + " closes a loop");
            check.parent.put(a, b);
        }
        Set<MazeNode> roots = new HashSet<>();
        for (MazeNode node : nodes) {
            roots.add(check.find(node));
        }
        assertEquals(1, roots.size(), "The edges form more than one component");
        assertEquals(nodes.size() - 1, edges.size());
        return nodes.size();
    }

    /** Asserts no loop. Returns the number of components. */
    static int assertForest(Collection<MazeEdge> edges) {
        TreeCheck check = new TreeCheck();
        Set<MazeNode> nodes = new HashSet<>();
        for (MazeEdge edge : edges) {
            nodes.add(edge.a());
            nodes.add(edge.b());
            MazeNode a = check.find(edge.a());
            MazeNode b = check.find(edge.b());
            assertNotEquals(a, b, () -> "The edge " + edge + " closes a loop");
            check.parent.put(a, b);
        }
        Set<MazeNode> roots = new HashSet<>();
        for (MazeNode node : nodes) {
            roots.add(check.find(node));
        }
        return roots.size();
    }

}
