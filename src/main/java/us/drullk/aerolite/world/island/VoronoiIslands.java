package us.drullk.aerolite.world.island;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

/**
 * The density function of the islands. Each cell of the row 0 of a {@link VoronoiLattice} holds one island, or none.
 * An island is its cell scaled about the site by the fill fraction of the cell. The density is positive inside the island.
 * Outside the row 0, in an empty cell, and outside the island, the density is negative.
 * The noise is the seed channel: the random state wires it with the world seed, and the lattice seed derives from its values.
 * The distortion shifts the position before the lattice lookup. Fractal simplex noise with one channel per axis gives the shift.
 * The distortion is the largest shift in blocks. The distortion scale is the wavelength of the first octave in blocks.
 * Each further octave halves the amplitude and the wavelength.
 * The lattice gives every cell the cell weight 0.5 and takes the Y scale 1. So its cells are the plain Voronoi cells.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class VoronoiIslands implements DensityFunction.SimpleFunction {

    /** The slider bounds of the distortion, in blocks. */
    public static final int MIN_DISTORTION = 0;
    public static final int MAX_DISTORTION = 32;
    public static final int MIN_DISTORTION_SCALE = 8;
    public static final int MAX_DISTORTION_SCALE = 1024;
    public static final int MIN_DISTORTION_OCTAVES = 1;
    public static final int MAX_DISTORTION_OCTAVES = 8;
    /** The codec bounds of the cell size and the cell height, in blocks. */
    public static final int MIN_CELL = 16;
    public static final int MAX_CELL = 4096;

    public static final MapCodec<VoronoiIslands> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(VoronoiIslands::noise),
            Codec.intRange(MIN_CELL, MAX_CELL).fieldOf("cell_size").forGetter(VoronoiIslands::cellSize),
            Codec.intRange(MIN_CELL, MAX_CELL).fieldOf("cell_height").forGetter(VoronoiIslands::cellHeight),
            Codec.INT.fieldOf("layer_min_y").forGetter(VoronoiIslands::layerMinY),
            Codec.doubleRange(0.0, 1.0).fieldOf("jitter").forGetter(VoronoiIslands::jitter),
            Codec.doubleRange(0.0, 1.0).fieldOf("fill_min").forGetter(VoronoiIslands::fillMin),
            Codec.doubleRange(0.0, 1.0).fieldOf("fill_max").forGetter(VoronoiIslands::fillMax),
            Codec.doubleRange(0.0, 1.0).fieldOf("empty_chance").forGetter(VoronoiIslands::emptyChance),
            Codec.doubleRange(MIN_DISTORTION, MAX_DISTORTION).fieldOf("distortion").forGetter(VoronoiIslands::distortion),
            Codec.intRange(MIN_DISTORTION_SCALE, MAX_DISTORTION_SCALE).fieldOf("distortion_scale").forGetter(VoronoiIslands::distortionScale),
            Codec.intRange(MIN_DISTORTION_OCTAVES, MAX_DISTORTION_OCTAVES).fieldOf("distortion_octaves").forGetter(VoronoiIslands::distortionOctaves)
    ).apply(instance, VoronoiIslands::new));
    public static final KeyDispatchDataCodec<VoronoiIslands> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final int EMPTY_SALT = 0xE0;
    private static final int FILL_SALT = 0xF1;
    private static final long WARP_SALT = 0xDA;
    /** The amplitude factor from one octave to the next. */
    private static final double PERSISTENCE = 0.5;

    private final DensityFunction.NoiseHolder noise;
    private final int cellSize;
    private final int cellHeight;
    private final int layerMinY;
    private final double jitter;
    private final double fillMin;
    private final double fillMax;
    private final double emptyChance;
    private final double distortion;
    private final int distortionScale;
    private final int distortionOctaves;
    private final VoronoiLattice lattice;
    private final SimplexNoise warpX;
    private final SimplexNoise warpY;
    private final SimplexNoise warpZ;
    /** The amplitude of the first octave. The octave amplitudes sum to the distortion. */
    private final double warpAmplitude;

    public VoronoiIslands(DensityFunction.NoiseHolder noise, int cellSize, int cellHeight, int layerMinY, double jitter, double fillMin, double fillMax, double emptyChance,
                          double distortion, int distortionScale, int distortionOctaves) {
        this.noise = noise;
        this.cellSize = cellSize;
        this.cellHeight = cellHeight;
        this.layerMinY = layerMinY;
        this.jitter = jitter;
        this.fillMin = Math.min(fillMin, fillMax);
        this.fillMax = Math.max(fillMin, fillMax);
        this.emptyChance = emptyChance;
        this.distortion = distortion;
        this.distortionScale = distortionScale;
        this.distortionOctaves = distortionOctaves;
        this.lattice = new VoronoiLattice(seedOf(noise), cellSize, cellHeight, layerMinY, jitter);
        RandomSource random = RandomSource.create(this.lattice.seed() ^ WARP_SALT);
        this.warpX = new SimplexNoise(random);
        this.warpY = new SimplexNoise(random);
        this.warpZ = new SimplexNoise(random);
        double sum = 0;
        double amplitude = 1;
        for (int octave = 0; octave < distortionOctaves; octave++) {
            sum += amplitude;
            amplitude *= PERSISTENCE;
        }
        this.warpAmplitude = distortion / sum;
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

    public double distortion() {
        return this.distortion;
    }

    public int distortionScale() {
        return this.distortionScale;
    }

    public int distortionOctaves() {
        return this.distortionOctaves;
    }

    public VoronoiLattice lattice() {
        return this.lattice;
    }

    /** A copy with another distortion. The other fields and the noise stay. */
    public VoronoiIslands withDistortion(double distortion) {
        return new VoronoiIslands(this.noise, this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance,
                distortion, this.distortionScale, this.distortionOctaves);
    }

    /** True while the cell holds an island. Only a cell of the row 0 holds one. A lost cell holds none. */
    public boolean hasIsland(VoronoiLattice.Cell cell) {
        return cell.iy() == 0 && this.lattice.value(cell, EMPTY_SALT) >= this.emptyChance && !this.lattice.lost(cell);
    }

    /** The fill fraction of the cell, between the two fill bounds. */
    public double fill(VoronoiLattice.Cell cell) {
        return this.fillMin + (this.fillMax - this.fillMin) * this.lattice.value(cell, FILL_SALT);
    }

    /** The shifted position. Each axis shifts by its noise channel, by at most the distortion. */
    public Vec3 warp(double x, double y, double z) {
        if (this.distortion == 0) {
            return new Vec3(x, y, z);
        }
        return new Vec3(x + this.shift(this.warpX, x, y, z), y + this.shift(this.warpY, x, y, z), z + this.shift(this.warpZ, x, y, z));
    }

    private double shift(SimplexNoise channel, double x, double y, double z) {
        double amplitude = this.warpAmplitude;
        double frequency = 1.0 / this.distortionScale;
        double sum = 0;
        for (int octave = 0; octave < this.distortionOctaves; octave++) {
            sum += amplitude * channel.getValue(x * frequency, y * frequency, z * frequency);
            amplitude *= PERSISTENCE;
            frequency *= 2;
        }
        return sum;
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return this.compute(context.blockX(), context.blockY(), context.blockZ());
    }

    /** The density at a point. The unit is about one block near the island surface. */
    public double compute(double x, double y, double z) {
        Vec3 warped = this.warp(x, y, z);
        VoronoiLattice.Cell cell = this.lattice.nearest(warped.x, warped.y, warped.z);
        if (!this.hasIsland(cell)) {
            return this.minValue();
        }
        VoronoiLattice.Gauge gauge = this.lattice.evaluate(cell, warped.x, warped.y, warped.z);
        return PowerRule.islandBody(this.fill(cell), gauge.value(), gauge.halfSpacing());
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new VoronoiIslands(visitor.visitNoise(this.noise), this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance,
                this.distortion, this.distortionScale, this.distortionOctaves));
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
