package us.drullk.aerolite.world.island;

import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Checks that the cell weight 0.5 and the Y scale 1 give each value of 0.5.0, bit for bit.
 * {@link Lattice050} holds the nearest site search and the gauge of 0.5.0.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class PlainLatticeTest {

    private static final DensityFunction.NoiseHolder NOISE = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-7, 1.0)));
    private static final int SAMPLES = 4000;
    private static final int[][] SIZES = {{256, 128}, {256, 256}, {128, 512}, {512, 64}};
    private static final double[] JITTERS = {0.0, 0.5, 0.8, 1.0};

    @Test
    void nearestGaugeAndHalfSpacingEqual050() {
        for (int[] size : SIZES) {
            for (double jitter : JITTERS) {
                VoronoiLattice lattice = new VoronoiLattice(7L, size[0], size[1], -16, jitter);
                Lattice050 old = new Lattice050(7L, size[0], size[1], -16, jitter);
                Random random = new Random(size[0] * 31L + size[1] + (long) (jitter * 100));
                for (int i = 0; i < SAMPLES; i++) {
                    double x = (random.nextDouble() - 0.5) * 8 * size[0];
                    double y = -16 + (random.nextDouble() - 0.5) * 6 * size[1];
                    double z = (random.nextDouble() - 0.5) * 8 * size[0];
                    VoronoiLattice.Cell cell = lattice.nearest(x, y, z);
                    String where = size[0] + "x" + size[1] + ", jitter " + jitter + " at " + x + " " + y + " " + z;
                    assertEquals(old.nearest(x, y, z), cell, where + ": the nearest cell");
                    double[] gauge = old.evaluate(cell, x, y, z);
                    VoronoiLattice.Gauge now = lattice.evaluate(cell, x, y, z);
                    assertEquals(gauge[0], now.value(), 0.0, where + ": the gauge");
                    assertEquals(gauge[1], now.halfSpacing(), 0.0, where + ": the half spacing");
                    assertEquals(gauge[1], lattice.cellRadius(cell), 0.0, where + ": the cell radius");
                    assertFalse(lattice.lost(cell), where + ": a lost cell");
                    assertEquals(0.5, lattice.cellWeight(cell), 0.0);
                }
            }
        }
    }

    @Test
    void densityEquals050() {
        for (double distortion : new double[] {0.0, 12.0}) {
            VoronoiIslands islands = new VoronoiIslands(NOISE, 256, 128, 0, 0.8, 0.3, 0.9, 0.1, distortion, 64, 3);
            Lattice050 old = new Lattice050(islands.lattice().seed(), 256, 128, 0, 0.8);
            Random random = new Random(5);
            int positive = 0;
            for (int i = 0; i < SAMPLES; i++) {
                double x = (random.nextDouble() - 0.5) * 2048;
                double y = (random.nextDouble() - 0.5) * 512;
                double z = (random.nextDouble() - 0.5) * 2048;
                Vec3 warped = islands.warp(x, y, z);
                VoronoiLattice.Cell cell = old.nearest(warped.x, warped.y, warped.z);
                double expected;
                if (cell.iy() != 0 || islands.lattice().value(cell, 0xE0) < islands.emptyChance()) {
                    expected = islands.minValue();
                } else {
                    double[] gauge = old.evaluate(cell, warped.x, warped.y, warped.z);
                    expected = (islands.fill(cell) - gauge[0]) * gauge[1];
                }
                positive += expected > 0 ? 1 : 0;
                assertEquals(expected, islands.compute(x, y, z), 0.0, "The density differs from 0.5.0 at " + x + " " + y + " " + z);
            }
            assertEquals(true, positive > 0, "No sample hit an island");
        }
    }

    /** The lattice of 0.5.0: the nearest site search and the gauge over the full boxes of the reach. */
    private static final class Lattice050 {

        private final long seed;
        private final int cellSize;
        private final int cellHeight;
        private final int layerMinY;
        private final double jitter;
        private final int searchReachXZ;
        private final int searchReachY;
        private final int wallReachXZ;
        private final int wallReachY;

        private Lattice050(long seed, int cellSize, int cellHeight, int layerMinY, double jitter) {
            this.seed = seed;
            this.cellSize = cellSize;
            this.cellHeight = cellHeight;
            this.layerMinY = layerMinY;
            this.jitter = jitter;
            double offset = 0.5 + jitter / 2;
            double farthest = Math.sqrt(2 * (offset * cellSize) * (offset * cellSize) + (offset * cellHeight) * (offset * cellHeight));
            this.searchReachXZ = reach(farthest, cellSize, offset);
            this.searchReachY = reach(farthest, cellHeight, offset);
            this.wallReachXZ = reach(2 * farthest, cellSize, jitter);
            this.wallReachY = reach(2 * farthest, cellHeight, jitter);
        }

        private static int reach(double distance, int size, double slack) {
            return Math.max(1, Mth.ceil(distance / size + slack) - 1);
        }

        private VoronoiLattice.Cell nearest(double x, double y, double z) {
            int bx = Mth.floor(x / this.cellSize);
            int by = Mth.floor((y - this.layerMinY) / this.cellHeight);
            int bz = Mth.floor(z / this.cellSize);
            double[] site = new double[3];
            double best = Double.POSITIVE_INFINITY;
            int nx = bx;
            int ny = by;
            int nz = bz;
            for (int dx = -this.searchReachXZ; dx <= this.searchReachXZ; dx++) {
                for (int dy = -this.searchReachY; dy <= this.searchReachY; dy++) {
                    for (int dz = -this.searchReachXZ; dz <= this.searchReachXZ; dz++) {
                        this.site(bx + dx, by + dy, bz + dz, site);
                        double distance = squared(site[0] - x, site[1] - y, site[2] - z);
                        if (distance < best) {
                            best = distance;
                            nx = bx + dx;
                            ny = by + dy;
                            nz = bz + dz;
                        }
                    }
                }
            }
            double[] found = new double[3];
            this.site(nx, ny, nz, found);
            return new VoronoiLattice.Cell(nx, ny, nz, found[0], found[1], found[2]);
        }

        /** The gauge and the half spacing. */
        private double[] evaluate(VoronoiLattice.Cell cell, double x, double y, double z) {
            double px = x - cell.siteX();
            double py = y - cell.siteY();
            double pz = z - cell.siteZ();
            double[] site = new double[3];
            double gauge = Double.NEGATIVE_INFINITY;
            double halfSpacing = Double.POSITIVE_INFINITY;
            for (int dx = -this.wallReachXZ; dx <= this.wallReachXZ; dx++) {
                for (int dy = -this.wallReachY; dy <= this.wallReachY; dy++) {
                    for (int dz = -this.wallReachXZ; dz <= this.wallReachXZ; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        this.site(cell.ix() + dx, cell.iy() + dy, cell.iz() + dz, site);
                        double vx = site[0] - cell.siteX();
                        double vy = site[1] - cell.siteY();
                        double vz = site[2] - cell.siteZ();
                        double spacing = squared(vx, vy, vz);
                        gauge = Math.max(gauge, 2.0 * (px * vx + py * vy + pz * vz) / spacing);
                        halfSpacing = Math.min(halfSpacing, spacing);
                    }
                }
            }
            return new double[] {gauge, Math.sqrt(halfSpacing) / 2.0};
        }

        private void site(int ix, int iy, int iz, double[] out) {
            long hash = RandomSupport.mixStafford13(this.seed ^ 0x5173);
            hash = RandomSupport.mixStafford13(hash ^ ix);
            hash = RandomSupport.mixStafford13(hash ^ iy);
            hash = RandomSupport.mixStafford13(hash ^ iz);
            out[0] = (ix + 0.5 + this.jitter * (unit(hash) - 0.5)) * this.cellSize;
            out[1] = this.layerMinY + (iy + 0.5 + this.jitter * (unit(hash >>> 21) - 0.5)) * this.cellHeight;
            out[2] = (iz + 0.5 + this.jitter * (unit(hash >>> 42) - 0.5)) * this.cellSize;
        }

        private static double unit(long bits) {
            return (bits & 0x1FFFFF) * (1.0 / (1 << 21));
        }

        private static double squared(double x, double y, double z) {
            return x * x + y * y + z * z;
        }
    }

}
