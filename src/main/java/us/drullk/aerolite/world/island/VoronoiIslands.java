package us.drullk.aerolite.world.island;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomSupport;

/**
 * The density function of the islands. Each cell of the row 0 of a {@link VoronoiLattice} holds one island, or none.
 * An island is its cell scaled about the site by the fill fraction of the cell. The density is positive inside the island.
 * Outside the row 0, in an empty cell, and outside the island, the density is negative.
 * The noise is the seed channel: the random state wires it with the world seed, and the lattice seed derives from its values.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public final class VoronoiIslands implements DensityFunction.SimpleFunction {

    public static final MapCodec<VoronoiIslands> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(VoronoiIslands::noise),
            Codec.intRange(16, 4096).fieldOf("cell_size").forGetter(VoronoiIslands::cellSize),
            Codec.intRange(16, 4096).fieldOf("cell_height").forGetter(VoronoiIslands::cellHeight),
            Codec.INT.fieldOf("layer_min_y").forGetter(VoronoiIslands::layerMinY),
            Codec.doubleRange(0.0, 1.0).fieldOf("jitter").forGetter(VoronoiIslands::jitter),
            Codec.doubleRange(0.0, 1.0).fieldOf("fill_min").forGetter(VoronoiIslands::fillMin),
            Codec.doubleRange(0.0, 1.0).fieldOf("fill_max").forGetter(VoronoiIslands::fillMax),
            Codec.doubleRange(0.0, 1.0).fieldOf("empty_chance").forGetter(VoronoiIslands::emptyChance)
    ).apply(instance, VoronoiIslands::new));
    public static final KeyDispatchDataCodec<VoronoiIslands> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final int EMPTY_SALT = 0xE0;
    private static final int FILL_SALT = 0xF1;

    private final DensityFunction.NoiseHolder noise;
    private final int cellSize;
    private final int cellHeight;
    private final int layerMinY;
    private final double jitter;
    private final double fillMin;
    private final double fillMax;
    private final double emptyChance;
    private final VoronoiLattice lattice;

    public VoronoiIslands(DensityFunction.NoiseHolder noise, int cellSize, int cellHeight, int layerMinY, double jitter, double fillMin, double fillMax, double emptyChance) {
        this.noise = noise;
        this.cellSize = cellSize;
        this.cellHeight = cellHeight;
        this.layerMinY = layerMinY;
        this.jitter = jitter;
        this.fillMin = Math.min(fillMin, fillMax);
        this.fillMax = Math.max(fillMin, fillMax);
        this.emptyChance = emptyChance;
        this.lattice = new VoronoiLattice(seedOf(noise), cellSize, cellHeight, layerMinY, jitter);
    }

    /** The lattice seed from the wired noise. An unwired noise gives the seed 0. */
    private static long seedOf(DensityFunction.NoiseHolder noise) {
        if (noise.noise() == null) {
            return 0L;
        }
        long a = Double.doubleToLongBits(noise.getValue(0.3, 0.7, 0.1));
        long b = Double.doubleToLongBits(noise.getValue(100.3, -40.7, 7.9));
        long c = Double.doubleToLongBits(noise.getValue(-5000.1, 12.5, 999.9));
        return RandomSupport.mixStafford13(RandomSupport.mixStafford13(RandomSupport.mixStafford13(a) ^ b) ^ c);
    }

    public DensityFunction.NoiseHolder noise() {
        return this.noise;
    }

    public int cellSize() {
        return this.cellSize;
    }

    public int cellHeight() {
        return this.cellHeight;
    }

    public int layerMinY() {
        return this.layerMinY;
    }

    public double jitter() {
        return this.jitter;
    }

    public double fillMin() {
        return this.fillMin;
    }

    public double fillMax() {
        return this.fillMax;
    }

    public double emptyChance() {
        return this.emptyChance;
    }

    public VoronoiLattice lattice() {
        return this.lattice;
    }

    /** True while the cell holds an island. Only a cell of the row 0 holds one. */
    public boolean hasIsland(VoronoiLattice.Cell cell) {
        return cell.iy() == 0 && this.lattice.value(cell, EMPTY_SALT) >= this.emptyChance;
    }

    /** The fill fraction of the cell, between the two fill bounds. */
    public double fill(VoronoiLattice.Cell cell) {
        return this.fillMin + (this.fillMax - this.fillMin) * this.lattice.value(cell, FILL_SALT);
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return this.compute(context.blockX(), context.blockY(), context.blockZ());
    }

    /** The density at a point. The unit is about one block near the island surface. */
    public double compute(double x, double y, double z) {
        VoronoiLattice.Cell cell = this.lattice.nearest(x, y, z);
        if (!this.hasIsland(cell)) {
            return this.minValue();
        }
        VoronoiLattice.Gauge gauge = this.lattice.evaluate(cell, x, y, z);
        return (this.fill(cell) - gauge.value()) * gauge.halfSpacing();
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new VoronoiIslands(visitor.visitNoise(this.noise), this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance));
    }

    @Override
    public double minValue() {
        return -2.0 * Math.max(this.cellSize, this.cellHeight);
    }

    @Override
    public double maxValue() {
        return this.fillMax * Math.max(this.cellSize, this.cellHeight);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
