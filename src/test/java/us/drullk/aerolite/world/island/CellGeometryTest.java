package us.drullk.aerolite.world.island;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the cell polyhedra: the boxes of a lattice without jitter, the shared faces, the distinct edges, the vertex distances, the gridlines, the edge distance, the ray test and the chunk overlap.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-12_19-19_6eca7213-840e-4a42-ba65-011a09997226.md}.
 */
class CellGeometryTest {

    private static final double TOLERANCE = 1.0E-6;
    private static final VoronoiLattice BOXES = new VoronoiLattice(7L, 256, 128, 0, 0);
    private static final VoronoiLattice JITTERED = new VoronoiLattice(7L, 256, 128, 0, 0.8);

    @Test
    void latticeWithoutJitterGivesBoxes() {
        for (int ix = -2; ix <= 2; ix++) {
            for (int iz = -2; iz <= 2; iz++) {
                CellGeometry.Shape shape = CellGeometry.shape(BOXES, BOXES.cell(ix, 0, iz));
                assertEquals(6, shape.faces().size());
                assertEquals(8, shape.vertices().size());
                assertEquals(12, shape.edges().size());
                for (Vec3 vertex : shape.vertices()) {
                    assertTrue(near(vertex.x, ix * 256) || near(vertex.x, (ix + 1) * 256), "x " + vertex.x);
                    assertTrue(near(vertex.y, 0) || near(vertex.y, 128), "y " + vertex.y);
                    assertTrue(near(vertex.z, iz * 256) || near(vertex.z, (iz + 1) * 256), "z " + vertex.z);
                }
            }
        }
    }

    @Test
    void neighborFacesCoincide() {
        for (int ix = -1; ix <= 1; ix++) {
            for (int iz = -1; iz <= 1; iz++) {
                VoronoiLattice.Cell cell = JITTERED.cell(ix, 0, iz);
                CellGeometry.Shape shape = CellGeometry.shape(JITTERED, cell);
                for (CellGeometry.Face face : shape.faces()) {
                    VoronoiLattice.Cell neighbor = face.neighbor();
                    assertNotNull(neighbor, "Every face of a clipped cell faces a neighbor");
                    CellGeometry.Shape other = CellGeometry.shape(JITTERED, neighbor);
                    CellGeometry.Face back = other.faces().stream()
                            .filter(candidate -> candidate.neighbor() != null && candidate.neighbor().equals(cell))
                            .findFirst().orElseThrow(() -> new AssertionError("The neighbor has no face back to the cell"));
                    assertEquals(sorted(face.vertices()).size(), sorted(back.vertices()).size());
                    List<Vec3> mine = sorted(face.vertices());
                    List<Vec3> theirs = sorted(back.vertices());
                    for (int i = 0; i < mine.size(); i++) {
                        assertTrue(mine.get(i).distanceTo(theirs.get(i)) < 1.0E-4, mine.get(i) + " and " + theirs.get(i));
                    }
                }
            }
        }
    }

    @Test
    void boxEdgesAppearOnceWithTheirOwners() {
        List<CellGeometry.Shape> shapes = shapes(BOXES);
        List<CellGeometry.CellEdge> edges = CellGeometry.distinctEdges(shapes);
        // 16 vertical edges, and 24 horizontal edges on each of the two levels.
        assertEquals(64, edges.size());
        int[] byOwners = new int[5];
        int[] verticalByOwners = new int[5];
        for (CellGeometry.CellEdge edge : edges) {
            byOwners[edge.owners().size()]++;
            if (!near(edge.segment().from().y, edge.segment().to().y)) {
                verticalByOwners[edge.owners().size()]++;
            }
        }
        // The vertical edges: 4 at the corners, 8 on the border, 4 inside. The horizontal edges: 24 on the border, 24 inside.
        assertEquals(List.of(0, 4, 8, 0, 4), Arrays.stream(verticalByOwners).boxed().toList());
        assertEquals(List.of(0, 28, 32, 0, 4), Arrays.stream(byOwners).boxed().toList());
    }

