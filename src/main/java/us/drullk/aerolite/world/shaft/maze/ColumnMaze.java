package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The maze of one column. Its junctions cut it into pieces, and each piece is one tree.
 * Each column patch is one tree with one virtual node per neighbor piece.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 *
 * @param pieces  the pieces from the bottom build limit to the top build limit
 * @param patches the column patch trees, one per junction from the bottom up
 */
public record ColumnMaze(List<Piece> pieces, List<PatchTree> patches) {

    private static final int PIECE = 0;
    private static final int PATCH = 1;

    /**
     * A junction on the column.
     *
     * @param center the Y of the junction center
     * @param sign   the sign of the column patch face, along the second offset of the horizontal shaft
     * @param alongX true when the horizontal shaft runs along X
     */
    public record Junction(int center, int sign, boolean alongX) {

        /** The column ring node under the patch node at {@code 2i} along the horizontal shaft. */
        public int columnNode(int i) {
            int block = this.alongX
                    ? Ring.block(2 * i, Ring.OUTER * this.sign)
                    : Ring.block(Ring.OUTER * this.sign, 2 * i);
            return block / 2;
        }
    }

    /** The rulings from {@code first} to {@code last}, every two blocks. The piece is empty when first exceeds last. */
    public record Piece(int first, int last, List<MazeEdge> edges) {

        public boolean isEmpty() {
            return this.first > this.last;
        }
    }

    public record PatchTree(Junction junction, List<MazeEdge> edges) {
    }

    /** The first ruling above a junction, or above the bottom build limit for the first piece. */
    static int pieceFirst(int minY, List<Junction> junctions, int k) {
        return k == 0 ? Ring.oddAbove(minY) : Ring.oddAbove(junctions.get(k - 1).center() + Ring.BOX + 1);
    }

    /** The last ruling below a junction, or below the top build limit for the last piece. */
    static int pieceLast(int maxY, List<Junction> junctions, int k) {
        return k == junctions.size() ? Ring.oddBelow(maxY - 1) : Ring.oddBelow(junctions.get(k).center() - Ring.BOX - 1);
    }

    /** True when every piece between two junctions holds at least one ruling. */
    public static boolean fits(List<Junction> junctions) {
        for (int k = 1; k < junctions.size(); k++) {
            if (pieceFirst(0, junctions, k) > pieceLast(0, junctions, k)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Samples the maze of a column.
     *
     * @param maxY      the top build limit, exclusive
     * @param junctions the junctions in ascending sequence of Y
     */
    public static ColumnMaze build(int minY, int maxY, List<Junction> junctions, PositionalRandomFactory random) {
        List<Piece> pieces = new ArrayList<>();
        for (int k = 0; k <= junctions.size(); k++) {
            pieces.add(piece(pieceFirst(minY, junctions, k), pieceLast(maxY, junctions, k), random.at(PIECE, k, 0)));
        }
        List<PatchTree> patches = new ArrayList<>();
        for (int k = 0; k < junctions.size(); k++) {
            patches.add(patch(junctions.get(k), pieces.get(k), pieces.get(k + 1), random.at(PATCH, k, 0)));
        }
        return new ColumnMaze(List.copyOf(pieces), List.copyOf(patches));
    }

    private static Piece piece(int first, int last, RandomSource random) {
        if (first > last) {
            return new Piece(first, last, List.of());
        }
        int[] rulings = new int[(last - first) / 2 + 1];
        for (int u = 0; u < rulings.length; u++) {
            rulings[u] = first + 2 * u;
        }
        RulingGraph graph = new RulingGraph(rulings);
        int[] parent = graph.graph.sampleTree(random);
        List<MazeEdge> edges = MazeEdge.treeEdges(parent, graph.names(), (virtual, partner) -> {
            throw new IllegalStateException("A piece has no virtual node");
        });
        return new Piece(first, last, MazeEdge.pruneCornerLeaves(edges));
    }

    private static PatchTree patch(Junction junction, Piece below, Piece above, RandomSource random) {
        RulingGraph graph = new RulingGraph(new int[0]);
        int patch = graph.addPatch(junction.center());
        int belowNode = -1;
        if (!below.isEmpty()) {
            belowNode = graph.addVirtual();
            for (int i = -2; i <= 2; i++) {
                graph.graph.addEdge(belowNode, RulingGraph.patchNode(patch, i, -3));
            }
        }
        if (!above.isEmpty()) {
            int aboveNode = graph.addVirtual();
            for (int i = -2; i <= 2; i++) {
                graph.graph.addEdge(aboveNode, RulingGraph.patchNode(patch, i, 3));
            }
        }
        int belowVirtual = belowNode;
        int[] parent = graph.graph.sampleTree(random);
        List<MazeEdge> edges = MazeEdge.treeEdges(parent, graph.names(), (virtual, partner) -> {
            MazeNode.Patch node = (MazeNode.Patch) partner;
            int along = virtual == belowVirtual ? below.last() : above.first();
            return new MazeNode.Ruling(along, junction.columnNode(node.i()));
        });
        return new PatchTree(junction, edges);
    }

}
