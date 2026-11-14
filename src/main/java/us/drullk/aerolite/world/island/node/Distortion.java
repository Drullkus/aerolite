package us.drullk.aerolite.world.island.node;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

/**
 * The distortion of one island variation: fractal simplex noise with one channel per axis.
 * The distortion is the largest shift in blocks. The distortion scale is the wavelength of the first octave in blocks.
 * Each further octave halves the amplitude and the wavelength. The octave amplitudes sum to the distortion.
 * The noise seeds from the lattice seed and the three distortion fields.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class Distortion {

    private static final long WARP_SALT = 0xDA;
    /** The amplitude factor from one octave to the next. */
    private static final double PERSISTENCE = 0.5;

    private final double distortion;
    private final int scale;
    private final int octaves;
    private final SimplexNoise warpX;
    private final SimplexNoise warpY;
    private final SimplexNoise warpZ;
    /** The amplitude of the first octave. */
    private final double amplitude;

    public Distortion(long latticeSeed, double distortion, int scale, int octaves) {
        this.distortion = distortion;
        this.scale = scale;
        this.octaves = octaves;
        long seed = RandomSupport.mixStafford13(latticeSeed ^ WARP_SALT);
        seed = RandomSupport.mixStafford13(seed ^ Double.doubleToLongBits(distortion));
        seed = RandomSupport.mixStafford13(seed ^ scale);
        seed = RandomSupport.mixStafford13(seed ^ octaves);
        RandomSource random = RandomSource.create(seed);
        this.warpX = new SimplexNoise(random);
        this.warpY = new SimplexNoise(random);
        this.warpZ = new SimplexNoise(random);
        double sum = 0;
        double amplitude = 1;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude;
            amplitude *= PERSISTENCE;
        }
        this.amplitude = sum == 0 ? 0 : distortion / sum;
    }

    /** The shifted position. Each axis shifts by its channel, by at most the distortion. A distortion of 0 shifts nothing. */
    public Vec3 warp(double x, double y, double z) {
        if (this.distortion == 0) {
            return new Vec3(x, y, z);
        }
        return new Vec3(x + this.shift(this.warpX, x, y, z), y + this.shift(this.warpY, x, y, z), z + this.shift(this.warpZ, x, y, z));
    }

    private double shift(SimplexNoise channel, double x, double y, double z) {
        double amplitude = this.amplitude;
        double frequency = 1.0 / this.scale;
        double sum = 0;
        for (int octave = 0; octave < this.octaves; octave++) {
            sum += amplitude * channel.getValue(x * frequency, y * frequency, z * frequency);
            amplitude *= PERSISTENCE;
            frequency *= 2;
        }
        return sum;
    }

}
