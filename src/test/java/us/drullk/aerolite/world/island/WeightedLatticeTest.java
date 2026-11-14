package us.drullk.aerolite.world.island;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the seeded weighted lattice against a brute force search over a much wider reach.
 * The sweep covers four cell weight ranges, four jitter values and cell heights near and far from the cell size.
 * It also checks the reach table of the prototype and the cell weight rule.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class WeightedLatticeTest {

    private static final int SIZE = 256;
    private static final int LAYER_MIN_Y = -48;
    private static final int POINTS = 120;
    private static final double TOLERANCE = 1.0E-9;
    /** The extra boxes of the brute force search on each side. */
    private static final int EXTRA = 2;

    /** One lattice of the sweep. A weight range of -1 to -1 gives each cell of the row 0 the weight 0 or 1. */
    private record Config(double weightMin, double weightMax, double jitter, int cellHeight, double yScale) {

        boolean extreme() {
            return this.weightMin < 0;
        }

        VoronoiLattice lattice() {
            VoronoiLattice.CellWeights weights = this.extreme()
                    ? (ix, iz) -> (mix(ix, iz) & 1) == 0 ? 0.0 : 1.0
                    : (ix, iz) -> this.weightMin + (this.weightMax - this.weightMin) * ((mix(ix, iz) >>> 11) * 0x1.0p-53);
            return new VoronoiLattice(20261113L, SIZE, this.cellHeight, LAYER_MIN_Y, this.jitter, this.yScale, weights,
                    this.extreme() ? 0.0 : this.weightMin, this.extreme() ? 1.0 : this.weightMax);
        }

        @Override
        public String toString() {
            return (this.extreme() ? "0 or 1" : this.weightMin + "-" + this.weightMax) + ", jitter " + this.jitter + ", height " + this.cellHeight + ", Y scale " + this.yScale;
        }
    }

    private static long mix(int ix, int iz) {
        return RandomSupport.mixStafford13(RandomSupport.mixStafford13(0xC3L ^ ix) ^ iz);
    }

    private static List<Config> sweep() {
        List<Config> configs = new ArrayList<>();
        double[][] ranges = {{0.5, 0.5}, {0.25, 0.75}, {0.0, 1.0}, {-1, -1}};
        for (double[] range : ranges) {
            for (double jitter : new double[] {0.0, 0.5, 0.8, 1.0}) {
                for (int height : new int[] {128, 32, 1024}) {
                    configs.add(new Config(range[0], range[1], jitter, height, (double) SIZE / height));
                }
                configs.add(new Config(range[0], range[1], jitter, 128, 1.0));
            }
        }
        return configs;
    }

    private static double[] randomPoint(Random random, VoronoiLattice lattice) {
        return new double[] {
                random.nextDouble() * 6 * SIZE,
                LAYER_MIN_Y + (random.nextDouble() * 4 - 1.5) * lattice.cellHeight(),
                random.nextDouble() * 6 * SIZE
        };
    }

    @Test
    void ownerMatchesTheBruteForceOwner() {
        for (Config config : sweep()) {
            VoronoiLattice lattice = config.lattice();
            Random random = new Random(config.hashCode());
            for (int i = 0; i < POINTS; i++) {
                double[] p = randomPoint(random, lattice);
                VoronoiLattice.Cell owner = lattice.nearest(p[0], p[1], p[2]);
                VoronoiLattice.Cell brute = bruteOwner(lattice, p);
                double difference = Math.abs(lattice.powerValue(owner, p[0], p[1], p[2]) - lattice.powerValue(brute, p[0], p[1], p[2]));
                assertTrue(owner.equals(brute) || difference <= TOLERANCE * SIZE * SIZE, config + ": the owner " + owner + " is not the brute force owner " + brute);
            }
        }
    }

    @Test
    void valuesMatchTheWideWallSearch() {
        int lost = 0;
        int checked = 0;
        for (Config config : sweep()) {
            VoronoiLattice lattice = config.lattice();
            Random random = new Random(config.hashCode() + 1);
            for (int i = 0; i < POINTS; i++) {
                double[] p = randomPoint(random, lattice);
                VoronoiLattice.Cell cell = lattice.nearest(p[0], p[1], p[2]);
                PowerRule.Walls wide = wideWalls(lattice, cell);
                VoronoiLattice.CellValues values = lattice.values(cell, p[0], p[1], p[2]);
                assertEquals(wide.lost(), values.lost(), config + ": the lost flag of " + cell);
                if (wide.lost()) {
                    lost++;
                    continue;
                }
                checked++;
                double py = p[1] * lattice.yScale();
                near(wide.gauge(p[0], py, p[2]), values.gauge(), config + ": the gauge");
                near(wide.wallDistance(p[0], py, p[2]), values.wallDistance(), config + ": the wall distance");
                near(wide.cellRadius(), values.cellRadius(), config + ": the cell radius");
                assertTrue(values.gauge() <= 1 + TOLERANCE && values.wallDistance() >= -TOLERANCE, config + ": the point lies outside its cell");
            }
            for (int ix = 0; ix < 6; ix++) {
                for (int iz = 0; iz < 6; iz++) {
                    VoronoiLattice.Cell cell = lattice.cell(ix, 0, iz);
                    assertEquals(wideWalls(lattice, cell).lost(), lattice.lost(cell), config + ": the lost flag of " + cell);
                    if (!config.extreme() && config.weightMin() == config.weightMax()) {
                        assertFalse(lattice.lost(cell), config + ": equal weights lose a cell");
                    }
                }
            }
        }
        assertTrue(lost > 0 && checked > 1000, lost + " lost owners, " + checked + " checked owners");
    }

    @Test
    void reachTableEqualsThePrototype() {
        // The prototype table: jitter, search reach, search offsets, wall reach, wall offsets. Weights 0 to 1 and scaled cubes.
        double[][] table = {{0.0, 1, 27, 2, 80}, {0.5, 2, 81, 3, 274}, {0.8, 2, 117, 4, 484}, {1.0, 3, 179, 5, 666}};
        for (double[] row : table) {
            VoronoiLattice lattice = new VoronoiLattice(1L, SIZE, 128, 0, row[0], 2.0, (ix, iz) -> 0.0, 0.0, 1.0);
            assertEquals((int) row[1], lattice.searchReachXZ());
            assertEquals((int) row[1], lattice.searchReachY());
            assertEquals((int) row[2], lattice.searchOffsetCount());
            assertEquals((int) row[3], lattice.wallReachXZ());
            assertEquals((int) row[4], lattice.wallOffsetCount());
        }
        StringBuilder text = new StringBuilder("Reach table, scaled cubes (search reach / offsets, wall reach / offsets):");
        for (double jitter : new double[] {0.0, 0.5, 0.8, 1.0}) {
            for (double[] range : new double[][] {{0.5, 0.5}, {0.25, 0.75}, {0.0, 1.0}}) {
                VoronoiLattice lattice = new VoronoiLattice(1L, SIZE, 128, 0, jitter, 2.0, (ix, iz) -> range[0], range[0], range[1]);
                text.append(String.format("%n  jitter %.1f, spread %.1f: R/S %.4f, search %d / %d, wall %d / %d", jitter, range[1] - range[0],
                        lattice.winnerReach() / SIZE, lattice.searchReachXZ(), lattice.searchOffsetCount(), lattice.wallReachXZ(), lattice.wallOffsetCount()));
            }
        }
        VoronoiLattice plain = new VoronoiLattice(1L, SIZE, 128, 0, 0.8);
        text.append(String.format("%n  VoronoiIslands mode, 256 by 128, jitter 0.8: search %d %d / %d, wall %d %d / %d", plain.searchReachXZ(), plain.searchReachY(),
                plain.searchOffsetCount(), plain.wallReachXZ(), plain.wallReachY(), plain.wallOffsetCount()));
        System.out.println(text);
    }

    @Test
    void tieTakesTheLeastIndex() {
        // A guard of -1 forces the loop path of the owner search.
        for (int guard : new int[] {VoronoiLattice.SEARCH_GUARD, -1}) {
            // Without jitter, a box corner is at an equal distance from the 8 sites around it.
            VoronoiLattice plain = new VoronoiLattice(5L, SIZE, 128, 0, 0.0, 1.0, (ix, iz) -> 0.5, 0.5, 0.5, guard, guard);
            assertEquals(guard > 0, plain.searchCached());
            assertEquals(plain.cell(0, 0, 0), plain.nearest(SIZE, 128, SIZE));
            assertEquals(plain.cell(-1, -1, -1), plain.nearest(0, 0, 0));
            // Two row 0 cells with one cell weight tie on the plane between their sites.
            VoronoiLattice weighted = new VoronoiLattice(5L, SIZE, 128, 0, 0.0, 2.0, (ix, iz) -> 0.3, 0.3, 0.3, guard, guard);
            for (int pass = 0; pass < 2; pass++) {
                assertEquals(weighted.cell(0, 0, 0), weighted.nearest(SIZE, 64, 128));
                assertEquals(weighted.cell(0, 0, -1), weighted.nearest(128, 64, 0));
                assertEquals(weighted.cell(0, 0, 0), weighted.nearest(SIZE, 64, 64));
            }
            assertEquals(weighted.powerValue(weighted.cell(0, 0, 0), SIZE, 64, 128), weighted.powerValue(weighted.cell(1, 0, 0), SIZE, 64, 128));
        }
    }

    @Test
    void cellWeightIsClampedInTheRowZeroAndHalfElsewhere() {
        VoronoiLattice lattice = new VoronoiLattice(3L, SIZE, 128, 0, 0.5, 2.0, (ix, iz) -> ix < 0 ? -4.0 : 7.0, 0.2, 0.6);
        assertEquals(0.2, lattice.cellWeight(lattice.cell(-1, 0, 0)));
        assertEquals(0.6, lattice.cellWeight(lattice.cell(1, 0, 0)));
        assertEquals(0.5, lattice.cellWeight(lattice.cell(1, 1, 0)));
        assertEquals(0.5, lattice.cellWeight(lattice.cell(-1, -1, 0)));
        assertThrows(IllegalArgumentException.class, () -> new VoronoiLattice(3L, SIZE, 128, 0, 0.5, 2.0, (ix, iz) -> 0.5, 0.7, 0.6));
        assertThrows(IllegalArgumentException.class, () -> new VoronoiLattice(3L, SIZE, 128, 0, 0.5, 2.0, (ix, iz) -> 0.5, -0.1, 0.6));
        assertThrows(IllegalArgumentException.class, () -> new VoronoiLattice(3L, SIZE, 128, 0, 0.5, 0.0, (ix, iz) -> 0.5, 0.5, 0.5));
    }

    @Test
    void cellWeightThatIsNotANumberCountsAsHalf() {
        VoronoiLattice broken = new VoronoiLattice(3L, SIZE, 128, 0, 0.8, 2.0, (ix, iz) -> ix == 0 && iz == 0 ? Double.NaN : 0.95, 0.9, 1.0);
        VoronoiLattice half = new VoronoiLattice(3L, SIZE, 128, 0, 0.8, 2.0, (ix, iz) -> ix == 0 && iz == 0 ? 0.5 : 0.95, 0.5, 1.0);
        assertEquals(0.5, broken.cellWeight(broken.cell(0, 0, 0)));
        for (int ix = -1; ix <= 1; ix++) {
            for (int iz = -1; iz <= 1; iz++) {
                VoronoiLattice.Cell cell = broken.cell(ix, 0, iz);
                VoronoiLattice.CellValues values = broken.values(cell, cell.siteX() + 3, cell.siteY() + 2, cell.siteZ() - 5);
                VoronoiLattice.CellValues expected = half.values(half.cell(ix, 0, iz), cell.siteX() + 3, cell.siteY() + 2, cell.siteZ() - 5);
                assertEquals(expected, values);
                assertFalse(Double.isNaN(values.gauge()) && !values.lost());
            }
        }
    }

    @Test
    void sharedLatticeGivesEqualValuesOnManyThreads() throws InterruptedException {
        VoronoiLattice shared = new Config(0.0, 1.0, 0.8, 128, 2.0).lattice();
        VoronoiLattice fresh = new Config(0.0, 1.0, 0.8, 128, 2.0).lattice();
        double[] expected = new double[4000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = sample(fresh, i);
        }
        List<Thread> threads = new ArrayList<>();
        boolean[] failed = new boolean[1];
        for (int t = 0; t < 8; t++) {
            int start = t * 500;
            threads.add(Thread.ofPlatform().start(() -> {
                for (int pass = 0; pass < 3; pass++) {
                    for (int k = 0; k < expected.length; k++) {
                        int i = (start + k * 7) % expected.length;
                        if (Double.compare(expected[i], sample(shared, i)) != 0) {
                            failed[0] = true;
                        }
                    }
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertFalse(failed[0], "A thread got another value from the shared caches");
    }

    /** The gauge of the owner at a point of a spiral over many boxes. A lost owner gives its box index. */
    private static double sample(VoronoiLattice lattice, int i) {
        double angle = i * 0.37;
        double x = Math.cos(angle) * i * 2.5;
        double y = (i % 37) * 9.0 - 120;
        double z = Math.sin(angle) * i * 2.5;
        VoronoiLattice.Cell cell = lattice.nearest(x, y, z);
        VoronoiLattice.CellValues values = lattice.values(cell, x, y, z);
        return values.lost() ? cell.ix() * 1000 + cell.iz() : values.gauge() + values.cellRadius();
    }

    /** The cell with the least power rule value over the search box plus {@link #EXTRA} boxes on each side. */
    private static VoronoiLattice.Cell bruteOwner(VoronoiLattice lattice, double[] p) {
        int bx = Mth.floor(p[0] / SIZE);
        int by = Mth.floor((p[1] - LAYER_MIN_Y) / lattice.cellHeight());
        int bz = Mth.floor(p[2] / SIZE);
        int reachXZ = lattice.searchReachXZ() + EXTRA;
        int reachY = lattice.searchReachY() + EXTRA;
        VoronoiLattice.Cell best = null;
        double bestValue = Double.POSITIVE_INFINITY;
        for (int dx = -reachXZ; dx <= reachXZ; dx++) {
            for (int dy = -reachY; dy <= reachY; dy++) {
                for (int dz = -reachXZ; dz <= reachXZ; dz++) {
                    VoronoiLattice.Cell cell = lattice.cell(bx + dx, by + dy, bz + dz);
                    double value = lattice.powerValue(cell, p[0], p[1], p[2]);
                    if (value < bestValue) {
                        bestValue = value;
                        best = cell;
                    }
                }
            }
        }
        return best;
    }

    /** The walls of the cell toward every site of the wall box plus {@link #EXTRA} boxes on each side. */
    private static PowerRule.Walls wideWalls(VoronoiLattice lattice, VoronoiLattice.Cell cell) {
        int reachXZ = lattice.wallReachXZ() + EXTRA;
        int reachY = lattice.wallReachY() + EXTRA;
        int count = (2 * reachXZ + 1) * (2 * reachY + 1) * (2 * reachXZ + 1) - 1;
        double[] others = new double[3 * count];
        double[] weights = new double[count];
        int at = 0;
        for (int dx = -reachXZ; dx <= reachXZ; dx++) {
            for (int dy = -reachY; dy <= reachY; dy++) {
                for (int dz = -reachXZ; dz <= reachXZ; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    VoronoiLattice.Cell other = lattice.cell(cell.ix() + dx, cell.iy() + dy, cell.iz() + dz);
                    others[3 * at] = other.siteX();
                    others[3 * at + 1] = other.siteY() * lattice.yScale();
                    others[3 * at + 2] = other.siteZ();
                    weights[at++] = lattice.cellWeight(other);
                }
            }
        }
        return PowerRule.walls(cell.siteX(), cell.siteY() * lattice.yScale(), cell.siteZ(), lattice.cellWeight(cell), others, weights, count, SIZE, lattice.yScale());
    }

    private static void near(double expected, double actual, String message) {
        assertTrue(Math.abs(actual - expected) <= TOLERANCE * Math.max(1, Math.abs(expected)), message + ": expected " + expected + ", got " + actual);
    }

}