    @Test
    void jitteredSharedEdgesAppearOnceWithTheirOwners() {
        List<CellGeometry.Shape> shapes = shapes(JITTERED);
        Set<VoronoiLattice.Cell> drawn = new HashSet<>();
        int sum = 0;
        for (CellGeometry.Shape shape : shapes) {
            drawn.add(shape.cell());
            sum += shape.edges().size();
        }
        List<CellGeometry.CellEdge> edges = CellGeometry.distinctEdges(shapes);
        assertTrue(edges.size() < sum, edges.size() + " of " + sum);
        int owners = 0;
        for (int i = 0; i < edges.size(); i++) {
            List<VoronoiLattice.Cell> cells = edges.get(i).owners();
            owners += cells.size();
            assertEquals(cells.size(), new HashSet<>(cells).size(), "An owner repeats");
            for (int j = i + 1; j < edges.size(); j++) {
                assertTrue(!sameEdge(edges.get(i).segment(), edges.get(j).segment()), "Two copies of " + edges.get(i).segment());
            }
        }
        assertEquals(sum, owners);
        boolean shared = false;
        for (CellGeometry.Shape shape : shapes) {
            for (CellGeometry.Face face : shape.faces()) {
                List<Vec3> polygon = face.vertices();
                for (int i = 0; i < polygon.size(); i++) {
                    CellGeometry.Segment edge = new CellGeometry.Segment(polygon.get(i), polygon.get((i + 1) % polygon.size()));
                    List<CellGeometry.CellEdge> found = edges.stream().filter(candidate -> sameEdge(candidate.segment(), edge)).toList();
                    assertEquals(1, found.size());
                    assertTrue(found.getFirst().owners().contains(shape.cell()), "The edge lacks its cell");
                    if (drawn.contains(face.neighbor())) {
                        shared = true;
                        assertTrue(found.getFirst().owners().contains(face.neighbor()), "The edge lacks the cell across its face");
                    }
                }
            }
        }
        assertTrue(shared, "No drawn cell has a drawn neighbor");
    }

    @Test
    void verticesAreEquidistantFromTheirSites() {
        for (int ix = -2; ix <= 2; ix++) {
            for (int iz = -2; iz <= 2; iz++) {
                VoronoiLattice.Cell cell = JITTERED.cell(ix, 0, iz);
                CellGeometry.Shape shape = CellGeometry.shape(JITTERED, cell);
                for (Vec3 vertex : shape.vertices()) {
                    double own = vertex.distanceTo(shape.site());
                    List<VoronoiLattice.Cell> defining = new ArrayList<>();
                    for (CellGeometry.Face face : shape.faces()) {
                        if (Math.abs(face.distance(vertex)) < 1.0E-5) {
                            defining.add(face.neighbor());
                        }
                    }
                    assertTrue(defining.size() >= 3, "A vertex lies on " + defining.size() + " faces");
                    for (VoronoiLattice.Cell neighbor : defining) {
                        double distance = vertex.distanceTo(new Vec3(neighbor.siteX(), neighbor.siteY(), neighbor.siteZ()));
                        assertEquals(own, distance, 1.0E-5);
                    }
                    // No site is nearer than the own site, so the vertex is on the cell.
                    VoronoiLattice.Cell nearest = JITTERED.nearest(vertex.x, vertex.y, vertex.z);
                    double nearestDistance = vertex.distanceTo(new Vec3(nearest.siteX(), nearest.siteY(), nearest.siteZ()));
                    assertEquals(own, nearestDistance, 1.0E-5);
                }
            }
        }
    }

    @Test
    void boxFaceGridlinesAreTheSectionLines() {
        CellGeometry.Shape shape = CellGeometry.shape(BOXES, BOXES.cell(0, 0, 0));
        CellGeometry.Face top = shape.faces().stream().filter(face -> face.normal().y > 0.99).findFirst().orElseThrow();
        // The top face spans 256 blocks on X and Z, so 15 inner lines run on each axis.
        assertEquals(30, top.gridlines().size());
        Set<Double> xLines = new HashSet<>();
        Set<Double> zLines = new HashSet<>();
        for (CellGeometry.Segment line : top.gridlines()) {
            assertTrue(near(line.from().y, 128) && near(line.to().y, 128));
            if (near(line.from().x, line.to().x)) {
                xLines.add((double) Math.round(line.from().x));
                assertEquals(256, Math.abs(line.to().z - line.from().z), TOLERANCE);
            } else {
                assertTrue(near(line.from().z, line.to().z));
                zLines.add((double) Math.round(line.from().z));
                assertEquals(256, Math.abs(line.to().x - line.from().x), TOLERANCE);
            }
        }
        for (int step = 1; step < 16; step++) {
            assertTrue(xLines.contains(step * 16.0), "x line " + step * 16);
            assertTrue(zLines.contains(step * 16.0), "z line " + step * 16);
        }
        CellGeometry.Face side = shape.faces().stream().filter(face -> face.normal().x > 0.99).findFirst().orElseThrow();
        // The side face spans 256 blocks on Z and 128 on Y: 15 lines across Z and 7 across Y.
        assertEquals(22, side.gridlines().size());
    }

