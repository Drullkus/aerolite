package us.drullk.aerolite.world.shaft;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A positional random factory that mixes each coordinate into the seed.
 * The vanilla Xoroshiro factory puts the coordinate hash into the seed unmixed,
 * so its first values correlate between neighbor coordinates.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 */
public final class MixedRandomFactory implements PositionalRandomFactory {

    private final long seed;

    public MixedRandomFactory(long seed) {
        this.seed = RandomSupport.mixStafford13(seed);
    }

    /** A factory for one purpose, independent of the factories of other purposes. */
    public MixedRandomFactory fork(String name) {
        return new MixedRandomFactory(this.seed ^ name.hashCode());
    }

    /** A factory for the coordinates, independent of the randoms at other coordinates. */
    public MixedRandomFactory fork(int x, int y, int z) {
        return new MixedRandomFactory(this.mix(x, y, z));
    }

    /** The seed of the random at the coordinates. A random with this seed equals {@link #at}. */
    public long seedAt(int x, int y, int z) {
        return this.mix(x, y, z);
    }

    private long mix(int x, int y, int z) {
        long hash = RandomSupport.mixStafford13(this.seed ^ x);
        hash = RandomSupport.mixStafford13(hash ^ y);
        return RandomSupport.mixStafford13(hash ^ z);
    }

    @Override
    public RandomSource at(int x, int y, int z) {
        return new XoroshiroRandomSource(this.mix(x, y, z));
    }

    @Override
    public RandomSource fromHashOf(String name) {
        return new XoroshiroRandomSource(RandomSupport.mixStafford13(this.seed ^ name.hashCode()));
    }

    @Override
    public RandomSource fromSeed(long seed) {
        return new XoroshiroRandomSource(RandomSupport.mixStafford13(this.seed ^ seed));
    }

    @Override
    public void parityConfigString(StringBuilder builder) {
        builder.append("MixedRandomFactory{").append(this.seed).append('}');
    }

}
