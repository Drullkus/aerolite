package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the segments of a horizontal shaft maze.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
class ShaftMazeTest {

    private final Ring ring = new Ring();
    private final ShaftMazes mazes = new ShaftMazes(this.ring, new MazeEdges(this.ring));

    private ShaftMaze maze(long seed, List<ShaftMaze.Box> boxes) {
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return this.mazes.shaft(lookup, new XoroshiroRandomSource(seed).forkPositional());
    }

    /** The distinct segments from the first chunk to the last chunk, by first chunk. */
    private static Map<Integer, ShaftMaze.Segment> segments(ShaftMaze maze, int firstChunk, int lastChunk) {
        Map<Integer, ShaftMaze.Segment> segments = new LinkedHashMap<>();
        for (int chunk = firstChunk; chunk <= lastChunk; chunk++) {
            ShaftMaze.Segment segment = maze.segmentAt(chunk);
            segments.putIfAbsent(segment.firstChunk(), segment);
        }
        return segments;
    }

    private static List<MazeEdge> edges(Map<Integer, ShaftMaze.Segment> segments) {
        List<MazeEdge> edges = new ArrayList<>();
        segments.values().forEach(segment -> edges.addAll(segment.edges()));
        return edges;
    }

    @Test
    void ringBlocksRoundTrip() {
        for (int block = 0; block < Ring.BLOCKS; block++) {
            assertEquals(block, this.ring.block(this.ring.first(block), this.ring.second(block)));
        }
    }

    @Test
    void plainShaftIsOneTree() {
        for (long seed = 0; seed < 4; seed++) {
            TreeCheck.assertOneTree(edges(segments(maze(seed, List.of()), 0, 10)));
        }
    }

    @Test
    void plainShaftIsOneTreeAtNegativeChunks() {
        TreeCheck.assertOneTree(edges(segments(maze(7, List.of()), -12, -2)));
    }

    @Test
    void connectorHasCrossingsOnBothSides() {
        ShaftMaze.Segment connector = maze(3, List.of()).segmentAt(5);
        assertTrue(connector.edges().stream().anyMatch(edge -> edge.a() instanceof MazeNode.Ruling a && a.along() == 16 * 5 - 1
                || edge.b() instanceof MazeNode.Ruling b && b.along() == 16 * 5 - 1));
        assertTrue(connector.edges().stream().anyMatch(edge -> edge.a() instanceof MazeNode.Ruling a && a.along() == 16 * 6 + 1
                || edge.b() instanceof MazeNode.Ruling b && b.along() == 16 * 6 + 1));
    }

    @Test
    void junctionShaftIsOneTree() {
        for (int center = 160; center < 192; center++) {
            for (int sign : new int[]{-1, 1}) {
                ShaftMaze maze = maze(center, List.of(new ShaftMaze.Box(center, sign)));
                TreeCheck.assertOneTree(edges(segments(maze, 4, 18)));
            }
        }
    }

    @Test
    void closeBoxesShareOneJunctionSegment() {
        List<ShaftMaze.Box> boxes = List.of(new ShaftMaze.Box(184, 1), new ShaftMaze.Box(215, -1), new ShaftMaze.Box(246, 1));
        ShaftMaze maze = maze(11, boxes);
        ShaftMaze.Segment segment = maze.segmentAt(12);
        assertEquals(2, segment.boxes().size());
        assertEquals(segment.edges(), maze.segmentAt(segment.firstChunk()).edges());
        assertEquals(segment.edges(), maze.segmentAt(segment.lastChunk()).edges());
        assertEquals(1, maze.segmentAt(segment.lastChunk() + 1).boxes().size());
        TreeCheck.assertOneTree(edges(segments(maze, 6, 20)));
    }

    @Test
    void segmentsArePure() {
        ShaftMaze first = maze(5, List.of(new ShaftMaze.Box(300, 1)));
        ShaftMaze second = maze(5, List.of(new ShaftMaze.Box(300, 1)));
        for (int chunk = 14; chunk <= 22; chunk++) {
            ShaftMaze.Segment a = first.segmentAt(chunk);
            ShaftMaze.Segment b = second.segmentAt(chunk);
            assertEquals(a.edges(), b.edges());
            assertArrayEquals(a.rulings(), b.rulings());
        }
    }

    @Test
    void junctionDropsTheBoxRulings() {
        ShaftMaze.Box box = new ShaftMaze.Box(203, 1);
        ShaftMaze.Segment segment = maze(1, List.of(box)).segmentAt(12);
        for (int along : segment.rulings()) {
            assertTrue(Math.abs(along - box.center()) > Ring.BOX);
        }
    }

}