    @Test
    void boxFaceEdgeDistanceIsTheDistanceToTheNearestSide() {
        CellGeometry.Shape shape = CellGeometry.shape(BOXES, BOXES.cell(0, 0, 0));
        // The top face spans 0 to 256 on X and Z at Y 128.
        CellGeometry.Face top = shape.faces().stream().filter(face -> face.normal().y > 0.99).findFirst().orElseThrow();
        assertEquals(128, top.edgeDistance(new Vec3(128, 128, 128)), TOLERANCE);
        assertEquals(10, top.edgeDistance(new Vec3(10, 128, 100)), TOLERANCE);
        assertEquals(20, top.edgeDistance(new Vec3(200, 128, 236)), TOLERANCE);
        assertEquals(0, top.edgeDistance(new Vec3(256, 128, 77)), TOLERANCE);
        assertEquals(0, top.edgeDistance(new Vec3(0, 128, 0)), TOLERANCE);
        // The side face spans 0 to 128 on Y and 0 to 256 on Z at X 256.
        CellGeometry.Face side = shape.faces().stream().filter(face -> face.normal().x > 0.99).findFirst().orElseThrow();
        assertEquals(64, side.edgeDistance(new Vec3(256, 64, 128)), TOLERANCE);
        assertEquals(28, side.edgeDistance(new Vec3(256, 100, 128)), TOLERANCE);
        for (CellGeometry.Segment line : top.gridlines()) {
            assertEquals(0, top.edgeDistance(line.from()), TOLERANCE);
            assertEquals(0, top.edgeDistance(line.to()), TOLERANCE);
        }
    }

    @Test
    void tiltedFaceEdgeDistanceIsMeasuredInTheFacePlane() {
        // A rectangle in the plane x + y = 8: 128 blocks across, Z from 0 to 64.
        double half = 32 * Math.sqrt(2);
        Vec3 normal = new Vec3(1, 1, 0).normalize();
        Vec3 across = new Vec3(1, -1, 0).normalize();
        Vec3 corner = new Vec3(8 - half, half, 0);
        List<Vec3> rectangle = List.of(corner, new Vec3(8 + half, -half, 0), new Vec3(8 + half, -half, 64), new Vec3(8 - half, half, 64));
        CellGeometry.Face face = new CellGeometry.Face(rectangle, normal, normal.dot(corner), null, List.of());
        // The center is 32 from each long side and 64 from each short side.
        assertEquals(32, face.edgeDistance(corner.add(across.scale(64)).add(0, 0, 32)), TOLERANCE);
        assertEquals(5, face.edgeDistance(corner.add(across.scale(5)).add(0, 0, 32)), TOLERANCE);
        assertEquals(7, face.edgeDistance(corner.add(across.scale(100)).add(0, 0, 57)), TOLERANCE);
        assertEquals(0, face.edgeDistance(corner.add(across.scale(128)).add(0, 0, 13)), TOLERANCE);
        // A point outside the polygon measures to the nearest corner.
        assertEquals(5, face.edgeDistance(corner.add(across.scale(-3)).add(0, 0, -4)), TOLERANCE);
    }

    @Test
    void rayHitsTheNearestBox() {
        CellGeometry.Shape near = CellGeometry.shape(BOXES, BOXES.cell(0, 0, 0));
        CellGeometry.Shape far = CellGeometry.shape(BOXES, BOXES.cell(1, 0, 0));
        Vec3 origin = new Vec3(-100, 64, 128);
        Vec3 direction = new Vec3(1, 0, 0);
        assertEquals(100, near.raycast(origin, direction), TOLERANCE);
        assertEquals(356, far.raycast(origin, direction), TOLERANCE);
        assertSame(near, CellGeometry.nearestHit(List.of(far, near), origin, direction));
        assertEquals(Double.POSITIVE_INFINITY, near.raycast(origin, new Vec3(-1, 0, 0)));
        assertEquals(0, near.raycast(new Vec3(128, 64, 128), direction), TOLERANCE);
        assertNull(CellGeometry.nearestHit(List.of(far, near), new Vec3(-100, 500, 128), direction));
    }

