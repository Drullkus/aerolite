package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.shaft.surface.RingShape;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * The pattern edges of the maze types on the graph of one segment.
 *
 * <ul>
 * <li>Serpentine: each ruling is one ring path with one gap. One axis edge next to the gap joins the ruling to the next ruling.</li>
 * <li>Ribs: the breadth-first tree of the segment graph from a root that the seam picks.</li>
 * <li>Lines: the axis edges of straight grooves on every node column, or on every second node column.</li>
 * <li>Gilbert: the generalized Hilbert curve over each block of rulings that axis edges join.</li>
 * </ul>
 *
 * <p>Every draw comes from the pattern random of the segment. The draw sequence of serpentine is the rulings,
 * the left virtual nodes, the patch rows, then the crossing groups.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class MazeTypes {

    private final RingShape ring;
    private final MazeSettings settings;

    MazeTypes(RingShape ring, MazeSettings settings) {
        this.ring = ring;
        this.settings = settings;
    }

    MazeSettings settings() {
        return this.settings;
    }

    /** True when the type changes the maze. A uniform maze or a strength of 0 samples the uniform maze. */
    boolean active() {
        MazeSettings.Type type = this.settings.type();
        return type == MazeSettings.Type.LINES || type != MazeSettings.Type.UNIFORM && this.settings.strength() > 0;
    }

    /** True when the weighted sampler runs. At full strength and for lines, the pattern edges are the segment edges. */
    boolean sampled() {
        return this.active() && this.settings.type() != MazeSettings.Type.LINES && this.settings.strength() < 1;
    }

    /** True when a connector replays the turns of its neighbor before it. */
    boolean turnsAtNeighbor() {
        return this.active() && this.settings.type() == MazeSettings.Type.SERPENTINE;
    }

    /** True when the ribs root sits at one ring position per pillar. */
    boolean pillarSeam() {
        return this.settings.type() == MazeSettings.Type.RIBS && this.settings.seam() == MazeSettings.Seam.PILLAR;
    }

    /**
     * The serpentine turns of a run of rulings. Each ruling draws the gap of each ring, then the bridge end when both gap ends have axis edges.
     *
     * @param gaps    the ring position of the gap of each ruling and ring; the gap is the ring edge from this position to the next
     * @param bridges the ring position of the bridge of each ruling and ring, or -1 when no gap end has an axis edge
     */
    record Turns(int[][] gaps, int[][] bridges) {
    }

    Turns turns(RandomSource random, int rulings) {
        int rings = this.ring.rings();
        int[][] gaps = new int[rulings][rings];
        int[][] bridges = new int[rulings][rings];
        for (int k = 0; k < rulings; k++) {
            for (int r = 0; r < rings; r++) {
                int nodes = this.ring.length(r) / 2;
                int first = this.ring.firstNode(r);
                int gap = random.nextInt(nodes);
                int next = (gap + 1) % nodes;
                boolean gapEnd = this.ring.hasAxisEdge(first + gap);
                boolean nextEnd = this.ring.hasAxisEdge(first + next);
                gaps[k][r] = gap;
                if (gapEnd && nextEnd) {
                    bridges[k][r] = random.nextInt(2) == 0 ? gap : next;
                } else {
                    bridges[k][r] = gapEnd ? gap : nextEnd ? next : -1;
                }
            }
        }
        return new Turns(gaps, bridges);
    }

    /** The ring position of each ring that a pillar seam roots at. */
    int[] pillarPositions(RandomSource random) {
        int[] positions = new int[this.ring.rings()];
        for (int r = 0; r < positions.length; r++) {
            positions[r] = random.nextInt(this.ring.length(r) / 2);
        }
        return positions;
    }

    /**
     * The pattern edges of the segment, as edge keys of its graph.
     *
     * @param leftBridges     the bridge of the facing ruling of the neighbor before the segment, per ring, or null
     * @param pillarPositions the ring positions of the pillar seam, per ring
     */
    LongLinkedOpenHashSet patternEdges(RulingGraph graph, RandomSource random, @Nullable int[] leftBridges, int[] pillarPositions) {
        LongLinkedOpenHashSet edges = new LongLinkedOpenHashSet();
        switch (this.settings.type()) {
            case SERPENTINE -> this.serpentine(graph, random, leftBridges, edges);
            case RIBS -> this.ribs(graph, random, pillarPositions, edges);
            case LINES -> this.lines(graph, edges);
            case GILBERT -> this.gilbert(graph, edges);
            case UNIFORM -> {
            }
        }
        return edges;
    }

    /** The segment edges at full strength. Gilbert joins its pieces into one tree. Every other type keeps its pattern edges. */
    LongList fullEdges(RulingGraph graph, RandomSource random, LongLinkedOpenHashSet pattern) {
        if (this.settings.type() == MazeSettings.Type.GILBERT) {
            return this.joinPieces(graph, random, pattern);
        }
        return new LongArrayList(pattern);
    }

    private void serpentine(RulingGraph graph, RandomSource random, @Nullable int[] leftBridges, LongSet edges) {
        int[] rulings = graph.rulings;
        Turns turns = this.turns(random, rulings.length);
        for (int k = 0; k < rulings.length; k++) {
            for (int r = 0; r < this.ring.rings(); r++) {
                int nodes = this.ring.length(r) / 2;
                int first = this.ring.firstNode(r);
                for (int p = 0; p < nodes; p++) {
                    if (p != turns.gaps()[k][r]) {
                        edges.add(MazeGraph.key(graph.node(rulings[k], first + p), graph.node(rulings[k], first + (p + 1) % nodes)));
                    }
                }
                int bridge = turns.bridges()[k][r];
                if (bridge < 0) {
                    continue;
                }
                int from = graph.node(rulings[k], first + bridge);
                int to = k + 1 < rulings.length && graph.has(rulings[k + 1]) ? graph.node(rulings[k + 1], first + bridge)
                        : k + 1 == rulings.length && graph.right.length > 0 ? graph.right[r] : -1;
                if (to >= 0 && graph.graph.hasAxisEdge(from, to)) {
                    edges.add(MazeGraph.key(from, to));
                }
            }
        }
        for (int r = 0; r < graph.left.length; r++) {
            int bridge = leftBridges == null ? -1 : leftBridges[r];
            int target = bridge < 0 ? -1 : graph.node(rulings[0], this.ring.firstNode(r) + bridge);
            if (target < 0 || !graph.graph.hasAxisEdge(graph.left[r], target)) {
                IntList neighbors = graph.graph.neighbors(graph.left[r]);
                target = neighbors.getInt(random.nextInt(neighbors.size()));
            }
            edges.add(MazeGraph.key(graph.left[r], target));
        }
        int m = Ring.PATCH_INDEX;
        for (RulingGraph.PatchNodes patch : graph.patches) {
            for (int along = -m; along <= m; along++) {
                for (int across = -m; across < m; across++) {
                    edges.add(MazeGraph.key(this.patchNode(graph, patch, along, across), this.patchNode(graph, patch, along, across + 1)));
                }
            }
            for (int along = -m; along < m; along++) {
                int end = random.nextInt(2) == 0 ? -m : m;
                edges.add(MazeGraph.key(this.patchNode(graph, patch, along, end), this.patchNode(graph, patch, along + 1, end)));
            }
        }
        this.pickOnePerGroup(graph, random, edges);
    }

    private void pickOnePerGroup(RulingGraph graph, RandomSource random, LongSet edges) {
        for (LongList group : graph.groups) {
            if (!group.isEmpty()) {
                edges.add(group.getLong(random.nextInt(group.size())));
            }
        }
    }

    private int patchNode(RulingGraph graph, RulingGraph.PatchNodes patch, int along, int across) {
        return patch.box().patching() == ShaftMaze.Patching.SHAFT
                ? graph.patchNode(patch.first(), along, across)
                : graph.patchNode(patch.first(), across, along);
    }

    private void ribs(RulingGraph graph, RandomSource random, int[] pillarPositions, LongSet edges) {
        IntList roots = new IntArrayList();
        MazeSettings.Seam seam = this.settings.seam();
        if (seam == MazeSettings.Seam.RANDOM || graph.rulings.length == 0) {
            for (IntList component : graph.graph.components()) {
                roots.add(component.getInt(random.nextInt(component.size())));
            }
        } else {
            for (int r = 0; r < this.ring.rings(); r++) {
                int position = seam == MazeSettings.Seam.SEEDED ? random.nextInt(this.ring.length(r) / 2) : pillarPositions[r];
                roots.add(graph.node(graph.rulings[0], this.ring.firstNode(r) + position));
            }
        }
        MazeGraph g = graph.graph;
        boolean[] seen = new boolean[g.size()];
        IntList queue = new IntArrayList();
        for (int node = 0; node < g.size() + roots.size(); node++) {
            int root = node < roots.size() ? roots.getInt(node) : node - roots.size();
            if (seen[root]) {
                continue;
            }
            seen[root] = true;
            queue.clear();
            queue.add(root);
            for (int k = 0; k < queue.size(); k++) {
                int at = queue.getInt(k);
                for (int next : g.neighbors(at)) {
                    if (!seen[next]) {
                        seen[next] = true;
                        queue.add(next);
                        edges.add(MazeGraph.key(at, next));
                    }
                }
            }
        }
    }

    /** True for every second node column with an axis edge, in ring sequence, or for every such column at spacing 1. */
    private boolean[] grooves() {
        boolean[] grooves = new boolean[this.ring.nodeCount()];
        int spacing = this.settings.spacing();
        for (int r = 0; r < this.ring.rings(); r++) {
            int first = this.ring.firstNode(r);
            int column = 0;
            for (int p = 0; p < this.ring.length(r) / 2; p++) {
                if (this.ring.hasAxisEdge(first + p)) {
                    grooves[first + p] = column % spacing == 0;
                    column++;
                }
            }
        }
        return grooves;
    }

    /**
     * The axis edges of the grooves. A patch line across the axis is a groove when a crossing of it lands on a groove node.
     * A crossing onto a groove node is a pattern edge.
     */
    private void lines(RulingGraph graph, LongSet edges) {
        MazeGraph g = graph.graph;
        boolean[] grooves = this.grooves();
        LongSet lines = new LongOpenHashSet();
        for (int e = 0; e < g.edgeCount(); e++) {
            if (g.edgeAxis(e) && this.crossingPatch(graph, e) instanceof MazeNode.Patch patch && grooves[this.rulingEnd(graph, e).node()]) {
                lines.add(this.line(graph, patch));
            }
        }
        for (int e = 0; e < g.edgeCount(); e++) {
            if (!g.edgeAxis(e)) {
                continue;
            }
            MazeNode a = graph.name(g.edgeA(e));
            MazeNode b = graph.name(g.edgeB(e));
            boolean keep;
            if (a instanceof MazeNode.Patch patch && b instanceof MazeNode.Patch) {
                keep = lines.contains(this.line(graph, patch));
            } else {
                MazeNode.Ruling ruling = this.rulingEnd(graph, e);
                keep = ruling != null && grooves[ruling.node()];
            }
            if (keep) {
                edges.add(MazeGraph.key(g.edgeA(e), g.edgeB(e)));
            }
        }
    }

    /** The patch end of a crossing edge between a patch node and a ruling node, or null. */
    @Nullable
    private MazeNode.Patch crossingPatch(RulingGraph graph, int edge) {
        MazeNode a = graph.name(graph.graph.edgeA(edge));
        MazeNode b = graph.name(graph.graph.edgeB(edge));
        if (a instanceof MazeNode.Patch patch && b instanceof MazeNode.Ruling) {
            return patch;
        }
        return b instanceof MazeNode.Patch patch && a instanceof MazeNode.Ruling ? patch : null;
    }

    @Nullable
    private MazeNode.Ruling rulingEnd(RulingGraph graph, int edge) {
        MazeNode a = graph.name(graph.graph.edgeA(edge));
        MazeNode b = graph.name(graph.graph.edgeB(edge));
        return a instanceof MazeNode.Ruling ruling ? ruling : b instanceof MazeNode.Ruling ruling ? ruling : null;
    }

    /** The key of the patch line across the axis that holds the patch node. */
    private long line(RulingGraph graph, MazeNode.Patch patch) {
        boolean shaft = graph.patches.stream().filter(p -> p.box().center() == patch.owner()).findFirst()
                .map(p -> p.box().patching() == ShaftMaze.Patching.SHAFT).orElse(true);
        return (long) patch.owner() << 32 | (shaft ? patch.j() : patch.i()) & 0xFFFFFFFFL;
    }

    /** The curve edges that the graph holds. The curve runs over each block of rulings that axis edges join, with its seam at ring position 0. */
    private void gilbert(RulingGraph graph, LongSet edges) {
        int[] rulings = graph.rulings;
        for (int r = 0; r < this.ring.rings(); r++) {
            int nodes = this.ring.length(r) / 2;
            int first = this.ring.firstNode(r);
            int start = 0;
            for (int k = 1; k <= rulings.length; k++) {
                if (k < rulings.length && rulings[k] == rulings[k - 1] + 2) {
                    continue;
                }
                int[] run = Arrays.copyOfRange(rulings, start, k);
                start = k;
                IntList cells = this.gilbertCells(nodes, run.length);
                for (int c = 2; c < cells.size(); c += 2) {
                    int a = graph.node(run[cells.getInt(c - 1)], first + cells.getInt(c - 2));
                    int b = graph.node(run[cells.getInt(c + 1)], first + cells.getInt(c));
                    if (graph.graph.hasEdge(a, b)) {
                        edges.add(MazeGraph.key(a, b));
                    }
                }
            }
        }
    }

    /** The cells of the generalized Hilbert curve over {@code width} ring positions by {@code height} rulings, as flat (position, ruling) pairs. */
    IntList gilbertCells(int width, int height) {
        IntList out = new IntArrayList(2 * width * height);
        if (width >= height) {
            this.gilbert(out, 0, 0, width, 0, 0, height);
        } else {
            this.gilbert(out, 0, 0, 0, height, width, 0);
        }
        return out;
    }

    /** The "gilbert" recursion of Jakub Červený. (ax, ay) is the major axis, (bx, by) the minor axis. */
    private void gilbert(IntList out, int x, int y, int ax, int ay, int bx, int by) {
        int w = Math.abs(ax + ay);
        int h = Math.abs(bx + by);
        int dax = Integer.signum(ax);
        int day = Integer.signum(ay);
        int dbx = Integer.signum(bx);
        int dby = Integer.signum(by);
        if (h == 1) {
            for (int k = 0; k < w; k++) {
                out.add(x);
                out.add(y);
                x += dax;
                y += day;
            }
            return;
        }
        if (w == 1) {
            for (int k = 0; k < h; k++) {
                out.add(x);
                out.add(y);
                x += dbx;
                y += dby;
            }
            return;
        }
        // Integer division rounds toward zero, like the half of the prototype.
        int ax2 = ax / 2;
        int ay2 = ay / 2;
        int bx2 = bx / 2;
        int by2 = by / 2;
        int w2 = Math.abs(ax2 + ay2);
        int h2 = Math.abs(bx2 + by2);
        if (2 * w > 3 * h) {
            if (w2 % 2 != 0 && w > 2) {
                ax2 += dax;
                ay2 += day;
            }
            this.gilbert(out, x, y, ax2, ay2, bx, by);
            this.gilbert(out, x + ax2, y + ay2, ax - ax2, ay - ay2, bx, by);
        } else {
            if (h2 % 2 != 0 && h > 2) {
                bx2 += dbx;
                by2 += dby;
            }
            this.gilbert(out, x, y, bx2, by2, ax2, ay2);
            this.gilbert(out, x + bx2, y + by2, ax, ay, bx - bx2, by - by2);
            this.gilbert(out, x + (ax - dax) + (bx2 - dbx), y + (ay - day) + (by2 - dby), -bx2, -by2, -(ax - ax2), -(ay - ay2));
        }
    }

    /**
     * Joins the curve pieces into one tree with the fewest extra edges.
     * A union-find pass runs over the graph edges that touch no virtual node and are no crossing, in a shuffled sequence.
     * Then each virtual node joins by one edge, and each crossing group by one crossing.
     */
    private LongList joinPieces(RulingGraph graph, RandomSource random, LongLinkedOpenHashSet curve) {
        MazeGraph g = graph.graph;
        int[] parent = new int[g.size()];
        for (int node = 0; node < parent.length; node++) {
            parent[node] = node;
        }
        LongList out = new LongArrayList(curve);
        for (long key : curve) {
            this.union(parent, (int) (key >>> 32), (int) key);
        }
        LongList candidates = new LongArrayList();
        for (int e = 0; e < g.edgeCount(); e++) {
            int a = g.edgeA(e);
            int b = g.edgeB(e);
            if (graph.name(a) != null && graph.name(b) != null && this.crossingPatch(graph, e) == null) {
                candidates.add(MazeGraph.key(a, b));
            }
        }
        for (int k = candidates.size() - 1; k > 0; k--) {
            int pick = random.nextInt(k + 1);
            long swap = candidates.getLong(k);
            candidates.set(k, candidates.getLong(pick));
            candidates.set(pick, swap);
        }
        for (long key : candidates) {
            if (this.union(parent, (int) (key >>> 32), (int) key)) {
                out.add(key);
            }
        }
        for (int[] side : new int[][]{graph.left, graph.right}) {
            for (int virtual : side) {
                IntList neighbors = g.neighbors(virtual);
                out.add(MazeGraph.key(virtual, neighbors.getInt(random.nextInt(neighbors.size()))));
            }
        }
        LongSet picked = new LongLinkedOpenHashSet();
        this.pickOnePerGroup(graph, random, picked);
        out.addAll(picked);
        return out;
    }

    private int find(int[] parent, int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]];
            node = parent[node];
        }
        return node;
    }

    private boolean union(int[] parent, int a, int b) {
        int ra = this.find(parent, a);
        int rb = this.find(parent, b);
        if (ra == rb) {
            return false;
        }
        parent[ra] = rb;
        return true;
    }

}
