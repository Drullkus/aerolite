package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An undirected graph of axis edges and ring edges, with the weighted Wilson sampler of a spanning forest.
 * The weight of an edge is its class factor times its pattern factor. The class factor is the bias for an axis edge
 * and 1 minus the bias for a ring edge. The pattern factor is 1 until a pattern sets it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class MazeGraph {

    private final List<IntList> axisNeighbors = new ArrayList<>();
    private final List<IntList> ringNeighbors = new ArrayList<>();
    private final List<IntList> neighbors = new ArrayList<>();
    private final IntList edgeA = new IntArrayList();
    private final IntList edgeB = new IntArrayList();
    private final BooleanArrayList edgeAxis = new BooleanArrayList();
    // Null while every pattern factor is 1. The unweighted step keeps the draws of the uniform maze.
    @Nullable
    private float[][] axisFactors;
    @Nullable
    private float[][] ringFactors;

    public int addNode() {
        this.axisNeighbors.add(new IntArrayList(2));
        this.ringNeighbors.add(new IntArrayList(2));
        this.neighbors.add(new IntArrayList(4));
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
        this.neighbors.get(a).add(b);
        this.neighbors.get(b).add(a);
        this.edgeA.add(a);
        this.edgeB.add(b);
        this.edgeAxis.add(axis);
    }

    public int size() {
        return this.axisNeighbors.size();
    }

    /** The number of edges. */
    public int edgeCount() {
        return this.edgeA.size();
    }

    /** The first node of the edge, in the sequence of {@link #addEdge}. */
    public int edgeA(int edge) {
        return this.edgeA.getInt(edge);
    }

    /** The second node of the edge, in the sequence of {@link #addEdge}. */
    public int edgeB(int edge) {
        return this.edgeB.getInt(edge);
    }

    /** True when the edge runs along the shaft axis. */
    public boolean edgeAxis(int edge) {
        return this.edgeAxis.getBoolean(edge);
    }

    /** Every neighbor of the node, in the sequence of {@link #addEdge}. */
    public IntList neighbors(int node) {
        return this.neighbors.get(node);
    }

    /** True when an edge joins the two nodes. */
    public boolean hasEdge(int a, int b) {
        return this.neighbors.get(a).contains(b);
    }

    /** True when an axis edge joins the two nodes. */
    public boolean hasAxisEdge(int a, int b) {
        return this.axisNeighbors.get(a).contains(b);
    }

    /** The key of the undirected edge between two nodes. */
    public static long key(int a, int b) {
        return (long) Math.min(a, b) << 32 | Math.max(a, b);
    }

    /**
     * Sets the pattern factor of every edge: 1 for an edge in {@code pattern}, {@code other} for any other edge.
     * An {@code other} of 1 restores the unweighted sampler.
     *
     * @param pattern the keys of the pattern edges, from {@link #key}
     * @param other   the pattern factor of the other edges, strictly between 0 and 1, or 1
     */
    public void setPatternFactors(LongSet pattern, float other) {
        if (other == 1) {
            this.axisFactors = null;
            this.ringFactors = null;
            return;
        }
        if (!(other > 0 && other < 1)) {
            throw new IllegalArgumentException("The pattern factor is not strictly between 0 and 1: " + other);
        }
        this.axisFactors = this.factors(this.axisNeighbors, pattern, other);
        this.ringFactors = this.factors(this.ringNeighbors, pattern, other);
    }

    private float[][] factors(List<IntList> side, LongSet pattern, float other) {
        float[][] factors = new float[this.size()][];
        for (int node = 0; node < factors.length; node++) {
            IntList list = side.get(node);
            factors[node] = new float[list.size()];
            for (int k = 0; k < list.size(); k++) {
                factors[node][k] = pattern.contains(key(node, list.getInt(k))) ? 1 : other;
            }
        }
        return factors;
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
    List<IntList> components() {
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
        if (this.axisFactors != null && this.ringFactors != null) {
            return this.weightedStep(random, axis, ring, this.axisFactors[node], this.ringFactors[node], bias);
        }
        float axisWeight = axis.size() * bias;
        float total = axisWeight + ring.size() * (1 - bias);
        if (random.nextFloat() * total < axisWeight) {
            return axis.getInt(random.nextInt(axis.size()));
        }
        return ring.getInt(random.nextInt(ring.size()));
    }

    /** Picks one edge in proportion to its class factor times its pattern factor. */
    private int weightedStep(RandomSource random, IntList axis, IntList ring, float[] axisFactors, float[] ringFactors, float bias) {
        float total = 0;
        for (float factor : axisFactors) {
            total += bias * factor;
        }
        for (float factor : ringFactors) {
            total += (1 - bias) * factor;
        }
        float x = random.nextFloat() * total;
        for (int k = 0; k < axis.size(); k++) {
            x -= bias * axisFactors[k];
            if (x < 0) {
                return axis.getInt(k);
            }
        }
        for (int k = 0; k < ring.size(); k++) {
            x -= (1 - bias) * ringFactors[k];
            if (x < 0) {
                return ring.getInt(k);
            }
        }
        // Rounding can leave a small positive rest. The last edge then takes it.
        return ring.isEmpty() ? axis.getInt(axis.size() - 1) : ring.getInt(ring.size() - 1);
    }

}
