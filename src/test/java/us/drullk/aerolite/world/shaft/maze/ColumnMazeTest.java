package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the segments of a column maze.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
class ColumnMazeTest {

    private final Ring ring = new Ring();
    private final ShaftMazes mazes = new ShaftMazes(this.ring, new MazeEdges(this.ring));

    private ShaftMaze column(long seed, int minY, int maxY, List<ColumnJunction> junctions) {
        List<ShaftMaze.Box> boxes = junctions.stream().map(ColumnJunction::box).toList();
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return this.mazes.bounded(lookup, new XoroshiroRandomSource(seed).forkPositional(), Math.floorDiv(minY, 16), Math.floorDiv(maxY - 1, 16));
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
        for (long seed = 0; seed < 4; seed++) {
            List<ColumnJunction> junctions = List.of(new ColumnJunction(-40, 1, true), new ColumnJunction(120, -1, false));
            TreeCheck.assertOneTree(edges(column(seed, -64, 320, junctions)));
        }
    }

    @Test
    void closeJunctionsShareOneSegment() {
        List<ColumnJunction> junctions = List.of(new ColumnJunction(23, 1, true), new ColumnJunction(40, -1, false));
        ShaftMaze maze = column(3, 0, 256, junctions);
        assertTrue(segments(maze).stream().anyMatch(segment -> segment.boxes().size() == 2));
        TreeCheck.assertOneTree(edges(maze));
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
        for (int i = -2; i <= 2; i++) {
            int x = maze.crossingNode(alongX, i) * 2;
            assertEquals(2 * i, this.ring.first(x));
            assertEquals(Ring.OUTER, this.ring.second(x));
            int z = maze.crossingNode(alongZ, i) * 2;
            assertEquals(-Ring.OUTER, this.ring.first(z));
            assertEquals(2 * i, this.ring.second(z));
        }
    }

}
