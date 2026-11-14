package us.drullk.aerolite.world.island.variation;

import net.minecraft.world.level.levelgen.RandomSupport;
import us.drullk.aerolite.world.island.VoronoiLattice;

/**
 * The seeded values of the cells of one lattice: the pick values, the cell weight roll, the fill roll and the cell random channels.
 * Each value is {@link VoronoiLattice#value} with the lattice seed and its own salt. So each roll is independent of the others.
 * A cell random channel mixes the channel into the lattice seed, so two channels never share their values.
 * The fill salt is the fill salt of {@code VoronoiIslands}. So equal fill ranges give the fill of 0.5.0.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class CellRolls {

    /** The salt of the pick value at list depth 0. Each further depth adds one. */
    public static final int PICK_SALT = 0x7C00;
    public static final int CELL_WEIGHT_SALT = 0xCE;
    public static final int FILL_SALT = 0xF1;
    /** The salt of the cell random channels. Each channel also mixes into its own seed. */
    public static final long RANDOM_SALT = 0x4D;

    private final long seed;

    public CellRolls(long seed) {
        this.seed = seed;
    }

    public long seed() {
        return this.seed;
    }

    /** The pick value of the cell of the row 0 at the list depth, in [0, 1). */
    public double pick(int ix, int iz, int depth) {
        return VoronoiLattice.value(this.seed, ix, 0, iz, PICK_SALT + depth);
    }

    /** The rolled cell weight of the cell of the row 0, uniform in the range. */
    public double cellWeight(UnitRange range, int ix, int iz) {
        return range.at(VoronoiLattice.value(this.seed, ix, 0, iz, CELL_WEIGHT_SALT));
    }

    /** The rolled fill of the cell of the row 0, uniform in the range. */
    public double fill(UnitRange range, int ix, int iz) {
        return range.at(VoronoiLattice.value(this.seed, ix, 0, iz, FILL_SALT));
    }

    /** The value of one cell random channel of the cell, in [0, 1). */
    public double random(int channel, int ix, int iy, int iz) {
        return VoronoiLattice.value(this.channelSeed(channel), ix, iy, iz, (int) RANDOM_SALT);
    }

    /** The seed of a cell random channel. Two channels never share a seed. */
    long channelSeed(int channel) {
        return RandomSupport.mixStafford13(this.seed ^ (RANDOM_SALT << 32 | Integer.toUnsignedLong(channel)));
    }

}
