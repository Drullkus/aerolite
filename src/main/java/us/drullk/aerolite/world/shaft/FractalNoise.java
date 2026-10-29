package us.drullk.aerolite.world.shaft;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Fractal Brownian simplex noise in three dimensions, in the range -1 to 1.
 * Each octave doubles the frequency and halves the amplitude.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public final class FractalNoise {

    private final SimplexNoise[] octaves;
    private final double frequency;
    private final double amplitudeSum;

    /**
     * @param scale the size in blocks of one feature of the first octave
     */
    public FractalNoise(RandomSource random, int octaveCount, double scale) {
        this.octaves = new SimplexNoise[octaveCount];
        double sum = 0;
        for (int k = 0; k < octaveCount; k++) {
            this.octaves[k] = new SimplexNoise(random);
            sum += 1.0 / (1 << k);
        }
        this.frequency = 1.0 / scale;
        this.amplitudeSum = sum;
    }

    public double sample(int x, int y, int z) {
        double value = 0;
        double frequency = this.frequency;
        double amplitude = 1;
        for (SimplexNoise octave : this.octaves) {
            value += amplitude * octave.getValue(x * frequency, y * frequency, z * frequency);
            frequency *= 2;
            amplitude *= 0.5;
        }
        return value / this.amplitudeSum;
    }

}