    @Test
    void boxChunksAreItsChunkRange() {
        CellGeometry.Shape shape = CellGeometry.shape(BOXES, BOXES.cell(1, 0, -1));
        LongList expected = new LongArrayList();
        for (int x = 16; x < 32; x++) {
            for (int z = -16; z < 0; z++) {
                expected.add(ChunkPos.asLong(x, z));
            }
        }
        assertEquals(new HashSet<>(expected), new HashSet<>(shape.chunks()));
    }

    @Test
    void jitteredChunksHoldTheSiteChunk() {
        for (int ix = -2; ix <= 2; ix++) {
            VoronoiLattice.Cell cell = JITTERED.cell(ix, 0, 1);
            CellGeometry.Shape shape = CellGeometry.shape(JITTERED, cell);
            assertTrue(shape.chunks().contains(ChunkPos.asLong((int) Math.floor(cell.siteX() / 16), (int) Math.floor(cell.siteZ() / 16))));
        }
    }

    @Test
    void tiltedFaceGridlinesLieOnTheGridPlanes() {
        // A rectangle in the plane x + y = 8: 128 blocks across, Z from 0 to 64. No X line lies on a Y plane.
        double half = 32 * Math.sqrt(2);
        List<Vec3> rectangle = List.of(new Vec3(8 - half, half, 0), new Vec3(8 + half, -half, 0), new Vec3(8 + half, -half, 64), new Vec3(8 - half, half, 64));
        Vec3 normal = new Vec3(1, 1, 0).normalize();
        List<CellGeometry.Segment> lines = CellGeometry.gridlines(rectangle, normal);
        int[] families = new int[3];
        for (CellGeometry.Segment line : lines) {
            families[gridAxis(line)]++;
        }
        // X planes from -32 to 48, Y planes from -32 to 32, Z planes at 16, 32 and 48.
        assertEquals(6, families[0]);
        assertEquals(5, families[1]);
        assertEquals(3, families[2]);
        for (CellGeometry.Segment line : lines) {
            if (gridAxis(line) == 2) {
                assertEquals(128, line.from().distanceTo(line.to()), TOLERANCE);
            } else {
                assertEquals(64, Math.abs(line.to().z - line.from().z), TOLERANCE);
            }
        }
    }

    @Test
    void jitteredGridlinesLieOnTheGridPlanes() {
        for (int ix = -2; ix <= 2; ix++) {
            for (int iz = -2; iz <= 2; iz++) {
                CellGeometry.Shape shape = CellGeometry.shape(JITTERED, JITTERED.cell(ix, 0, iz));
                for (CellGeometry.Face face : shape.faces()) {
                    Set<List<Long>> seen = new HashSet<>();
                    for (CellGeometry.Segment line : face.gridlines()) {
                        int axis = gridAxis(line);
                        assertTrue(Math.abs(face.distance(line.from())) < 1.0E-6 && Math.abs(face.distance(line.to())) < 1.0E-6, "The line leaves the face");
                        assertTrue(seen.add(List.of((long) axis, Math.round(component(line.from(), axis)))), "Two lines on one plane");
                    }
                }
            }
        }
    }

    @Test
    void clipThroughVerticesKeepsTheBox() {
        // Without jitter, each diagonal bisector passes exactly through an edge or a vertex of the box.
        CellGeometry.Shape shape = CellGeometry.shape(BOXES, BOXES.cell(0, 0, 0));
        for (CellGeometry.Face face : shape.faces()) {
            assertEquals(4, face.vertices().size());
            assertEquals(4, new HashSet<>(face.vertices()).size());
        }
    }

