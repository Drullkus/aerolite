package us.drullk.aerolite.world.island;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

/**
 * Prints the time of 100000 density samples of {@link VoronoiIslands}. The test asserts no time limit.
 * It also prints the time of 100000 owner lookups and gauges on a weighted lattice.
 * The samples lie 4 blocks apart on X and Z, like the noise cells of a chunk. They lie 40 blocks apart on Y.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class LatticeTimingTest {

    private static final DensityFunction.NoiseHolder NOISE = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-7, 1.0)));
    private static final int SAMPLES = 100000;

    @Test
    void printDensityTime() {
        double sink = 0;
        // The first passes warm up the compiler and the caches.
        for (int pass = 0; pass < 3; pass++) {
            sink += densityPass(new VoronoiIslands(NOISE, 256, 128, 0, 0.8, 0.3, 0.9, 0.1, 12.0, 64, 3), pass * 4096);
        }
        VoronoiIslands islands = new VoronoiIslands(NOISE, 256, 128, 0, 0.8, 0.3, 0.9, 0.1, 12.0, 64, 3);
        long start = System.nanoTime();
        sink += densityPass(islands, 20000);
        long time = System.nanoTime() - start;
        System.out.printf("Lattice timing: %d density samples of VoronoiIslands take %.1f ms (checksum %.3f)%n", SAMPLES, time / 1.0E6, sink);
    }

    @Test
    void printWeightedOwnerTime() {
        double sink = 0;
        for (int pass = 0; pass < 3; pass++) {
            sink += ownerPass(weighted(), pass * 4096);
        }
        VoronoiLattice lattice = weighted();
        long start = System.nanoTime();
        sink += ownerPass(lattice, 20000);
        long time = System.nanoTime() - start;
        System.out.printf("Lattice timing: %d owner lookups and gauges, weights 0 to 1, jitter 0.8, 256 by 128, take %.1f ms (checksum %.3f)%n", SAMPLES, time / 1.0E6, sink);
    }

    /** A lattice with the cell weights 0 to 1 and the jitter 0.8. The cell size is 256, the cell height 128 and the Y scale 2. */
    private static VoronoiLattice weighted() {
        return new VoronoiLattice(5L, 256, 128, 0, 0.8, 2.0, (ix, iz) -> (RandomSupport.mixStafford13(RandomSupport.mixStafford13(ix) ^ iz) >>> 11) * 0x1.0p-53, 0.0, 1.0);
    }

    private static double ownerPass(VoronoiLattice lattice, int originX) {
        double sum = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                for (int y = 0; y < 10; y++) {
                    double bx = originX + x * 4;
                    double by = -64 + y * 40;
                    double bz = z * 4;
                    VoronoiLattice.Cell cell = lattice.nearest(bx, by, bz);
                    double gauge = lattice.evaluate(cell, bx, by, bz).value();
                    sum += Double.isNaN(gauge) ? cell.ix() : gauge;
                }
            }
        }
        return sum;
    }

    /** One sample per noise cell corner over a block of 100 by 10 by 100 corners. */
    private static double densityPass(VoronoiIslands islands, int originX) {
        double sum = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                for (int y = 0; y < 10; y++) {
                    sum += islands.compute(originX + x * 4, -64 + y * 40, z * 4);
                }
            }
        }
        return sum;
    }

}
