package us.drullk.aerolite.world.island;

import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the kept walls of a lattice cell against every wall candidate.
 * The gauge inside and outside the cell and the cell radius are equal bit for bit.
 * So are the lost flag and the wall distance at a point of the cell.
 * The shape of a cell equals the shape that every candidate clips.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class KeptWallsTest {

    private static final int SIZE = 256;
    private static final int POINTS = 40;
    /** A bound on the largest kept wall count of a cell. */
    private static final int KEPT_BOUND = 128;

    /** One lattice of the sweep. The weight range -1 gives each cell of the row 0 the weight 0 or 1. */
    private record Config(double weightMin, double weightMax, double jitter, int cellHeight, double yScale) {

        VoronoiLattice lattice() {
            boolean bounds = this.weightMin < 0;
            VoronoiLattice.CellWeights weights = bounds
                    ? (ix, iz) -> (mix(ix, iz) & 1) == 0 ? 0.0 : 1.0
                    : (ix, iz) -> this.weightMin + (this.weightMax - this.weightMin) * ((mix(ix, iz) >>> 11) * 0x1.0p-53);
            return new VoronoiLattice(41L, SIZE, this.cellHeight, -32, this.jitter, this.yScale, weights, bounds ? 0.0 : this.weightMin, bounds ? 1.0 : this.weightMax);
        }

        @Override
        public String toString() {
            return (this.weightMin < 0 ? "0 or 1" : this.weightMin + "-" + this.weightMax) + ", jitter " + this.jitter + ", height " + this.cellHeight + ", Y scale " + this.yScale;
        }
    }

    private static long mix(int ix, int iz) {
        return RandomSupport.mixStafford13(RandomSupport.mixStafford13(0x77L ^ ix) ^ iz);
    }

    private static List<Config> sweep() {
        List<Config> configs = new ArrayList<>();
        for (double[] range : new double[][] {{0.5, 0.5}, {0.0, 1.0}, {-1, -1}}) {
            for (double jitter : new double[] {0.0, 0.8, 1.0}) {
                for (int height : new int[] {32, 128, 1024}) {
                    configs.add(new Config(range[0], range[1], jitter, height, (double) SIZE / height));
                    configs.add(new Config(range[0], range[1], jitter, height, 1.0));
                }
            }
        }
        return configs;
    }

    @Test
    void keptWallsGiveTheValuesOfEveryCandidate() {
        StringBuilder text = new StringBuilder("Kept walls per cell (mean, largest, candidates):");
        int largest = 0;
        for (Config config : sweep()) {
            VoronoiLattice lattice = config.lattice();
            Random random = new Random(config.hashCode());
            int cells = 0;
            int kept = 0;
            int most = 0;
            for (int i = 0; i < POINTS; i++) {
                double x = random.nextDouble() * 4 * SIZE;
                double y = -32 + (random.nextDouble() * 3 - 1) * config.cellHeight();
                double z = random.nextDouble() * 4 * SIZE;
                VoronoiLattice.Cell cell = lattice.nearest(x, y, z);
                PowerRule.Walls pruned = lattice.walls(cell);
                PowerRule.Walls all = allWalls(lattice, cell);
                String where = config + ", " + cell;
                assertEquals(all.lost(), pruned.lost(), where + ": the lost flag");
                if (all.lost()) {
                    continue;
                }
                cells++;
                kept += pruned.count();
                most = Math.max(most, pruned.count());
                double k = lattice.yScale();
                same(all.cellRadius(), pruned.cellRadius(), where + ": the cell radius");
                same(all.gauge(x, y * k, z), pruned.gauge(x, y * k, z), where + ": the gauge in the cell");
                same(all.wallDistance(x, y * k, z), pruned.wallDistance(x, y * k, z), where + ": the wall distance in the cell");
                same(all.gauge(cell.siteX(), cell.siteY() * k, cell.siteZ()), pruned.gauge(cell.siteX(), cell.siteY() * k, cell.siteZ()), where + ": the gauge at the site");
                for (int outside = 0; outside < 4; outside++) {
                    double ox = x + (random.nextDouble() - 0.5) * 4 * SIZE;
                    double oy = (y + (random.nextDouble() - 0.5) * 4 * config.cellHeight()) * k;
                    double oz = z + (random.nextDouble() - 0.5) * 4 * SIZE;
                    same(all.gauge(ox, oy, oz), pruned.gauge(ox, oy, oz), where + ": the gauge outside the cell");
                }
            }
            assertTrue(cells > 0, config + ": no cell that is not lost");
            int candidates = lattice.wallOffsetCount();
            assertTrue(candidates < 300 || most <= candidates / 4, config + ": a cell keeps " + most + " of " + candidates + " candidates");
            largest = Math.max(largest, most);
            text.append(String.format("%n  %s: %.1f, %d, %d", config, (double) kept / cells, most, lattice.wallOffsetCount()));
        }
        System.out.println(text);
        assertTrue(largest <= KEPT_BOUND, "A cell keeps " + largest + " walls");
    }

    @Test
    void shapeEqualsTheShapeOfEveryCandidate() {
        int faces = 0;
        for (Config config : List.of(new Config(0.0, 1.0, 0.8, 128, 2.0), new Config(0.5, 0.5, 0.8, 128, 1.0), new Config(0.5, 0.5, 0.0, 128, 1.0),
                new Config(-1, -1, 1.0, 32, 8.0))) {
            VoronoiLattice lattice = config.lattice();
            for (int ix = 0; ix < 4; ix++) {
                for (int iz = 0; iz < 4; iz++) {
                    VoronoiLattice.Cell cell = lattice.cell(ix, 0, iz);
                    CellGeometry.Shape shape = CellGeometry.shape(lattice, cell);
                    if (lattice.lost(cell)) {
                        continue;
                    }
                    assertNotNull(shape);
                    List<CellGeometry.Face> reference = referenceFaces(lattice, cell);
                    assertEquals(reference.size(), shape.faces().size(), config + ", " + cell + ": the face count");
                    PowerRule.Walls kept = lattice.walls(cell);
                    for (CellGeometry.Face face : reference) {
                        CellGeometry.Face match = shape.faces().stream().filter(candidate -> face.neighbor().equals(candidate.neighbor())).findFirst()
                                .orElseThrow(() -> new AssertionError(config + ": the shape lacks the face toward " + face.neighbor()));
                        assertTrue(holds(lattice, cell, kept, face.neighbor()), config + ": no kept wall faces " + face.neighbor());
                        List<Vec3> mine = sorted(match.vertices());
                        List<Vec3> theirs = sorted(face.vertices());
                        assertEquals(theirs.size(), mine.size());
                        for (int i = 0; i < mine.size(); i++) {
                            assertTrue(mine.get(i).distanceTo(theirs.get(i)) < 1.0E-6, mine.get(i) + " and " + theirs.get(i));
                        }
                        faces++;
                    }
                }
            }
        }
        assertTrue(faces > 300, faces + " faces");
    }

    @Test
    void cellThinnerThanTheClipToleranceKeepsEveryCandidate() {
        for (double delta : new double[] {1.0E-6, 1.0E-9, 1.0E-12}) {
            // Four neighbors almost win the site, so the cell is a column thinner than the clip tolerance.
            VoronoiLattice lattice = new VoronoiLattice(3L, SIZE, 128, 0, 0.0, 2.0,
                    (ix, iz) -> ix == 0 && iz == 0 ? 0.0 : Math.abs(ix) + Math.abs(iz) == 1 ? 1.0 - delta : 0.5, 0.0, 1.0);
            VoronoiLattice.Cell cell = lattice.cell(0, 0, 0);
            PowerRule.Walls pruned = lattice.walls(cell);
            PowerRule.Walls all = allWalls(lattice, cell);
            String where = "delta " + delta;
            assertEquals(false, pruned.lost(), where + ": the cell is lost");
            double k = lattice.yScale();
            same(all.cellRadius(), pruned.cellRadius(), where + ": the cell radius");
            same(all.gauge(cell.siteX() + 1.0E-12, cell.siteY() * k, cell.siteZ()), pruned.gauge(cell.siteX() + 1.0E-12, cell.siteY() * k, cell.siteZ()), where + ": the gauge by the site");
            same(all.gauge(cell.siteX() + 1, cell.siteY() * k, cell.siteZ()), pruned.gauge(cell.siteX() + 1, cell.siteY() * k, cell.siteZ()), where + ": the gauge outside the cell");
            CellGeometry.Shape shape = CellGeometry.shape(lattice, cell);
            assertTrue(shape == null || shape.faces().size() >= 4, where + ": a shape with " + (shape == null ? 0 : shape.faces().size()) + " faces");
            if (shape != null) {
                assertEquals(Double.POSITIVE_INFINITY, shape.raycast(new Vec3(cell.siteX() + 5000, cell.siteY(), cell.siteZ()), new Vec3(1, 0, 0)), where + ": a ray away hits the shape");
            }
        }
    }

    /** True when a kept wall of the cell faces the neighbor. */
    private static boolean holds(VoronoiLattice lattice, VoronoiLattice.Cell cell, PowerRule.Walls kept, VoronoiLattice.Cell neighbor) {
        for (int i = 0; i < kept.count(); i++) {
            if (lattice.wallNeighbor(cell, i).equals(neighbor)) {
                return true;
            }
        }
        return false;
    }

    /** The faces of the polyhedron that every wall candidate clips, in the candidate sequence. */
    static List<CellGeometry.Face> referenceFaces(VoronoiLattice lattice, VoronoiLattice.Cell cell) {
        PowerRule.Walls all = allWalls(lattice, cell);
        List<VoronoiLattice.Cell> neighbors = new ArrayList<>();
        lattice.visitWallOffsets((dx, dy, dz) -> neighbors.add(lattice.cell(cell.ix() + dx, cell.iy() + dy, cell.iz() + dz)));
        CellGeometry.Clipper clipper = new CellGeometry.Clipper(new Vec3(cell.siteX(), cell.siteY(), cell.siteZ()), lattice.winnerReach(), lattice.yScale());
        for (int i = 0; i < all.count(); i++) {
            clipper.clipWall(all.vx(i), all.vy(i), all.vz(i), all.h(i), all.norm(i), neighbors.get(i));
        }
        return clipper.faces();
    }

    /** The walls of the cell toward every wall candidate of the lattice, without the pruning. */
    static PowerRule.Walls allWalls(VoronoiLattice lattice, VoronoiLattice.Cell cell) {
        List<VoronoiLattice.Cell> others = new ArrayList<>();
        lattice.visitWallOffsets((dx, dy, dz) -> others.add(lattice.cell(cell.ix() + dx, cell.iy() + dy, cell.iz() + dz)));
        double[] sites = new double[3 * others.size()];
        double[] weights = new double[others.size()];
        for (int i = 0; i < others.size(); i++) {
            VoronoiLattice.Cell other = others.get(i);
            sites[3 * i] = other.siteX();
            sites[3 * i + 1] = other.siteY() * lattice.yScale();
            sites[3 * i + 2] = other.siteZ();
            weights[i] = lattice.cellWeight(other);
        }
        return PowerRule.walls(cell.siteX(), cell.siteY() * lattice.yScale(), cell.siteZ(), lattice.cellWeight(cell), sites, weights, others.size(), SIZE, lattice.yScale());
    }

    private static void same(double expected, double actual, String message) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static List<Vec3> sorted(List<Vec3> points) {
        List<Vec3> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.<Vec3>comparingDouble(point -> Math.round(point.x * 100))
                .thenComparingDouble(point -> Math.round(point.y * 100))
                .thenComparingDouble(point -> Math.round(point.z * 100)));
        return sorted;
    }

}
