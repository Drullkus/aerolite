package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import us.drullk.aerolite.world.shaft.surface.CrossingCandidate;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The maze of one shaft, cut into segments of one chunk length.
 * Tree segments sit on even chunks and connector segments on odd chunks.
 * A junction segment replaces the chunks around one or more junction boxes.
 * A column uses chunk sections as its chunks, bounded by the build limits.
 * The ring description of the shape gives the nodes of each ruling and the crossing candidates of each box.
 * A shape with several rings samples one tree per ring piece. A connector gets one virtual node per ring on each side.
 * A crossing onto a foreign patch joins one virtual node per box, which stands for the tree of the other pillar.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ShaftMaze {

    public static final int CHUNK = 16;
    public static final int RULINGS_PER_CHUNK = CHUNK / 2;

    private static final int PLAIN = 0;
    private static final int JUNCTION = 1;

    private final RingShape ring;
    private final MazeEdges mazeEdges;
    private final BoxLookup boxes;
    private final PositionalRandomFactory random;
    private final int minChunk;
    private final int maxChunk;
    private final float bias;

    /**
     * A shaft from the chunk {@code minChunk} to the chunk {@code maxChunk}, both included.
     *
     * @param bias the axis edge weight of the maze sampler
     */
    public ShaftMaze(RingShape ring, MazeEdges mazeEdges, BoxLookup boxes, PositionalRandomFactory random, int minChunk, int maxChunk, float bias) {
        this.ring = ring;
        this.mazeEdges = mazeEdges;
        this.boxes = boxes;
        this.random = random;
        this.minChunk = minChunk;
        this.maxChunk = maxChunk;
        this.bias = bias;
    }

    public int minChunk() {
        return this.minChunk;
    }

    public int maxChunk() {
        return this.maxChunk;
    }

    public float bias() {
        return this.bias;
    }

    public RingShape ring() {
        return this.ring;
    }

    /** How the patch of a box sits on the shaft. */
    public enum Patching {
        /** A horizontal shaft. Patch index i runs along the shaft. */
        SHAFT,
        /** A column at a junction with an X shaft. Patch index j runs along the column. */
        COLUMN_ALONG_X,
        /** A column at a junction with a Z shaft. Patch index j runs along the column. */
        COLUMN_ALONG_Z
    }

    /**
     * A junction box on the shaft.
     *
     * @param sign the face sign of the patch, along the second offset of the horizontal shaft at the box
     */
    public record Box(int center, int sign, Patching patching) {

        public Box(int center, int sign) {
            this(center, sign, Patching.SHAFT);
        }

        public boolean covers(int along) {
            return Math.abs(along - this.center) <= Ring.BOX;
        }
    }

    /** The offset of the section that is constant on the patch face of the box. */
    public static SectionAxis normal(Box box) {
        return box.patching() == Patching.COLUMN_ALONG_Z ? SectionAxis.FIRST : SectionAxis.SECOND;
    }

    /** The node of the first ring under the patch border node of the box at cross index {@code k}, on the outer face of the 13x13 square. */
    public int crossingNode(Box box, int k) {
        int index = normal(box) == SectionAxis.SECOND
                ? this.ring.index(0, 2 * k, Ring.OUTER * box.sign())
                : this.ring.index(0, Ring.OUTER * box.sign(), 2 * k);
        return this.ring.node(0, index);
    }

    /**
     * A possible crossing between a patch border node and the nearest ruling on one side of the box.
     *
     * @param along  the patch index along the shaft
     * @param across the patch index across the shaft
     * @param ruling the block of the ruling along the shaft
     * @param node   the ring node on the ruling
     */
    record Crossing(int along, int across, int ruling, int node) {
    }

    /** The possible crossings of a box onto its own patch, from the crossing candidates of the ring. */
    List<Crossing> crossings(Box box) {
        return this.crossings(box, false);
    }

    /** The possible crossings of a box onto the patch of the other pillar. */
    List<Crossing> foreignCrossings(Box box) {
        return this.crossings(box, true);
    }

    private List<Crossing> crossings(Box box, boolean foreign) {
        List<Crossing> crossings = new ArrayList<>();
        for (CrossingCandidate candidate : this.ring.crossings(normal(box), box.sign())) {
            if (candidate.foreign() == foreign) {
                crossings.add(new Crossing(-Ring.PATCH_INDEX, candidate.across(), this.lastRulingBefore(box), candidate.node()));
                crossings.add(new Crossing(Ring.PATCH_INDEX, candidate.across(), this.firstRulingAfter(box), candidate.node()));
            }
        }
        return crossings;
    }

    /** The graph node of the patch node at an index along the shaft and an index across it. */
    private int patchNode(RulingGraph graph, Box box, int firstPatchNode, int along, int across) {
        return box.patching() == Patching.SHAFT
                ? graph.patchNode(firstPatchNode, along, across)
                : graph.patchNode(firstPatchNode, across, along);
    }

    private int lastRulingBefore(Box box) {
        int a = box.center() - Ring.BOX - 1;
        return (a & 1) != 0 ? a : a - 1;
    }

    private int firstRulingAfter(Box box) {
        int a = box.center() + Ring.BOX + 1;
        return (a & 1) != 0 ? a : a + 1;
    }

    private int firstChunk(Box box) {
        return Math.floorDiv(this.lastRulingBefore(box), CHUNK);
    }

    private int lastChunk(Box box) {
        return Math.floorDiv(this.firstRulingAfter(box), CHUNK);
    }

    /** Finds the boxes of the shaft whose centers lie in a block range. */
    @FunctionalInterface
    public interface BoxLookup {
        List<Box> boxes(int minCenter, int maxCenter);
    }

    /**
     * One segment of the shaft maze.
     *
     * @param rulings the rulings of the segment, in ascending sequence
     * @param edges   the tree edges, with crossings into the neighbor segments
     */
    public record Segment(int firstChunk, int lastChunk, List<Box> boxes, int[] rulings, List<MazeEdge> edges) {
    }

    /** The boxes that cover at least one block of a range along the shaft axis. */
    public List<Box> boxesNear(int min, int max) {
        return this.boxes.boxes(min - Ring.BOX, max + Ring.BOX);
    }

    /** The segment that holds the chunk at index {@code chunk} along the shaft axis. */
    public Segment segmentAt(int chunk) {
        List<Box> members = this.boxesIn(chunk, chunk, true);
        if (members.isEmpty()) {
            return this.plain(chunk);
        }
        while (true) {
            int lo = members.stream().mapToInt(this::firstChunk).min().orElseThrow();
            int hi = members.stream().mapToInt(this::lastChunk).max().orElseThrow();
            List<Box> found = this.boxesIn(lo, hi, false);
            if (found.size() == members.size()) {
                return this.junction(lo, hi, found);
            }
            members = found;
        }
    }

    private List<Box> boxesIn(int lo, int hi, boolean containing) {
        List<Box> found = new ArrayList<>();
        for (Box box : this.boxes.boxes(CHUNK * lo - CHUNK, CHUNK * hi + 2 * CHUNK)) {
            boolean hit = containing
                    ? this.firstChunk(box) <= lo && lo <= this.lastChunk(box)
                    : this.lastChunk(box) >= lo && this.firstChunk(box) <= hi;
            if (hit) {
                found.add(box);
            }
        }
        found.sort(Comparator.comparingInt(Box::center));
        return found;
    }

    private boolean inRange(int chunk) {
        return chunk >= this.minChunk && chunk <= this.maxChunk;
    }

    private Segment plain(int chunk) {
        int base = CHUNK * chunk;
        int[] rulings = new int[RULINGS_PER_CHUNK];
        for (int u = 0; u < RULINGS_PER_CHUNK; u++) {
            rulings[u] = base + 2 * u + 1;
        }
        RulingGraph graph = new RulingGraph(this.ring, rulings);
        int[] left = (chunk & 1) != 0 && this.inRange(chunk - 1) ? graph.addVirtuals(rulings[0]) : new int[0];
        if ((chunk & 1) != 0 && this.inRange(chunk + 1)) {
            graph.addVirtuals(rulings[rulings.length - 1]);
        }
        int[] parent = graph.graph.sampleForest(this.random.at(PLAIN, chunk, 0), this.bias);
        List<MazeEdge> edges = this.mazeEdges.treeEdges(parent, graph.names(), (virtual, partner) -> this.crossing(partner, contains(left, virtual) ? -2 : 2));
        return new Segment(chunk, chunk, List.of(), rulings, this.mazeEdges.pruneCornerLeaves(edges, this.ring));
    }

    private static boolean contains(int[] nodes, int node) {
        return Arrays.stream(nodes).anyMatch(n -> n == node);
    }

    private Segment junction(int clusterLo, int clusterHi, List<Box> members) {
        int lo = Math.max(clusterLo, this.minChunk);
        int hi = Math.min(clusterHi, this.maxChunk);
        int[] rulings = IntStream.iterate(CHUNK * lo + 1, along -> along < CHUNK * (hi + 1), along -> along + 2)
                .filter(along -> members.stream().noneMatch(box -> box.covers(along)))
                .toArray();
        RulingGraph graph = new RulingGraph(this.ring, rulings);
        Int2ObjectMap<Map<MazeNode, MazeNode>> foreignTargets = new Int2ObjectOpenHashMap<>();
        for (Box box : members) {
            int patch = graph.addPatch(box.center(), box.patching() == Patching.SHAFT);
            for (Crossing crossing : this.crossings(box)) {
                if (graph.has(crossing.ruling())) {
                    graph.graph.addEdge(this.patchNode(graph, box, patch, crossing.along(), crossing.across()), graph.node(crossing.ruling(), crossing.node()), true);
                }
            }
            this.addForeign(graph, box, foreignTargets);
        }
        int[] left = (lo & 1) != 0 && this.inRange(lo - 1) ? graph.addVirtuals(rulings[0]) : new int[0];
        if ((hi & 1) != 0 && this.inRange(hi + 1)) {
            graph.addVirtuals(rulings[rulings.length - 1]);
        }
        int[] parent = graph.graph.sampleForest(this.random.at(JUNCTION, lo, 0), this.bias);
        List<MazeEdge> edges = this.mazeEdges.treeEdges(parent, graph.names(), (virtual, partner) -> foreignTargets.containsKey(virtual)
                ? foreignTargets.get(virtual).get(partner)
                : this.crossing(partner, contains(left, virtual) ? -2 : 2));
        return new Segment(lo, hi, List.copyOf(members), rulings, this.mazeEdges.pruneCornerLeaves(edges, this.ring));
    }

    /**
     * Joins the foreign crossings of a box to one virtual node, which stands for the tree of the other pillar.
     * So each tree edge at that node is one crossing onto the foreign patch, and no such edge closes a loop.
     */
    private void addForeign(RulingGraph graph, Box box, Int2ObjectMap<Map<MazeNode, MazeNode>> foreignTargets) {
        List<Crossing> foreign = this.foreignCrossings(box);
        if (foreign.isEmpty()) {
            return;
        }
        int virtual = graph.addVirtual();
        Map<MazeNode, MazeNode> targets = new HashMap<>();
        for (Crossing crossing : foreign) {
            if (graph.has(crossing.ruling())) {
                graph.graph.addEdge(virtual, graph.node(crossing.ruling(), crossing.node()), true);
                MazeNode.Foreign target = box.patching() == Patching.SHAFT
                        ? new MazeNode.Foreign(box.center(), crossing.along(), crossing.across())
                        : new MazeNode.Foreign(box.center(), crossing.across(), crossing.along());
                targets.put(new MazeNode.Ruling(crossing.ruling(), crossing.node()), target);
            }
        }
        foreignTargets.put(virtual, targets);
    }

    /** The node on the facing ruling of the neighbor segment. */
    private MazeNode crossing(MazeNode partner, int step) {
        MazeNode.Ruling ruling = (MazeNode.Ruling) partner;
        return new MazeNode.Ruling(ruling.along() + step, ruling.node());
    }

}
