package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the segments of a column maze.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 */
class ColumnMazeTest {

    private final Ring ring = new Ring();
    private final ShaftMazes mazes = new ShaftMazes(new MazeEdges());

    private ShaftMaze column(long seed, int minY, int maxY, List<ColumnJunction> junctions) {
        return this.column(seed, minY, maxY, junctions, 0.5F);
    }

    private ShaftMaze column(long seed, int minY, int maxY, List<ColumnJunction> junctions, float bias) {
        List<ShaftMaze.Box> boxes = junctions.stream().map(ColumnJunction::box).toList();
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return this.mazes.bounded(lookup, new XoroshiroRandomSource(seed).forkPositional(), Math.floorDiv(minY, 16), Math.floorDiv(maxY - 1, 16), bias);
    }

    private static List<ShaftMaze.Segment> segments(ShaftMaze maze) {
        List<ShaftMaze.Segment> segments = new ArrayList<>();
        int chunk = maze.minChunk();
        while (chunk <= maze.maxChunk()) {
            ShaftMaze.Segment segment = maze.segmentAt(chunk);
            segments.add(segment);
            chunk = segment.lastChunk() + 1;
        }
        return segments;
    }

    private static List<MazeEdge> edges(ShaftMaze maze) {
        List<MazeEdge> edges = new ArrayList<>();
        segments(maze).forEach(segment -> edges.addAll(segment.edges()));
        return edges;
    }

    @Test
    void columnIsOneTree() {
        for (float bias : ShaftMazeTest.BIASES) {
            for (long seed = 0; seed < 4; seed++) {
                List<ColumnJunction> junctions = List.of(new ColumnJunction(-40, 1, true), new ColumnJunction(120, -1, false));
                List<MazeEdge> edges = edges(column(seed, -64, 320, junctions, bias));
                TreeCheck.assertOneTree(edges);
                long patchNodes = edges.stream().flatMap(edge -> Stream.of(edge.a(), edge.b())).filter(MazeNode.Patch.class::isInstance).distinct().count();
                assertEquals(2 * Ring.PATCH_NODES * Ring.PATCH_NODES, patchNodes, "The tree misses a patch node at bias " + bias);
                Set<Integer> rulings = new HashSet<>();
                edges.stream().flatMap(edge -> Stream.of(edge.a(), edge.b()))
                        .filter(MazeNode.Ruling.class::isInstance)
                        .forEach(node -> rulings.add(((MazeNode.Ruling) node).along()));
                // Each box drops the 8 rulings that it covers.
                assertEquals((320 + 64) / 2 - 2 * 8, rulings.size(), "The tree misses a ruling at bias " + bias);
            }
        }
    }

    /** On a column, patch index j runs along the column. So the crossings leave the nodes at the extreme j. */
    @Test
    void columnCrossingsSkipThePatchCorners() {
        for (float bias : ShaftMazeTest.BIASES) {
            for (long seed = 0; seed < 8; seed++) {
                List<ColumnJunction> junctions = List.of(new ColumnJunction(-40, 1, true), new ColumnJunction(120, -1, false));
                for (MazeEdge edge : edges(column(seed, -64, 320, junctions, bias))) {
                    MazeNode.Patch patch = edge.a() instanceof MazeNode.Patch a && edge.b() instanceof MazeNode.Ruling ? a
                            : edge.b() instanceof MazeNode.Patch b && edge.a() instanceof MazeNode.Ruling ? b : null;
                    if (patch != null) {
                        assertEquals(Ring.PATCH_INDEX, Math.abs(patch.j()), () -> "A column crossing leaves a patch node off the border: " + edge);
                        assertTrue(Math.abs(patch.i()) < Ring.PATCH_INDEX, () -> "A column crossing leaves a patch corner node: " + edge);
                    }
                }
            }
        }
    }

    @Test
    void closeJunctionsShareOneSegment() {
        for (float bias : ShaftMazeTest.BIASES) {
            List<ColumnJunction> junctions = List.of(new ColumnJunction(23, 1, true), new ColumnJunction(40, -1, false));
            ShaftMaze maze = column(3, 0, 256, junctions, bias);
            assertTrue(segments(maze).stream().anyMatch(segment -> segment.boxes().size() == 2));
            TreeCheck.assertOneTree(edges(maze));
        }
    }

    @Test
    void noRulingLeavesTheBuildHeight() {
        ShaftMaze maze = column(5, 0, 256, List.of(new ColumnJunction(100, 1, true)));
        for (MazeEdge edge : edges(maze)) {
            for (var node : List.of(edge.a(), edge.b())) {
                if (node instanceof MazeNode.Ruling ruling) {
                    assertTrue(ruling.along() > 0 && ruling.along() < 256, () -> "ruling at " + ruling.along());
                }
            }
        }
    }

    @Test
    void segmentsStayShort() {
        for (ShaftMaze.Segment segment : segments(column(6, -64, 320, List.of(new ColumnJunction(-40, 1, true), new ColumnJunction(200, 1, false))))) {
            assertTrue(segment.lastChunk() - segment.firstChunk() <= 2);
        }
    }

    @Test
    void crossingNodesFaceThePatch() {
        ShaftMaze maze = this.column(0, 0, 256, List.of());
        ShaftMaze.Box alongX = new ColumnJunction(0, 1, true).box();
        ShaftMaze.Box alongZ = new ColumnJunction(0, -1, false).box();
        for (int i = -Ring.PATCH_INDEX; i <= Ring.PATCH_INDEX; i++) {
            int x = maze.crossingNode(alongX, i) * 2;
            assertEquals(2 * i, this.ring.first(x));
            assertEquals(Ring.OUTER, this.ring.second(x));
            int z = maze.crossingNode(alongZ, i) * 2;
            assertEquals(-Ring.OUTER, this.ring.first(z));
            assertEquals(2 * i, this.ring.second(z));
        }
    }

}
