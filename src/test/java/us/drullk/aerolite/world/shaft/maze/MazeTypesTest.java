package us.drullk.aerolite.world.shaft.maze;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.shaft.surface.PillarRing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the maze types on a horizontal shaft.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class MazeTypesTest {

    private static final MazeSettings SERPENTINE = MazeSettings.DEFAULT.withType(MazeSettings.Type.SERPENTINE);
    private static final MazeSettings LINES = MazeSettings.DEFAULT.withType(MazeSettings.Type.LINES);

    private final MazeEdges mazeEdges = new MazeEdges();

    private ShaftMaze maze(long seed, List<ShaftMaze.Box> boxes, MazeSettings settings) {
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return new ShaftMaze(PillarRing.INSTANCE, this.mazeEdges, lookup, new XoroshiroRandomSource(seed).forkPositional(),
                Integer.MIN_VALUE / ShaftMaze.CHUNK, Integer.MAX_VALUE / ShaftMaze.CHUNK - 1, 0.5F, settings);
    }

    private static List<MazeEdge> edges(ShaftMaze maze, int firstChunk, int lastChunk) {
        List<MazeEdge> edges = new ArrayList<>();
        int chunk = firstChunk;
        while (chunk <= lastChunk) {
            ShaftMaze.Segment segment = maze.segmentAt(chunk);
            edges.addAll(segment.edges());
            chunk = segment.lastChunk() + 1;
        }
        return edges;
    }

    /** The uniform type with any strength, and any type at strength 0, draws the maze of 0.3. */
    @Test
    void uniformAndZeroStrengthKeepTheMaze() {
        List<ShaftMaze.Box> boxes = List.of(new ShaftMaze.Box(203, 1));
        List<MazeSettings> same = new ArrayList<>(List.of(MazeSettings.DEFAULT.withStrength(0.7F), MazeSettings.DEFAULT.withStrength(1)));
        for (MazeSettings.Type type : MazeSettings.Type.values()) {
            if (type != MazeSettings.Type.LINES) {
                same.add(MazeSettings.DEFAULT.withType(type).withStrength(0));
            }
        }
        for (long seed = 0; seed < 4; seed++) {
            List<MazeEdge> expected = edges(maze(seed, boxes, MazeSettings.DEFAULT), 4, 18);
            for (MazeSettings settings : same) {
                assertEquals(expected, edges(maze(seed, boxes, settings), 4, 18), settings.toString());
            }
        }
    }

    /** A connector turns into its neighbor next to the gap of the facing neighbor ruling. */
    @Test
    void serpentineTurnsNextToTheNeighborGap() {
        for (long seed = 0; seed < 16; seed++) {
            ShaftMaze maze = maze(seed, List.of(), SERPENTINE.withStrength(1));
            for (int connector = 1; connector < 12; connector += 2) {
                int before = ShaftMaze.CHUNK * connector - 1;
                int after = ShaftMaze.CHUNK * (connector + 1) - 1;
                assertTurn(maze.segmentAt(connector).edges(), maze.segmentAt(connector - 1).edges(), before);
                assertTurn(maze.segmentAt(connector).edges(), maze.segmentAt(connector).edges(), after);
            }
        }
    }

    /** One crossing leaves the ruling at {@code along}. Its ring position has at most one ring edge on that ruling. */
    private static void assertTurn(List<MazeEdge> crossingEdges, List<MazeEdge> rulingEdges, int along) {
        List<Integer> crossings = new ArrayList<>();
        for (MazeEdge edge : crossingEdges) {
            if (edge.a() instanceof MazeNode.Ruling a && edge.b() instanceof MazeNode.Ruling b && Math.min(a.along(), b.along()) == along
                    && Math.max(a.along(), b.along()) == along + 2) {
                crossings.add(a.node());
            }
        }
        assertEquals(1, crossings.size(), () -> "The border at " + along + " has crossings at " + crossings);
        int node = crossings.get(0);
        long ringEdges = rulingEdges.stream().filter(edge -> edge.a() instanceof MazeNode.Ruling a && edge.b() instanceof MazeNode.Ruling b
                && a.along() == along && b.along() == along && (a.node() == node || b.node() == node)).count();
        assertTrue(ringEdges <= 1, () -> "The crossing at node " + node + " of ruling " + along + " sits away from the gap");
    }

    /** Spacing 1 grooves every node column with axis edges. Spacing 2 grooves every second one, 3, 2, 3, 2 per face. */
    @Test
    void linesGrooveTheNodeColumns() {
        Set<Integer> every = new TreeSet<>();
        for (int node = 0; node < Ring.NODES; node++) {
            if (!new Ring().isCorner(node)) {
                every.add(node);
            }
        }
        assertEquals(every, grooveColumns(maze(1, List.of(), LINES)));
        assertEquals(Set.of(1, 3, 5, 8, 10, 13, 15, 17, 20, 22), grooveColumns(maze(1, List.of(), LINES.withSpacing(2))));
    }

    private static Set<Integer> grooveColumns(ShaftMaze maze) {
        Set<Integer> columns = new TreeSet<>();
        for (MazeEdge edge : edges(maze, 0, 6)) {
            MazeNode.Ruling a = (MazeNode.Ruling) edge.a();
            MazeNode.Ruling b = (MazeNode.Ruling) edge.b();
            assertEquals(a.node(), b.node(), "Lines holds a ring edge");
            columns.add(a.node());
        }
        return columns;
    }

    /** At full strength each groove runs straight through the junction patch. Only the center patch line holds a groove at spacing 2. */
    @Test
    void linesCrossThePatchOnGrooveColumns() {
        ShaftMaze.Box box = new ShaftMaze.Box(203, 1);
        Set<Integer> lines = new HashSet<>();
        for (MazeEdge edge : maze(2, List.of(box), LINES.withSpacing(2)).segmentAt(12).edges()) {
            if (edge.a() instanceof MazeNode.Patch a && edge.b() instanceof MazeNode.Patch b) {
                assertEquals(a.j(), b.j(), "A patch groove runs across the axis");
                lines.add(a.j());
            }
        }
        assertEquals(Set.of(0), lines);
    }

    /** A strong serpentine favors the ring edges of its ring paths. */
    @Test
    void strengthPullsTowardThePattern() {
        double uniform = ringShare(edges(maze(4, List.of(), MazeSettings.DEFAULT), 0, 10));
        double serpentine = ringShare(edges(maze(4, List.of(), SERPENTINE.withStrength(0.9F)), 0, 10));
        assertTrue(serpentine > uniform + 0.1, uniform + " " + serpentine);
    }

    private static double ringShare(List<MazeEdge> edges) {
        long ring = edges.stream().filter(edge -> edge.a() instanceof MazeNode.Ruling a && edge.b() instanceof MazeNode.Ruling b && a.along() == b.along()).count();
        return ring / (double) edges.size();
    }

    /** Full strength serpentine, ribs and gilbert keep the plain shaft one tree. */
    @Test
    void fullStrengthPlainShaftIsOneTree() {
        for (MazeSettings.Type type : List.of(MazeSettings.Type.SERPENTINE, MazeSettings.Type.RIBS, MazeSettings.Type.GILBERT)) {
            for (MazeSettings.Seam seam : MazeSettings.Seam.values()) {
                for (long seed = 0; seed < 4; seed++) {
                    TreeCheck.assertOneTree(edges(maze(seed, List.of(), MazeSettings.DEFAULT.withType(type).withSeam(seam).withStrength(1)), 0, 10));
                }
            }
        }
    }

    /**
     * The curve visits every cell once and steps to a neighbor cell.
     * Like the prototype, a run of an odd number of rulings longer than the ring takes one diagonal step, which the graph drops.
     */
    @Test
    void gilbertCurveFillsTheRectangle() {
        MazeTypes types = new MazeTypes(PillarRing.INSTANCE, MazeSettings.DEFAULT);
        for (int height = 1; height <= 40; height++) {
            IntList cells = types.gilbertCells(Ring.NODES, height);
            assertEquals(2 * Ring.NODES * height, cells.size());
            Set<Integer> seen = new HashSet<>();
            int diagonals = 0;
            for (int c = 0; c < cells.size(); c += 2) {
                int x = cells.getInt(c);
                int y = cells.getInt(c + 1);
                assertTrue(x >= 0 && x < Ring.NODES && y >= 0 && y < height);
                assertTrue(seen.add(y * Ring.NODES + x), "The curve visits a cell twice");
                if (c > 0) {
                    int dx = Math.abs(x - cells.getInt(c - 2));
                    int dy = Math.abs(y - cells.getInt(c - 1));
                    assertTrue(dx <= 1 && dy <= 1 && dx + dy >= 1, "The curve jumps at height " + height);
                    diagonals += dx + dy == 2 ? 1 : 0;
                }
            }
            assertEquals(height > Ring.NODES && height % 2 == 1 ? 1 : 0, diagonals, "height " + height);
        }
    }

    /** The curve equals the curve of the prototype: the checksums come from {@code gilbert_cells} of {@code originshift.py}. */
    @Test
    void gilbertCurveMatchesThePrototype() {
        MazeTypes types = new MazeTypes(PillarRing.INSTANCE, MazeSettings.DEFAULT);
        int[][] expected = {{1, 565293747}, {2, 377134020}, {3, 790619558}, {8, 295479796}, {15, 664938123}, {16, 907513350}, {27, 617026650}, {40, 540566144}};
        for (int[] row : expected) {
            IntList cells = types.gilbertCells(Ring.NODES, row[0]);
            long hash = 0;
            for (int c = 0; c < cells.size(); c += 2) {
                hash = (hash * 31 + cells.getInt(c + 1) * Ring.NODES + cells.getInt(c)) % 1_000_000_007L;
            }
            assertEquals(row[1], hash, "height " + row[0]);
        }
    }

}
