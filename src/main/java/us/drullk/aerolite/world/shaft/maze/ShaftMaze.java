package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The maze of one horizontal shaft, cut into chunk segments.
 * Tree segments sit on even chunks and connector segments on odd chunks.
 * A junction segment replaces the chunks around one or more junction boxes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public final class ShaftMaze {

    public static final int CHUNK = 16;
    public static final int RULINGS_PER_CHUNK = CHUNK / 2;

    private static final int PLAIN = 0;
    private static final int JUNCTION = 1;

    private final BoxLookup boxes;
    private final PositionalRandomFactory random;

    public ShaftMaze(BoxLookup boxes, PositionalRandomFactory random) {
        this.boxes = boxes;
        this.random = random;
    }

    /** A junction box on the shaft. The shaft patch is on the face at {@code 7 * sign} along the second offset. */
    public record Box(int center, int sign) {

        public int lastRulingBefore() {
            return Ring.oddBelow(this.center - Ring.BOX - 1);
        }

        public int firstRulingAfter() {
            return Ring.oddAbove(this.center + Ring.BOX + 1);
        }

        public int firstChunk() {
            return Math.floorDiv(this.lastRulingBefore(), CHUNK);
        }

        public int lastChunk() {
            return Math.floorDiv(this.firstRulingAfter(), CHUNK);
        }

        public boolean covers(int along) {
            return Math.abs(along - this.center) <= Ring.BOX;
        }
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
            int lo = members.stream().mapToInt(Box::firstChunk).min().orElseThrow();
            int hi = members.stream().mapToInt(Box::lastChunk).max().orElseThrow();
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
                    ? box.firstChunk() <= lo && lo <= box.lastChunk()
                    : box.lastChunk() >= lo && box.firstChunk() <= hi;
            if (hit) {
                found.add(box);
            }
        }
        found.sort(Comparator.comparingInt(Box::center));
        return found;
    }

    private Segment plain(int chunk) {
        int base = CHUNK * chunk;
        int[] rulings = new int[RULINGS_PER_CHUNK];
        for (int u = 0; u < RULINGS_PER_CHUNK; u++) {
            rulings[u] = base + 2 * u + 1;
        }
        RulingGraph graph = new RulingGraph(rulings);
        int left = -1;
        int right = -1;
        if ((chunk & 1) != 0) {
            left = graph.addVirtual(rulings[0]);
            right = graph.addVirtual(rulings[rulings.length - 1]);
        }
        int[] parent = graph.graph.sampleTree(this.random.at(PLAIN, chunk, 0));
        int leftNode = left;
        List<MazeEdge> edges = MazeEdge.treeEdges(parent, graph.names(), (virtual, partner) -> crossing(partner, virtual == leftNode ? -2 : 2));
        return new Segment(chunk, chunk, List.of(), rulings, MazeEdge.pruneCornerLeaves(edges));
    }

    private Segment junction(int lo, int hi, List<Box> members) {
        int[] rulings = IntStream.iterate(CHUNK * lo + 1, along -> along < CHUNK * (hi + 1), along -> along + 2)
                .filter(along -> members.stream().noneMatch(box -> box.covers(along)))
                .toArray();
        RulingGraph graph = new RulingGraph(rulings);
        for (Box box : members) {
            int patch = graph.addPatch(box.center());
            for (int j = -2; j <= 2; j++) {
                int v = Ring.faceNode(box.sign(), j);
                graph.graph.addEdge(RulingGraph.patchNode(patch, -3, j), graph.node(box.lastRulingBefore(), v));
                graph.graph.addEdge(RulingGraph.patchNode(patch, 3, j), graph.node(box.firstRulingAfter(), v));
            }
        }
        int left = (lo & 1) != 0 ? graph.addVirtual(rulings[0]) : -1;
        if ((hi & 1) != 0) {
            graph.addVirtual(rulings[rulings.length - 1]);
        }
        int[] parent = graph.graph.sampleTree(this.random.at(JUNCTION, lo, 0));
        List<MazeEdge> edges = MazeEdge.treeEdges(parent, graph.names(), (virtual, partner) -> crossing(partner, virtual == left ? -2 : 2));
        return new Segment(lo, hi, List.copyOf(members), rulings, MazeEdge.pruneCornerLeaves(edges));
    }

    /** The node on the facing ruling of the neighbor segment. */
    private static MazeNode crossing(MazeNode partner, int step) {
        MazeNode.Ruling ruling = (MazeNode.Ruling) partner;
        return new MazeNode.Ruling(ruling.along() + step, ruling.node());
    }

}
