package us.drullk.aerolite.world.island;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the sites, the nearest site search and the gauge of the lattice.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
class VoronoiLatticeTest {

    private static final VoronoiLattice LATTICE = new VoronoiLattice(7L, 256, 128, 0, 0.8);
    private static final int SAMPLES = 2000;

    private static double[] randomPoint(Random random) {
        return new double[] {
                (random.nextDouble() - 0.5) * 8 * LATTICE.cellSize(),
                LATTICE.layerMinY() + (random.nextDouble() - 0.5) * 6 * LATTICE.cellHeight(),
                (random.nextDouble() - 0.5) * 8 * LATTICE.cellSize()
        };
    }

    private static double distance(VoronoiLattice.Cell cell, double[] p) {
        double dx = cell.siteX() - p[0];
        double dy = cell.siteY() - p[1];
        double dz = cell.siteZ() - p[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The nearest cell over a lattice block one step wider than the search reach around the point. */
    private static VoronoiLattice.Cell bruteNearest(double[] p) {
        int bx = Math.floorDiv((int) Math.floor(p[0]), LATTICE.cellSize());
        int by = Math.floorDiv((int) Math.floor(p[1] - LATTICE.layerMinY()), LATTICE.cellHeight());
        int bz = Math.floorDiv((int) Math.floor(p[2]), LATTICE.cellSize());
        int reachXZ = LATTICE.searchReachXZ() + 1;
        int reachY = LATTICE.searchReachY() + 1;
        VoronoiLattice.Cell best = null;
        for (int dx = -reachXZ; dx <= reachXZ; dx++) {
            for (int dy = -reachY; dy <= reachY; dy++) {
                for (int dz = -reachXZ; dz <= reachXZ; dz++) {
                    VoronoiLattice.Cell cell = LATTICE.cell(bx + dx, by + dy, bz + dz);
                    if (best == null || distance(cell, p) < distance(best, p)) {
                        best = cell;
                    }
                }
            }
        }
        return best;
    }

    @Test
    void siteStaysInsideItsBox() {
        for (int ix = -3; ix <= 3; ix++) {
            for (int iy = -2; iy <= 2; iy++) {
                for (int iz = -3; iz <= 3; iz++) {
                    VoronoiLattice.Cell cell = LATTICE.cell(ix, iy, iz);
                    assertTrue(cell.siteX() >= ix * 256 && cell.siteX() < (ix + 1) * 256, "The site X left its box");
                    assertTrue(cell.siteY() >= iy * 128 && cell.siteY() < (iy + 1) * 128, "The site Y left its box");
                    assertTrue(cell.siteZ() >= iz * 256 && cell.siteZ() < (iz + 1) * 256, "The site Z left its box");
                    double margin = (1 - 0.8) / 2;
                    assertTrue(cell.siteX() >= (ix + margin) * 256 && cell.siteX() <= (ix + 1 - margin) * 256, "The jitter passed its margin");
                }
            }
        }
    }

    @Test
    void reachFollowsTheBoxSizesAndTheJitter() {
        assertEquals(2, LATTICE.searchReachXZ());
        assertEquals(3, LATTICE.searchReachY());
        assertEquals(3, LATTICE.wallReachXZ());
        assertEquals(6, LATTICE.wallReachY());
        VoronoiLattice cubic = new VoronoiLattice(7L, 128, 128, 0, 0.8);
        assertEquals(cubic.searchReachXZ(), cubic.searchReachY());
        assertEquals(1, new VoronoiLattice(7L, 128, 128, 0, 0.0).searchReachXZ());
    }

    @Test
    void sitesDependOnTheSeed() {
        VoronoiLattice other = new VoronoiLattice(8L, 256, 128, 0, 0.8);
        assertNotEquals(LATTICE.cell(0, 0, 0), other.cell(0, 0, 0));
        assertEquals(LATTICE.cell(0, 0, 0), new VoronoiLattice(7L, 256, 128, 0, 0.8).cell(0, 0, 0));
    }

    @Test
    void nearestMatchesTheBruteForceSearch() {
        Random random = new Random(1);
        for (int i = 0; i < SAMPLES; i++) {
            double[] p = randomPoint(random);
            assertEquals(bruteNearest(p), LATTICE.nearest(p[0], p[1], p[2]), "The nearest search missed the nearest site");
        }
    }

    @Test
    void gaugeIsZeroAtTheSiteAndBelowOneInsideTheCell() {
        Random random = new Random(2);
        for (int i = 0; i < SAMPLES; i++) {
            double[] p = randomPoint(random);
            VoronoiLattice.Cell cell = LATTICE.nearest(p[0], p[1], p[2]);
            assertEquals(0.0, LATTICE.evaluate(cell, cell.siteX(), cell.siteY(), cell.siteZ()).value(), 1e-9);
            double inside = LATTICE.evaluate(cell, p[0], p[1], p[2]).value();
            assertTrue(inside <= 1.0 + 1e-9, "A point of the cell has a gauge above 1");
            VoronoiLattice.Cell other = LATTICE.cell(cell.ix() + 1, cell.iy(), cell.iz());
            assertTrue(LATTICE.evaluate(other, p[0], p[1], p[2]).value() > 1.0 - 1e-9, "A point outside the cell has a gauge below 1");
        }
    }

    @Test
    void gaugeIsOneOnTheWallAndScalesAboutTheSite() {
        Random random = new Random(3);
        for (int i = 0; i < SAMPLES; i++) {
            double[] p = randomPoint(random);
            VoronoiLattice.Cell cell = LATTICE.nearest(p[0], p[1], p[2]);
            VoronoiLattice.Gauge gauge = LATTICE.evaluate(cell, p[0], p[1], p[2]);
            double scale = 0.5;
            double sx = cell.siteX() + scale * (p[0] - cell.siteX());
            double sy = cell.siteY() + scale * (p[1] - cell.siteY());
            double sz = cell.siteZ() + scale * (p[2] - cell.siteZ());
            assertEquals(scale * gauge.value(), LATTICE.evaluate(cell, sx, sy, sz).value(), 1e-9, "The gauge is not homogeneous");
            VoronoiLattice.Cell neighbor = nearestNeighbor(cell);
            double mx = (cell.siteX() + neighbor.siteX()) / 2;
            double my = (cell.siteY() + neighbor.siteY()) / 2;
            double mz = (cell.siteZ() + neighbor.siteZ()) / 2;
            assertEquals(1.0, LATTICE.evaluate(cell, mx, my, mz).value(), 1e-9, "The midpoint to the nearest neighbor is not on the wall");
            assertEquals(distance(cell, new double[] {mx, my, mz}), gauge.halfSpacing(), 1e-9, "The half spacing is not the distance to the nearest wall");
        }
    }

    @Test
    void valuesAreInUnitRangeAndIndependentPerSalt() {
        boolean differ = false;
        for (int ix = -5; ix <= 5; ix++) {
            VoronoiLattice.Cell cell = LATTICE.cell(ix, 0, 0);
            double a = LATTICE.value(cell, 1);
            double b = LATTICE.value(cell, 2);
            assertTrue(a >= 0 && a < 1 && b >= 0 && b < 1);
            differ |= Math.abs(a - b) > 1e-6;
        }
        assertTrue(differ, "Two salts gave the same values");
    }

    private static VoronoiLattice.Cell nearestNeighbor(VoronoiLattice.Cell cell) {
        VoronoiLattice.Cell best = null;
        double[] site = {cell.siteX(), cell.siteY(), cell.siteZ()};
        int reachXZ = LATTICE.wallReachXZ();
        int reachY = LATTICE.wallReachY();
        for (int dx = -reachXZ; dx <= reachXZ; dx++) {
            for (int dy = -reachY; dy <= reachY; dy++) {
                for (int dz = -reachXZ; dz <= reachXZ; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    VoronoiLattice.Cell other = LATTICE.cell(cell.ix() + dx, cell.iy() + dy, cell.iz() + dz);
                    if (best == null || distance(other, site) < distance(best, site)) {
                        best = other;
                    }
                }
            }
        }
        return best;
    }

}
