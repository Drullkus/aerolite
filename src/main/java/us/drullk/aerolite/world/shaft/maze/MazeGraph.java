package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An undirected graph whose adjacency lists keep the insertion sequence, with the Origin Shift sampler.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public final class MazeGraph {

    /** The Origin Shift steps per node. The stats mode of the maze prototype matches Wilson's algorithm at this count. */
    public static final int STEPS_PER_NODE = 16;

    private final List<IntList> adjacency = new ArrayList<>();

    public int addNode() {
        this.adjacency.add(new IntArrayList(4));
        return this.adjacency.size() - 1;
    }

    public void addEdge(int a, int b) {
        this.adjacency.get(a).add(b);
        this.adjacency.get(b).add(a);
    }

    public int size() {
        return this.adjacency.size();
    }

    /**
     * Samples a spanning tree with Origin Shift from the breadth-first tree of a random root.
     *
     * @return the parent of each node, -1 for the root
     */
    public int[] sampleTree(RandomSource random) {
        int root = random.nextInt(this.size());
        int[] parent = this.breadthFirstTree(root);
        long steps = (long) STEPS_PER_NODE * this.size();
        for (long step = 0; step < steps; step++) {
            IntList neighbors = this.adjacency.get(root);
            int next = neighbors.getInt(random.nextInt(neighbors.size()));
            parent[root] = next;
            root = next;
            parent[root] = -1;
        }
        return parent;
    }

    private int[] breadthFirstTree(int root) {
        int[] parent = new int[this.size()];
        Arrays.fill(parent, -2);
        parent[root] = -1;
        int[] queue = new int[this.size()];
        int head = 0;
        int tail = 0;
        queue[tail++] = root;
        while (head < tail) {
            int node = queue[head++];
            IntList neighbors = this.adjacency.get(node);
            for (int k = 0; k < neighbors.size(); k++) {
                int next = neighbors.getInt(k);
                if (parent[next] == -2) {
                    parent[next] = node;
                    queue[tail++] = next;
                }
            }
        }
        return parent;
    }

}
