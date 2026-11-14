package us.drullk.aerolite.world.island;

import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the polyhedra of a weighted lattice. Each shape is convex and holds its site.
 * Each face lies on a wall plane. Two neighbors share each face without gap or overlap.
 * A lost cell gives no shape.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class WeightedCellGeometryTest {

    private static final int SIZE = 256;
    private static final int HEIGHT = 128;
    /** The plane tolerance in blocks. */
    private static final double TOLERANCE = 1.0E-5;
    private static final VoronoiLattice LATTICE = new VoronoiLattice(11L, SIZE, HEIGHT, 0, 0.8, (double) SIZE / HEIGHT,
            (ix, iz) -> (RandomSupport.mixStafford13(RandomSupport.mixStafford13(ix) ^ iz) >>> 11) * 0x1.0p-53, 0.0, 1.0);

    /** The shapes of the cells of the rows -2 to 2 in a block of 7 by 7 boxes. A lost cell maps to null. */
    private static Map<VoronoiLattice.Cell, CellGeometry.Shape> shapes() {
        Map<VoronoiLattice.Cell, CellGeometry.Shape> shapes = new HashMap<>();
        for (int ix = -3; ix <= 3; ix++) {
            for (int iy = -2; iy <= 2; iy++) {
                for (int iz = -3; iz <= 3; iz++) {
                    VoronoiLattice.Cell cell = LATTICE.cell(ix, iy, iz);
                    shapes.put(cell, CellGeometry.shape(LATTICE, cell));
                }
            }
        }
        return shapes;
    }

    @Test
    void lostCellsGiveNoShape() {
        int lost = 0;
        for (Map.Entry<VoronoiLattice.Cell, CellGeometry.Shape> entry : shapes().entrySet()) {
            if (LATTICE.lost(entry.getKey())) {
                lost++;
                assertNull(entry.getValue(), "A lost cell has a shape");
            } else {
                assertNotNull(entry.getValue(), "A cell that is not lost has no shape");
            }
        }
        assertTrue(lost > 0, "The lattice has no lost cell");
    }

    @Test
    void shapesAreConvexHoldTheirSitesAndLieOnTheWalls() {
        int faces = 0;
        for (CellGeometry.Shape shape : shapes().values()) {
            if (shape == null) {
                continue;
            }
            VoronoiLattice.Cell cell = shape.cell();
            for (CellGeometry.Face face : shape.faces()) {
                assertNotNull(face.neighbor(), "A face of the start box stays");
                assertTrue(face.distance(shape.site()) < 0, "The site lies outside a face");
                for (Vec3 vertex : shape.vertices()) {
                    assertTrue(face.distance(vertex) <= TOLERANCE, "A vertex lies outside a face, so the shape is not convex");
                }
                for (Vec3 vertex : face.vertices()) {
                    // On a wall, the two cells have equal power rule values.
                    double own = LATTICE.powerValue(cell, vertex.x, vertex.y, vertex.z);
                    double across = LATTICE.powerValue(face.neighbor(), vertex.x, vertex.y, vertex.z);
                    assertEquals(own, across, TOLERANCE * SIZE, "A face vertex is off the wall plane");
                    // No cell has a smaller power rule value, so the vertex is on the cell.
                    VoronoiLattice.Cell owner = LATTICE.nearest(vertex.x, vertex.y, vertex.z);
                    assertEquals(own, LATTICE.powerValue(owner, vertex.x, vertex.y, vertex.z), TOLERANCE * SIZE, "A face vertex is outside the cell");
                }
                faces++;
            }
        }
        assertTrue(faces > 500, faces + " faces");
    }

    @Test
    void neighborShapesShareFaces() {
        Map<VoronoiLattice.Cell, CellGeometry.Shape> shapes = shapes();
        int shared = 0;
        for (CellGeometry.Shape shape : shapes.values()) {
            if (shape == null) {
                continue;
            }
            for (CellGeometry.Face face : shape.faces()) {
                CellGeometry.Shape other = shapes.get(face.neighbor());
                if (other == null) {
                    // A face toward a cell outside the block, or toward a lost cell with space.
                    assertTrue(!shapes.containsKey(face.neighbor()) || LATTICE.lost(face.neighbor()), "A neighbor has no shape");
                    continue;
                }
                CellGeometry.Face back = other.faces().stream()
                        .filter(candidate -> shape.cell().equals(candidate.neighbor()))
                        .findFirst().orElseThrow(() -> new AssertionError("The neighbor has no face back to the cell"));
                List<Vec3> mine = sorted(face.vertices());
                List<Vec3> theirs = sorted(back.vertices());
                assertEquals(mine.size(), theirs.size());
                for (int i = 0; i < mine.size(); i++) {
                    assertTrue(mine.get(i).distanceTo(theirs.get(i)) < 1.0E-4, mine.get(i) + " and " + theirs.get(i));
                }
                shared++;
            }
        }
        assertTrue(shared > 200, shared + " shared faces");
    }

    @Test
    void samplePointsLieInExactlyTheShapeOfTheirOwner() {
        Map<VoronoiLattice.Cell, CellGeometry.Shape> shapes = shapes();
        Random random = new Random(13);
        int inside = 0;
        int inLost = 0;
        for (int i = 0; i < 3000; i++) {
            Vec3 point = new Vec3((random.nextDouble() * 2 - 1) * SIZE, (random.nextDouble() * 2 - 0.5) * HEIGHT, (random.nextDouble() * 2 - 1) * SIZE);
            VoronoiLattice.Cell owner = LATTICE.nearest(point.x, point.y, point.z);
            assertTrue(shapes.containsKey(owner), "The sample left the block of shapes");
            List<VoronoiLattice.Cell> holders = new ArrayList<>();
            boolean nearFace = false;
            for (CellGeometry.Shape shape : shapes.values()) {
                if (shape == null) {
                    continue;
                }
                double largest = Double.NEGATIVE_INFINITY;
                for (CellGeometry.Face face : shape.faces()) {
                    largest = Math.max(largest, face.distance(point));
                }
                if (Math.abs(largest) < 1.0E-6) {
                    nearFace = true;
                } else if (largest < 0) {
                    holders.add(shape.cell());
                }
            }
            if (nearFace) {
                continue;
            }
            if (LATTICE.lost(owner)) {
                inLost++;
                assertEquals(List.of(), holders, "A point of a lost cell lies in a shape");
            } else {
                inside++;
                assertEquals(List.of(owner), holders, "The point lies in another set of shapes than the shape of its owner");
            }
        }
        assertTrue(inside > 2000, inside + " points in shapes");
        System.out.println("Weighted geometry: " + inside + " points in the shape of their owner, " + inLost + " points in lost cells");
    }

    private static List<Vec3> sorted(List<Vec3> points) {
        List<Vec3> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.<Vec3>comparingDouble(point -> Math.round(point.x * 100))
                .thenComparingDouble(point -> Math.round(point.y * 100))
                .thenComparingDouble(point -> Math.round(point.z * 100)));
        return sorted;
    }

}
