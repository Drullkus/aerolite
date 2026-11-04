package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.shaft.surface.PillarRing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Puts two boxes 31 to 35 blocks apart on one shaft and on one column, so that one junction segment holds both.
 * Every maze type keeps each pillar one tree over every maze node. Lines is a forest by design.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class MergedJunctionTest {

    private static final int SCENES = 100;
    private static final int MIN_GAP = 31;
    private static final int MAX_GAP = 35;
    private static final float BIAS = 0.5F;
    private static final float[] STRENGTHS = {0.5F, 0.9F, 1};
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private final MazeEdges mazeEdges = new MazeEdges();

    /** The rows of the prototype: the uniform maze, each pattern type at three strengths, each ribs seam, and lines at each spacing. */
    private static List<MazeSettings> rows() {
        List<MazeSettings> rows = new ArrayList<>();
        rows.add(MazeSettings.DEFAULT);
        List<MazeSettings> types = List.of(
                MazeSettings.DEFAULT.withType(MazeSettings.Type.SERPENTINE),
                MazeSettings.DEFAULT.withType(MazeSettings.Type.RIBS),
                MazeSettings.DEFAULT.withType(MazeSettings.Type.RIBS).withSeam(MazeSettings.Seam.SEEDED),
                MazeSettings.DEFAULT.withType(MazeSettings.Type.RIBS).withSeam(MazeSettings.Seam.PILLAR),
                MazeSettings.DEFAULT.withType(MazeSettings.Type.GILBERT));
        for (MazeSettings type : types) {
            for (float strength : STRENGTHS) {
                rows.add(type.withStrength(strength));
            }
        }
        for (int spacing = MazeSettings.MIN_SPACING; spacing <= MazeSettings.MAX_SPACING; spacing++) {
            rows.add(MazeSettings.DEFAULT.withType(MazeSettings.Type.LINES).withSpacing(spacing));
        }
        return rows;
    }

    private static ShaftMaze.BoxLookup lookup(List<ShaftMaze.Box> boxes) {
        return (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
    }

    private ShaftMaze shaft(long seed, List<ShaftMaze.Box> boxes, MazeSettings settings) {
        return new ShaftMaze(PillarRing.INSTANCE, this.mazeEdges, lookup(boxes), new XoroshiroRandomSource(seed).forkPositional(),
                Integer.MIN_VALUE / ShaftMaze.CHUNK, Integer.MAX_VALUE / ShaftMaze.CHUNK - 1, BIAS, settings);
    }

    private ShaftMaze column(long seed, List<ShaftMaze.Box> boxes, MazeSettings settings) {
        return new ShaftMaze(PillarRing.INSTANCE, this.mazeEdges, lookup(boxes), new XoroshiroRandomSource(seed).forkPositional(),
                Math.floorDiv(MIN_Y, ShaftMaze.CHUNK), Math.floorDiv(MAX_Y - 1, ShaftMaze.CHUNK), BIAS, settings);
    }

    /** Draws a first center and a gap until the junction segment of the first box holds both boxes. */
    private List<ShaftMaze.Box> mergedBoxes(RandomSource random, int low, int high, boolean column) {
        while (true) {
            int c1 = low + random.nextInt(high - low);
            int c2 = c1 + MIN_GAP + random.nextInt(MAX_GAP - MIN_GAP + 1);
            List<ShaftMaze.Box> boxes = List.of(this.box(random, c1, column), this.box(random, c2, column));
            ShaftMaze probe = column ? this.column(0, boxes, MazeSettings.DEFAULT) : this.shaft(0, boxes, MazeSettings.DEFAULT);
            if (probe.segmentAt(Math.floorDiv(c1, ShaftMaze.CHUNK)).boxes().size() == 2) {
                return boxes;
            }
        }
    }

    private ShaftMaze.Box box(RandomSource random, int center, boolean column) {
        int sign = random.nextBoolean() ? 1 : -1;
        return column ? new ColumnJunction(center, sign, random.nextBoolean()).box() : new ShaftMaze.Box(center, sign);
    }

    /** The distinct segments from the first chunk to the last chunk. */
    private static List<ShaftMaze.Segment> segments(ShaftMaze maze, int firstChunk, int lastChunk) {
        Map<Integer, ShaftMaze.Segment> segments = new LinkedHashMap<>();
        int chunk = firstChunk;
        while (chunk <= lastChunk) {
            ShaftMaze.Segment segment = maze.segmentAt(chunk);
            segments.putIfAbsent(segment.firstChunk(), segment);
            chunk = segment.lastChunk() + 1;
        }
        return List.copyOf(segments.values());
    }

    /** Every ruling node that is not a corner, and every patch node. */
    private static Set<MazeNode> mazeNodes(List<ShaftMaze.Segment> segments) {
        Set<MazeNode> nodes = new HashSet<>();
        for (ShaftMaze.Segment segment : segments) {
            for (int along : segment.rulings()) {
                for (int node = 0; node < PillarRing.INSTANCE.nodeCount(); node++) {
                    if (PillarRing.INSTANCE.hasAxisEdge(node)) {
                        nodes.add(new MazeNode.Ruling(along, node));
                    }
                }
            }
            for (ShaftMaze.Box box : segment.boxes()) {
                for (int i = -Ring.PATCH_INDEX; i <= Ring.PATCH_INDEX; i++) {
                    for (int j = -Ring.PATCH_INDEX; j <= Ring.PATCH_INDEX; j++) {
                        nodes.add(new MazeNode.Patch(box.center(), i, j));
                    }
                }
            }
        }
        return nodes;
    }

    private static void check(List<ShaftMaze.Segment> segments, MazeSettings settings, String scene) {
        assertTrue(segments.stream().anyMatch(segment -> segment.boxes().size() == 2), () -> "No junction segment holds both boxes in " + scene);
        List<MazeEdge> edges = new ArrayList<>();
        segments.forEach(segment -> edges.addAll(segment.edges()));
        if (settings.type() == MazeSettings.Type.LINES) {
            int components = TreeCheck.assertForest(edges);
            assertTrue(components > 1, () -> "Lines is one tree in " + scene);
            return;
        }
        TreeCheck.assertOneTree(edges);
        Set<MazeNode> carried = new HashSet<>();
        edges.forEach(edge -> {
            carried.add(edge.a());
            carried.add(edge.b());
        });
        Set<MazeNode> missing = new HashSet<>(mazeNodes(segments));
        missing.removeAll(carried);
        assertEquals(Set.of(), missing, () -> "The tree misses maze nodes in " + scene);
    }

    @Test
    void mergedShaftsAreOneTree() {
        for (int k = 0; k < SCENES; k++) {
            List<ShaftMaze.Box> boxes = this.mergedBoxes(new XoroshiroRandomSource(1000 + k), 100, 400, false);
            int first = Math.floorDiv(boxes.get(0).center(), ShaftMaze.CHUNK) - 4;
            int last = Math.floorDiv(boxes.get(1).center(), ShaftMaze.CHUNK) + 4;
            // Even ends are tree segments, so no edge leaves the range.
            first -= Math.floorMod(first, 2);
            last += Math.floorMod(last, 2);
            for (MazeSettings settings : rows()) {
                check(segments(this.shaft(k, boxes, settings), first, last), settings, "shaft " + k + " " + boxes + " " + settings);
            }
        }
    }

    @Test
    void mergedColumnsAreOneTree() {
        for (int k = 0; k < SCENES; k++) {
            List<ShaftMaze.Box> boxes = this.mergedBoxes(new XoroshiroRandomSource(2000 + k), MIN_Y + 24, MAX_Y - 24 - MAX_GAP, true);
            for (MazeSettings settings : rows()) {
                ShaftMaze column = this.column(k, boxes, settings);
                check(segments(column, column.minChunk(), column.maxChunk()), settings, "column " + k + " " + boxes + " " + settings);
            }
        }
    }

}
