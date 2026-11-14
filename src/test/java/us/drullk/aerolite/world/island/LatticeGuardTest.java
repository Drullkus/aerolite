package us.drullk.aerolite.world.island;

import net.minecraft.world.level.levelgen.RandomSupport;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the size guards of the lattice. A guarded lattice gives the owner, the lost flag and each value of an unguarded lattice, bit for bit.
 * Also checks that a full lattice of any size stays below the memory bound.
 * The bound comes from the entry sizes and the slot counts.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class LatticeGuardTest {

    private static final int SIZE = 256;
    /** The memory bound of one full lattice, in bytes. */
    private static final long MEMORY_BOUND = 10L * 1024 * 1024;

    private static VoronoiLattice.CellWeights weights(double min, double max) {
        return (ix, iz) -> min + (max - min) * ((RandomSupport.mixStafford13(RandomSupport.mixStafford13(0x31L ^ ix) ^ iz) >>> 11) * 0x1.0p-53);
    }

    private static VoronoiLattice lattice(int cellSize, int height, double jitter, double yScale, double min, double max, int searchGuard, int wallGuard) {
        return new VoronoiLattice(9L, cellSize, height, -16, jitter, yScale, weights(min, max), min, max, searchGuard, wallGuard);
    }

    @Test
    void forcedGuardGivesTheSameValues() {
        double[][] configs = {{128, 2.0, 0.0, 1.0, 0.8}, {128, 1.0, 0.5, 0.5, 0.8}, {32, 8.0, 0.0, 1.0, 1.0}, {1024, 0.25, 0.25, 0.75, 0.5}, {128, 2.0, 0.5, 0.5, 0.0}};
        for (double[] config : configs) {
            int height = (int) config[0];
            VoronoiLattice open = lattice(SIZE, height, config[4], config[1], config[2], config[3], VoronoiLattice.SEARCH_GUARD, VoronoiLattice.WALL_GUARD);
            VoronoiLattice guarded = lattice(SIZE, height, config[4], config[1], config[2], config[3], -1, -1);
            assertTrue(open.searchCached() && open.wallOffsetsKept(), "The small lattice is guarded");
            assertFalse(guarded.searchCached() || guarded.wallOffsetsKept(), "The forced guard is off");
            assertEquals(open.searchOffsetCount(), guarded.searchOffsetCount());
            assertEquals(open.wallOffsetCount(), guarded.wallOffsetCount());
            compare(open, guarded, 1500, new Random(height));
        }
    }

    @Test
    void latticeAboveTheGuardsGivesTheSameValues() {
        // The cell size 1024, the cell height 32 and the Y scale 1 pass both guards.
        VoronoiLattice guarded = lattice(1024, 32, 0.8, 1.0, 0.5, 0.5, VoronoiLattice.SEARCH_GUARD, VoronoiLattice.WALL_GUARD);
        assertFalse(guarded.searchCached(), "The search keeps its cache above the guard");
        assertFalse(guarded.wallOffsetsKept(), "The walls keep their offsets above the guard");
        VoronoiLattice open = lattice(1024, 32, 0.8, 1.0, 0.5, 0.5, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertTrue(open.searchCached() && open.wallOffsetsKept());
        System.out.println("Guard test: 1024 by 32 has " + guarded.searchOffsetCount() + " search offsets and " + guarded.wallOffsetCount() + " wall offsets");
        compare(open, guarded, 300, new Random(3));
    }

    private static void compare(VoronoiLattice open, VoronoiLattice guarded, int points, Random random) {
        for (int i = 0; i < points; i++) {
            double x = (random.nextDouble() - 0.5) * 4 * open.cellSize();
            double y = -16 + (random.nextDouble() * 3 - 1) * open.cellHeight();
            double z = (random.nextDouble() - 0.5) * 4 * open.cellSize();
            VoronoiLattice.Cell cell = open.nearest(x, y, z);
            assertEquals(cell, guarded.nearest(x, y, z), "The owner differs at " + x + " " + y + " " + z);
            VoronoiLattice.CellValues expected = open.values(cell, x, y, z);
            VoronoiLattice.CellValues actual = guarded.values(cell, x, y, z);
            assertEquals(expected.lost(), actual.lost());
            assertEquals(Double.doubleToRawLongBits(expected.gauge()), Double.doubleToRawLongBits(actual.gauge()), "The gauge differs");
            assertEquals(Double.doubleToRawLongBits(expected.wallDistance()), Double.doubleToRawLongBits(actual.wallDistance()), "The wall distance differs");
            assertEquals(Double.doubleToRawLongBits(expected.cellRadius()), Double.doubleToRawLongBits(actual.cellRadius()), "The cell radius differs");
            assertEquals(guarded.walls(cell).count(), open.walls(cell).count(), "The kept wall count differs");
        }
    }

    @Test
    void fullLatticeStaysBelowTheMemoryBound() {
        long largest = bytes(VoronoiLattice.SEARCH_GUARD, VoronoiLattice.WALL_GUARD);
        StringBuilder text = new StringBuilder(String.format("Memory per full lattice, at most %.2f MB:", largest / 1048576.0));
        assertTrue(largest < MEMORY_BOUND, "A full lattice takes " + largest + " bytes");
        int[][] sizes = {{256, 128}, {1024, 32}, {64, 512}, {16, 4096}};
        for (int[] size : sizes) {
            VoronoiLattice lattice = new VoronoiLattice(1L, size[0], size[1], 0, 0.8);
            text.append(describe(size[0] + " by " + size[1] + ", Y scale 1, jitter 0.8", lattice));
        }
        VoronoiLattice newMode = new VoronoiLattice(1L, SIZE, 128, 0, 1.0, 2.0, (ix, iz) -> 0.0, 0.0, 1.0);
        text.append(describe("256 by 128, Y scale 2, jitter 1, weights 0 to 1", newMode));
        assertTrue(newMode.searchCached() && newMode.wallOffsetsKept(), "The largest lattice of the new mode is guarded");
        VoronoiLattice shipped = new VoronoiLattice(1L, SIZE, 128, 0, 0.8);
        assertTrue(shipped.searchCached() && shipped.wallOffsetsKept(), "The shipped lattice of the old mode is guarded");
        System.out.println(text);
    }

    private static String describe(String name, VoronoiLattice lattice) {
        int search = lattice.searchCached() ? lattice.searchOffsetCount() : 0;
        int walls = lattice.wallOffsetsKept() ? lattice.wallOffsetCount() : 0;
        return String.format("%n  %s: search %s, walls %s, %.2f MB", name, lattice.searchCached() ? "cached" : "guarded", lattice.wallOffsetsKept() ? "kept" : "guarded",
                bytes(search, walls) / 1048576.0);
    }

    /**
     * The bytes of a full lattice with the search offset count and the wall offset count of its arrays. A count of 0 stands for a guard.
     * An object takes a 16 byte header, an array a 16 byte header, a reference 4 bytes.
     */
    private static long bytes(int searchOffsets, int wallOffsets) {
        long slot = 16 + 12 + 4;
        long site = 16 + 4 * 8;
        long sites = VoronoiLattice.SITE_CACHE_SIZE * (slot + site);
        long search = searchOffsets == 0 ? 0 : 16 + 2 * 4 + 16 + 24L * searchOffsets + 16 + 8L * searchOffsets;
        long searches = searchOffsets == 0 ? 0 : VoronoiLattice.SEARCH_CACHE_SIZE * (slot + search) + 16 + 12L * searchOffsets;
        long offsets = wallOffsets == 0 ? 0 : 16 + 12L * wallOffsets;
        long kept = VoronoiLattice.KEPT_GUARD;
        long walls = 16 + 4 * 8 + 4 * 4 + 4 + 1 + 8 + 16 + 24 * kept + 16 + 8 * kept + 16 + 8 * kept + 16 + 12 * kept;
        long wallCache = VoronoiLattice.WALL_CACHE_SIZE * (slot + walls);
        long tables = 3 * 16 + 4L * (VoronoiLattice.SITE_CACHE_SIZE + VoronoiLattice.SEARCH_CACHE_SIZE + VoronoiLattice.WALL_CACHE_SIZE);
        return sites + searches + offsets + wallCache + tables;
    }

}
