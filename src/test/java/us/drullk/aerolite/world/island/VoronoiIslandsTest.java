package us.drullk.aerolite.world.island;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the island density: positive at the site of a filled cell, negative outside the row 0 and in an empty cell.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
class VoronoiIslandsTest {

    private static final DensityFunction.NoiseHolder NOISE = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-7, 1.0)));
    private static final VoronoiIslands ISLANDS = new VoronoiIslands(NOISE, 256, 128, 0, 0.8, 0.3, 0.9, 0.1);

    @Test
    void filledCellIsSolidAtItsSiteAndHollowOnItsWall() {
        int filled = 0;
        int empty = 0;
        for (int ix = -6; ix <= 6; ix++) {
            for (int iz = -6; iz <= 6; iz++) {
                VoronoiLattice.Cell cell = ISLANDS.lattice().cell(ix, 0, iz);
                double atSite = ISLANDS.compute(cell.siteX(), cell.siteY(), cell.siteZ());
                if (ISLANDS.hasIsland(cell)) {
                    filled++;
                    double fill = ISLANDS.fill(cell);
                    assertTrue(fill >= 0.3 && fill <= 0.9, "The fill left its bounds");
                    assertTrue(atSite > 0, "A filled cell is hollow at its site");
                    double wallX = cell.siteX();
                    VoronoiLattice.Gauge gauge = ISLANDS.lattice().evaluate(cell, wallX, cell.siteY(), cell.siteZ());
                    assertEquals(fill * gauge.halfSpacing(), atSite, 1e-9, "The density at the site is not the fill times the half spacing");
                } else {
                    empty++;
                    assertEquals(ISLANDS.minValue(), atSite, "An empty cell is solid at its site");
                }
            }
        }
        assertTrue(filled > 100, "Too few filled cells");
        assertTrue(empty > 0, "No empty cell");
    }

    @Test
    void rowsAboveAndBelowHoldNoIsland() {
        for (int ix = -6; ix <= 6; ix++) {
            for (int iy : new int[] {-2, -1, 1, 2}) {
                VoronoiLattice.Cell cell = ISLANDS.lattice().cell(ix, iy, 0);
                assertEquals(ISLANDS.minValue(), ISLANDS.compute(cell.siteX(), cell.siteY(), cell.siteZ()), "A cell outside the row 0 is solid");
            }
        }
    }

    @Test
    void densityStaysInsideItsBoundsAndIsNegativeOutsideTheIsland() {
        Random random = new Random(4);
        for (int i = 0; i < 2000; i++) {
            double x = (random.nextDouble() - 0.5) * 2048;
            double y = (random.nextDouble() - 0.5) * 512;
            double z = (random.nextDouble() - 0.5) * 2048;
            double density = ISLANDS.compute(x, y, z);
            assertTrue(density >= ISLANDS.minValue() && density <= ISLANDS.maxValue(), "The density left its bounds");
            VoronoiLattice.Cell cell = ISLANDS.lattice().nearest(x, y, z);
            double gauge = ISLANDS.lattice().evaluate(cell, x, y, z).value();
            boolean inside = ISLANDS.hasIsland(cell) && gauge < ISLANDS.fill(cell);
            assertEquals(inside, density > 0, "The sign of the density does not match the scaled cell");
        }
    }

    @Test
    void contextComputeMatchesThePointCompute() {
        assertEquals(ISLANDS.compute(10, 20, 30), ISLANDS.compute(new DensityFunction.SinglePointContext(10, 20, 30)));
    }

}
