package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An undirected graph of axis edges and ring edges, with the weighted Wilson sampler of a spanning forest.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class MazeGraph {

    private final List<IntList> axisNeighbors = new ArrayList<>();
    private final List<IntList> ringNeighbors = new ArrayList<>();

    public int addNode() {
        this.axisNeighbors.add(new IntArrayList(2));
        this.ringNeighbors.add(new IntArrayList(2));
        return this.axisNeighbors.size() - 1;
    }

    /**
     * Joins two nodes.
     *
     * @param axis true for an edge along the shaft axis, false for an edge around the shaft
     */
    public void addEdge(int a, int b, boolean axis) {
        List<IntList> side = axis ? this.axisNeighbors : this.ringNeighbors;
        side.get(a).add(b);
        side.get(b).add(a);
    }

    public int size() {
        return this.axisNeighbors.size();
    }

    /**
     * Samples a spanning forest with Wilson's algorithm, one tree per connected component.
     * Each component draws its root first, in the sequence of its lowest node.
     * The loop-erased random walk takes an axis edge with weight {@code bias} and a ring edge with weight {@code 1 - bias}.
     * A connected graph draws exactly the values of a single spanning tree from one random root.
     *
     * @param bias the axis edge weight, strictly between 0 and 1
     * @return the parent of each node, -1 for each root
     */
    public int[] sampleForest(RandomSource random, float bias) {
        if (!(bias > 0 && bias < 1)) {
            throw new IllegalArgumentException("The maze bias is not strictly between 0 and 1: " + bias);
        }
        int[] parent = new int[this.size()];
        Arrays.fill(parent, -1);
        boolean[] inTree = new boolean[this.size()];
        for (IntList component : this.components()) {
            inTree[component.getInt(random.nextInt(component.size()))] = true;
        }
        for (int start = 0; start < this.size(); start++) {
            int node = start;
            while (!inTree[node]) {
                parent[node] = this.step(random, node, bias);
                node = parent[node];
            }
            // The last exit from each node overwrites its parent, so the walk loses its loops.
            node = start;
            while (!inTree[node]) {
                inTree[node] = true;
                node = parent[node];
            }
        }
        return parent;
    }

    /** The connected components, each sorted, in the sequence of their lowest node. */
    private List<IntList> components() {
        int[] component = new int[this.size()];
        Arrays.fill(component, -1);
        List<IntList> components = new ArrayList<>();
        IntArrayList stack = new IntArrayList();
        for (int root = 0; root < this.size(); root++) {
            if (component[root] >= 0) {
                continue;
            }
            IntList members = new IntArrayList();
            component[root] = components.size();
            stack.add(root);
            while (!stack.isEmpty()) {
                int node = stack.popInt();
                members.add(node);
                for (List<IntList> side : List.of(this.axisNeighbors, this.ringNeighbors)) {
                    for (int next : side.get(node)) {
                        if (component[next] < 0) {
                            component[next] = components.size();
                            stack.add(next);
                        }
                    }
                }
            }
            members.sort(null);
            components.add(members);
        }
        return components;
    }

    /** Picks one edge at the node in proportion to its weight. */
    private int step(RandomSource random, int node, float bias) {
        IntList axis = this.axisNeighbors.get(node);
        IntList ring = this.ringNeighbors.get(node);
        float axisWeight = axis.size() * bias;
        float total = axisWeight + ring.size() * (1 - bias);
        if (random.nextFloat() * total < axisWeight) {
            return axis.getInt(random.nextInt(axis.size()));
        }
        return ring.getInt(random.nextInt(ring.size()));
    }

}