    @Test
    void rayFromInsideAJitteredCellHitsIt() {
        CellGeometry.Shape shape = CellGeometry.shape(JITTERED, JITTERED.cell(0, 0, 0));
        CellGeometry.Shape other = CellGeometry.shape(JITTERED, JITTERED.cell(1, 0, 0));
        Vec3 origin = shape.site();
        Vec3 direction = other.site().subtract(origin);
        assertEquals(0, shape.raycast(origin, direction), TOLERANCE);
        double enter = other.raycast(origin, direction);
        assertTrue(enter > 0 && enter < 1, "enter " + enter);
        assertSame(shape, CellGeometry.nearestHit(List.of(other, shape), origin, direction));
    }

    @Test
    void jitteredChunksAreTheHullChunks() {
        for (int ix = -1; ix <= 1; ix++) {
            CellGeometry.Shape shape = CellGeometry.shape(JITTERED, JITTERED.cell(ix, 0, 0));
            Set<Long> chunks = new HashSet<>(shape.chunks());
            double minX = shape.vertices().stream().mapToDouble(Vec3::x).min().orElseThrow();
            double maxX = shape.vertices().stream().mapToDouble(Vec3::x).max().orElseThrow();
            double minZ = shape.vertices().stream().mapToDouble(Vec3::z).min().orElseThrow();
            double maxZ = shape.vertices().stream().mapToDouble(Vec3::z).max().orElseThrow();
            int bounds = 0;
            for (int x = (int) Math.floor(minX / 16); x <= (int) Math.floor(maxX / 16); x++) {
                for (int z = (int) Math.floor(minZ / 16); z <= (int) Math.floor(maxZ / 16); z++) {
                    bounds++;
                    // A chunk overlaps the column when a sample point inside the chunk lies in the cell at some height.
                    boolean sampled = false;
                    for (int sx = 1; sx < 16 && !sampled; sx += 2) {
                        for (int sz = 1; sz < 16 && !sampled; sz += 2) {
                            sampled = inColumn(shape, x * 16 + sx + 0.5, z * 16 + sz + 0.5);
                        }
                    }
                    if (sampled) {
                        assertTrue(chunks.contains(ChunkPos.asLong(x, z)), "chunk " + x + " " + z);
                    }
                }
            }
            // The hull is no rectangle, so its bounds hold chunks outside it.
            assertTrue(chunks.size() < bounds, chunks.size() + " of " + bounds);
        }
    }

    /** The shapes of the 3 by 3 cells of the row 0 around the origin. */
    private static List<CellGeometry.Shape> shapes(VoronoiLattice lattice) {
        List<CellGeometry.Shape> shapes = new ArrayList<>();
        for (int ix = -1; ix <= 1; ix++) {
            for (int iz = -1; iz <= 1; iz++) {
                shapes.add(CellGeometry.shape(lattice, lattice.cell(ix, 0, iz)));
            }
        }
        return shapes;
    }

    /** True when the two segments have the same end points, in either direction. */
    private static boolean sameEdge(CellGeometry.Segment a, CellGeometry.Segment b) {
        return a.from().distanceTo(b.from()) < 1.0E-4 && a.to().distanceTo(b.to()) < 1.0E-4
                || a.from().distanceTo(b.to()) < 1.0E-4 && a.to().distanceTo(b.from()) < 1.0E-4;
    }

    /** True when the vertical line at X and Z crosses the polyhedron. */
    private static boolean inColumn(CellGeometry.Shape shape, double x, double z) {
        return shape.raycast(new Vec3(x, -10000, z), new Vec3(0, 1, 0)) < Double.POSITIVE_INFINITY;
    }

    /** The axis of the grid plane that holds both ends of the line. */
    private static int gridAxis(CellGeometry.Segment line) {
        for (int axis = 0; axis < 3; axis++) {
            double from = component(line.from(), axis);
            double plane = Math.round(from / CellGeometry.GRID_SPACING) * (double) CellGeometry.GRID_SPACING;
            if (near(from, plane) && near(component(line.to(), axis), plane)) {
                return axis;
            }
        }
        throw new AssertionError(line + " lies on no grid plane");
    }

    private static double component(Vec3 point, int axis) {
        return axis == 0 ? point.x : axis == 1 ? point.y : point.z;
    }

    private static List<Vec3> sorted(List<Vec3> points) {
        List<Vec3> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.<Vec3>comparingDouble(point -> Math.round(point.x * 100))
                .thenComparingDouble(point -> Math.round(point.y * 100))
                .thenComparingDouble(point -> Math.round(point.z * 100)));
        return sorted;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < TOLERANCE;
    }

}
