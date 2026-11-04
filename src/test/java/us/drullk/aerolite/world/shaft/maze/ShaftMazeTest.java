package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the segments of a horizontal shaft maze.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 */
class ShaftMazeTest {

    private final Ring ring = new Ring();
    private final ShaftMazes mazes = new ShaftMazes(new MazeEdges());

    /** The smallest, the uniform and the largest maze bias. */
    static final float[] BIASES = {0.05F, 0.5F, 0.95F};

    private ShaftMaze maze(long seed, List<ShaftMaze.Box> boxes) {
        return this.maze(seed, boxes, 0.5F);
    }

    private ShaftMaze maze(long seed, List<ShaftMaze.Box> boxes, float bias) {
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return this.mazes.shaft(lookup, new XoroshiroRandomSource(seed).forkPositional(), bias);
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
        for (float bias : BIASES) {
            for (long seed = 0; seed < 4; seed++) {
                ShaftMaze maze = maze(seed, List.of(), bias);
                int nodes = TreeCheck.assertOneTree(edges(segments(maze, 0, 10)));
                assertEquals(reachableNodes(maze, 0, 10), nodes, "bias " + bias);
            }
        }
    }

    @Test
    void plainShaftIsOneTreeAtNegativeChunks() {
        for (float bias : BIASES) {
            TreeCheck.assertOneTree(edges(segments(maze(7, List.of(), bias), -12, -2)));
        }
    }

    /** The node count of the rulings from the first chunk to the last chunk, without the corner nodes that pruning can drop. */
    private int reachableNodes(ShaftMaze maze, int firstChunk, int lastChunk) {
        int rulings = (lastChunk - firstChunk + 1) * ShaftMaze.RULINGS_PER_CHUNK;
        return rulings * (Ring.NODES - 4) + cornerNodes(edges(segments(maze, firstChunk, lastChunk)));
    }

    private int cornerNodes(List<MazeEdge> edges) {
        Set<MazeNode> corners = new HashSet<>();
        for (MazeEdge edge : edges) {
            for (MazeNode node : List.of(edge.a(), edge.b())) {
                if (node instanceof MazeNode.Ruling ruling && this.ring.isCorner(ruling.node())) {
                    corners.add(node);
                }
            }
        }
        return corners.size();
    }

    @Test
    void biasShiftsTheShareOfAxisEdges() {
        double ringwise = axisShare(maze(2, List.of(), 0.05F));
        double uniform = axisShare(maze(2, List.of(), 0.5F));
        double lengthwise = axisShare(maze(2, List.of(), 0.95F));
        assertTrue(ringwise < uniform && uniform < lengthwise, ringwise + " " + uniform + " " + lengthwise);
    }

    /** The share of tree edges that join two rulings. */
    private static double axisShare(ShaftMaze maze) {
        List<MazeEdge> edges = edges(segments(maze, 0, 10));
        long axis = edges.stream().filter(edge -> edge.a() instanceof MazeNode.Ruling a && edge.b() instanceof MazeNode.Ruling b && a.along() != b.along()).count();
        return axis / (double) edges.size();
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
        for (float bias : BIASES) {
            for (int center = 160; center < 192; center++) {
                for (int sign : new int[]{-1, 1}) {
                    ShaftMaze maze = maze(center, List.of(new ShaftMaze.Box(center, sign)), bias);
                    int nodes = TreeCheck.assertOneTree(edges(segments(maze, 4, 18)));
                    long patchNodes = edges(segments(maze, 4, 18)).stream()
                            .flatMap(edge -> Stream.of(edge.a(), edge.b()))
                            .filter(MazeNode.Patch.class::isInstance)
                            .distinct()
                            .count();
                    assertEquals(Ring.PATCH_NODES * Ring.PATCH_NODES, patchNodes, "The tree misses a patch node at bias " + bias);
                    assertTrue(nodes > patchNodes);
                }
            }
        }
    }

    /** The patch border nodes that face the shaft get a crossing each. The patch corner nodes get none. */
    @Test
    void crossingsSkipThePatchCorners() {
        ShaftMaze.Box box = new ShaftMaze.Box(203, 1);
        ShaftMaze maze = maze(1, List.of(box));
        Set<List<Integer>> crossed = new HashSet<>();
        for (ShaftMaze.Crossing crossing : maze.crossings(box)) {
            assertEquals(Ring.PATCH_INDEX, Math.abs(crossing.along()));
            assertTrue(Math.abs(crossing.across()) < Ring.PATCH_INDEX, () -> "A patch corner node has a crossing: " + crossing);
            assertEquals(maze.crossingNode(box, crossing.across()), crossing.node());
            assertTrue(crossed.add(List.of(crossing.along(), crossing.across())), () -> "A patch node has two crossings: " + crossing);
        }
        Set<List<Integer>> expected = new HashSet<>();
        for (int along : new int[]{-Ring.PATCH_INDEX, Ring.PATCH_INDEX}) {
            for (int across = 1 - Ring.PATCH_INDEX; across < Ring.PATCH_INDEX; across++) {
                expected.add(List.of(along, across));
            }
        }
        assertEquals(expected, crossed);
        assertEquals(2 * (Ring.PATCH_NODES - 2), crossed.size());
    }

    /** No tree edge joins a patch corner node to a ruling. */
    @Test
    void treesHaveNoCornerCrossing() {
        for (float bias : BIASES) {
            for (int center = 160; center < 192; center++) {
                ShaftMaze maze = maze(center, List.of(new ShaftMaze.Box(center, 1)), bias);
                for (MazeEdge edge : edges(segments(maze, 4, 18))) {
                    MazeNode.Patch patch = edge.a() instanceof MazeNode.Patch a && edge.b() instanceof MazeNode.Ruling ? a
                            : edge.b() instanceof MazeNode.Patch b && edge.a() instanceof MazeNode.Ruling ? b : null;
                    if (patch != null) {
                        assertEquals(Ring.PATCH_INDEX, Math.abs(patch.i()), () -> "A crossing leaves a patch node off the border: " + edge);
                        assertTrue(Math.abs(patch.j()) < Ring.PATCH_INDEX, () -> "A crossing leaves a patch corner node: " + edge);
                    }
                }
            }
        }
    }

    @Test
    void closeBoxesShareOneJunctionSegment() {
        List<ShaftMaze.Box> boxes = List.of(new ShaftMaze.Box(184, 1), new ShaftMaze.Box(215, -1), new ShaftMaze.Box(246, 1));
        for (float bias : BIASES) {
            closeBoxesShareOneJunctionSegment(boxes, bias);
        }
    }

    private void closeBoxesShareOneJunctionSegment(List<ShaftMaze.Box> boxes, float bias) {
        ShaftMaze maze = maze(11, boxes, bias);
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
