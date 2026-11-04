package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.shaft.shape.ShapeRings;
import us.drullk.aerolite.world.shaft.shape.WebPlane;
import us.drullk.aerolite.world.shaft.surface.RingShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the junction link of the I-beam and the Bridge against the results of the shapes prototype.
 * The column scene has an X pillar with a box at Y -24 and a Z pillar with a box at Y 24, both on one column.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class ShapeMazeTest {

    private static final ShapeRings SHAPES = new ShapeRings();
    private static final MazeEdges EDGES = new MazeEdges();
    private static final int X_BOX_Y = -24;
    private static final int Z_BOX_Y = 24;

    /** A maze node of one pillar of the scene. */
    private record Key(String pillar, MazeNode node) {
    }

    private static final class Components {
        private final Map<Key, Key> parent = new HashMap<>();

        Key find(Key key) {
            Key root = key;
            while (!this.parent.getOrDefault(root, root).equals(root)) {
                root = this.parent.get(root);
            }
            this.parent.put(key, root);
            return root;
        }

        /** Joins two nodes. Returns false when they already share a component. */
        boolean union(Key a, Key b) {
            Key ra = this.find(a);
            Key rb = this.find(b);
            if (ra.equals(rb)) {
                return false;
            }
            this.parent.put(ra, rb);
            return true;
        }
    }

    private static List<MazeEdge> edges(ShaftMaze maze) {
        List<MazeEdge> edges = new ArrayList<>();
        int chunk = maze.minChunk();
        while (chunk <= maze.maxChunk()) {
            ShaftMaze.Segment segment = maze.segmentAt(chunk);
            edges.addAll(segment.edges());
            chunk = segment.lastChunk() + 1;
        }
        return edges;
    }

    private static ShaftMaze horizontal(RingShape ring, long seed, List<ShaftMaze.Box> boxes) {
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        return new ShaftMaze(ring, EDGES, lookup, new XoroshiroRandomSource(seed).forkPositional(), -4, 3, 0.5F);
    }

    /** The number of components that hold ruling nodes of each ring, for a set of edges on one ring shape. */
    private static int[] ringComponents(RingShape ring, List<MazeEdge> edges) {
        Components components = new Components();
        for (MazeEdge edge : edges) {
            assertTrue(components.union(new Key("", edge.a()), new Key("", edge.b())), () -> "The edge " + edge + " closes a loop");
        }
        List<Set<Key>> roots = new ArrayList<>();
        for (int r = 0; r < ring.rings(); r++) {
            roots.add(new HashSet<>());
        }
        edges.stream().flatMap(edge -> Stream.of(edge.a(), edge.b())).forEach(node -> {
            if (node instanceof MazeNode.Ruling ruling) {
                roots.get(ring.ringOf(ruling.node())).add(components.find(new Key("", node)));
            }
        });
        return roots.stream().mapToInt(Set::size).toArray();
    }

    @Test
    void horizontalBridgeIsOneTreePerPlateWithEachPatchOnOnePlate() {
        RingShape ring = SHAPES.ring(PillarShape.BRIDGE);
        List<ShaftMaze.Box> boxes = List.of(new ShaftMaze.Box(-40, 1), new ShaftMaze.Box(8, -1), new ShaftMaze.Box(30, 1));
        for (long seed = 0; seed < 8; seed++) {
            List<MazeEdge> edges = edges(horizontal(ring, seed, boxes));
            int[] perRing = ringComponents(ring, edges);
            assertEquals(1, perRing[0], "The top plate is not one tree");
            assertEquals(1, perRing[1], "The bottom plate is not one tree");
            assertEquals(2, TreeCheck.assertForest(edges), "The plates join or a patch floats");
            for (ShaftMaze.Box box : boxes) {
                Set<Integer> plates = new HashSet<>();
                int crossings = 0;
                long patchNodes = edges.stream().flatMap(edge -> Stream.of(edge.a(), edge.b()))
                        .filter(node -> node instanceof MazeNode.Patch patch && patch.owner() == box.center()).distinct().count();
                assertEquals(Ring.PATCH_NODES * Ring.PATCH_NODES, patchNodes, "The tree misses a patch node");
                for (MazeEdge edge : edges) {
                    if (edge.a() instanceof MazeNode.Patch patch && edge.b() instanceof MazeNode.Ruling ruling && patch.owner() == box.center()
                            || edge.b() instanceof MazeNode.Patch other && edge.a() instanceof MazeNode.Ruling second && other.owner() == box.center()) {
                        MazeNode.Ruling node = (MazeNode.Ruling) (edge.a() instanceof MazeNode.Ruling ? edge.a() : edge.b());
                        MazeNode.Patch onPatch = (MazeNode.Patch) (edge.a() instanceof MazeNode.Patch ? edge.a() : edge.b());
                        plates.add(ring.ringOf(node.node()));
                        crossings++;
                        assertEquals(Ring.PATCH_INDEX, Math.abs(onPatch.i()));
                        assertEquals(Ring.PATCH_INDEX, Math.abs(onPatch.j()), "A Bridge crossing leaves a patch node off the corner");
                    }
                }
                assertEquals(1, plates.size(), "The patch of the box at " + box.center() + " hangs off " + plates.size() + " plates");
                assertEquals(1, crossings, "The patch of the box at " + box.center() + " takes " + crossings + " crossings");
            }
        }
    }

    @Test
    void horizontalIBeamIsOneTreeThroughItsBoxes() {
        RingShape ring = SHAPES.ring(PillarShape.I_BEAM);
        List<ShaftMaze.Box> boxes = List.of(new ShaftMaze.Box(-40, 1), new ShaftMaze.Box(8, -1));
        for (long seed = 0; seed < 8; seed++) {
            List<MazeEdge> edges = edges(horizontal(ring, seed, boxes));
            TreeCheck.assertOneTree(edges);
            for (MazeEdge edge : edges) {
                if (edge.a() instanceof MazeNode.Patch && edge.b() instanceof MazeNode.Ruling || edge.b() instanceof MazeNode.Patch && edge.a() instanceof MazeNode.Ruling) {
                    MazeNode.Patch patch = (MazeNode.Patch) (edge.a() instanceof MazeNode.Patch ? edge.a() : edge.b());
                    MazeNode.Ruling ruling = (MazeNode.Ruling) (edge.a() instanceof MazeNode.Ruling ? edge.a() : edge.b());
                    int index = ring.indexOf(ruling.node());
                    assertEquals(Ring.OUTER, Math.abs(ring.second(0, index)), "An I-beam crossing leaves a node off the flange edges");
                    assertEquals(4, Math.abs(ring.first(0, index)));
                    assertEquals(Ring.PATCH_INDEX, Math.abs(patch.j()), "An I-beam crossing leaves a patch node off the corner");
                }
            }
        }
    }

    /** The edges of the column scene, with the foreign patch nodes of the column named as patch nodes of their horizontal pillar. */
    private record Scene(RingShape x, RingShape z, RingShape column, List<MazeEdge> xEdges, List<MazeEdge> zEdges, List<MazeEdge> columnEdges) {
    }

    private static Scene scene(PillarShape horizontalShape, PillarShape columnShape, WebPlane plane, long seed, int xSign, int zSign) {
        RingShape horizontal = SHAPES.ring(horizontalShape);
        RingShape column = SHAPES.ring(columnShape, plane);
        List<MazeEdge> x = edges(horizontal(horizontal, seed, List.of(new ShaftMaze.Box(0, xSign))));
        List<MazeEdge> z = edges(horizontal(horizontal, seed + 100, List.of(new ShaftMaze.Box(0, zSign))));
        List<ShaftMaze.Box> boxes = List.of(new ColumnJunction(X_BOX_Y, -xSign, true).box(), new ColumnJunction(Z_BOX_Y, -zSign, false).box());
        ShaftMaze.BoxLookup lookup = (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
        List<MazeEdge> c = edges(new ShaftMaze(column, EDGES, lookup, new XoroshiroRandomSource(seed + 200).forkPositional(), -4, 3, 0.5F));
        return new Scene(horizontal, horizontal, column, x, z, c);
    }

    private static Key columnKey(MazeNode node) {
        if (node instanceof MazeNode.Foreign foreign) {
            return new Key(foreign.owner() == X_BOX_Y ? "x" : "z", new MazeNode.Patch(0, foreign.i(), foreign.j()));
        }
        return new Key("c", node);
    }

    /**
     * Checks the component counts of the column scene: each untouched horizontal pillar gives one component per ring,
     * and the column with each touched horizontal pillar gives the group target.
     */
    private static void checkScene(Scene scene, PillarShape horizontalShape, PillarShape columnShape) {
        Components all = new Components();
        Set<String> touched = new HashSet<>();
        for (MazeEdge edge : scene.xEdges()) {
            assertTrue(all.union(new Key("x", edge.a()), new Key("x", edge.b())), "An X pillar edge closes a loop");
        }
        for (MazeEdge edge : scene.zEdges()) {
            assertTrue(all.union(new Key("z", edge.a()), new Key("z", edge.b())), "A Z pillar edge closes a loop");
        }
        for (MazeEdge edge : scene.columnEdges()) {
            Key a = columnKey(edge.a());
            Key b = columnKey(edge.b());
            Stream.of(a, b).filter(key -> !key.pillar().equals("c")).forEach(key -> touched.add(key.pillar()));
            assertTrue(all.union(a, b), () -> "The column edge " + edge + " closes a loop");
        }
        String label = horizontalShape + " + " + columnShape;
        for (String pillar : List.of("x", "z")) {
            List<MazeEdge> edges = pillar.equals("x") ? scene.xEdges() : scene.zEdges();
            int[] perRing = ringComponents(scene.x(), edges);
            for (int count : perRing) {
                assertEquals(1, count, label + ": a ring of the " + pillar + " pillar is not one tree");
            }
        }
        Set<Key> group = new HashSet<>();
        for (MazeEdge edge : scene.columnEdges()) {
            group.add(all.find(columnKey(edge.a())));
            group.add(all.find(columnKey(edge.b())));
        }
        for (String pillar : touched) {
            for (MazeEdge edge : pillar.equals("x") ? scene.xEdges() : scene.zEdges()) {
                group.add(all.find(new Key(pillar, edge.a())));
                group.add(all.find(new Key(pillar, edge.b())));
            }
        }
        int target = 1;
        if (columnShape == PillarShape.BRIDGE) {
            assertEquals(1, touched.size(), label + ": the column Bridge does not touch exactly one horizontal pillar");
            target += scene.x().rings() - 1;
        } else {
            assertEquals(0, touched.size(), label + ": a column touches a horizontal pillar");
        }
        assertEquals(target, group.size(), label + ": the column group");
    }

    @Test
    void columnSceneMeetsThePrototypeResults() {
        for (PillarShape horizontal : PillarShape.values()) {
            for (PillarShape column : PillarShape.values()) {
                for (WebPlane plane : WebPlane.values()) {
                    for (long seed = 0; seed < 4; seed++) {
                        for (int[] signs : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                            Scene scene = scene(horizontal, column, plane, seed, signs[0], signs[1]);
                            checkScene(scene, horizontal, column);
                            if (column == PillarShape.BRIDGE) {
                                // Web plane X matches the X junction, so the far plate touches the Z pillar.
                                String expected = plane == WebPlane.X ? "z" : "x";
                                boolean found = scene.columnEdges().stream().flatMap(edge -> Stream.of(edge.a(), edge.b()))
                                        .filter(MazeNode.Foreign.class::isInstance)
                                        .allMatch(node -> ((MazeNode.Foreign) node).owner() == (expected.equals("z") ? Z_BOX_Y : X_BOX_Y));
                                assertTrue(found, "The column Bridge touches the wrong horizontal pillar for web plane " + plane);
                            }
                        }
                    }
                }
            }
        }
    }

}
