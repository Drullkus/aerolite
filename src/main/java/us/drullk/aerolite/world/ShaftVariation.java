package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * One set of shaft blocks, maze bias and decay values. The registry {@code aerolite:shaft_variation} holds the shipped variations.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 *
 * @param bias             the weight of axis edges in the maze sampler; ring edges get one minus this weight
 * @param decayScale       the size of one decay region along a shaft, a power of two
 * @param decayThreshold   decay noise below the negative threshold keeps only maze walls, above it only maze paths
 * @param erosionThreshold decay noise beyond this threshold in either direction erodes the shaft and its junction boxes
 * @param interior         the shaft interior block
 * @param junction         the block of the bezel of a junction
 * @param wall             the maze wall block
 * @param path             the maze path block
 */
public record ShaftVariation(float bias, int decayScale, float decayThreshold, float erosionThreshold,
                             BlockState interior, BlockState junction, BlockState wall, BlockState path) {

    public static final float MIN_BIAS = 0.05F;
    public static final float MAX_BIAS = 0.95F;
    public static final float UNIFORM_BIAS = 0.5F;
    public static final int MIN_DECAY_SCALE = 32;
    public static final int MAX_DECAY_SCALE = 1024;
    /** The smallest step from the decay threshold to the erosion threshold. */
    public static final float THRESHOLD_STEP = 0.05F;
    public static final int DEFAULT_DECAY_SCALE = 64;
    public static final float DEFAULT_DECAY_THRESHOLD = 0.35F;
    public static final float DEFAULT_EROSION_THRESHOLD = 0.5F;

    /** A bias outside the slider range clamps to the range. */
    private static final Codec<Float> BIAS_CODEC = Codec.FLOAT.xmap(ShaftVariation::clampBias, bias -> bias);

    public static final Codec<Integer> DECAY_SCALE_CODEC = Codec.intRange(MIN_DECAY_SCALE, MAX_DECAY_SCALE).validate(scale -> Mth.isPowerOfTwo(scale)
            ? DataResult.success(scale)
            : DataResult.error(() -> "Decay scale is not a power of two: " + scale));

    public static final MapCodec<ShaftVariation> MAP_CODEC = mapCodec(DEFAULT_DECAY_SCALE, DEFAULT_DECAY_THRESHOLD, DEFAULT_EROSION_THRESHOLD);

    /** The codec of one entry of the registry {@code aerolite:shaft_variation}. */
    public static final Codec<ShaftVariation> CODEC = MAP_CODEC.codec();

    /**
     * The variation fields, whose missing decay fields take the given values. The fields are always written.
     * A missing junction block takes the interior block.
     * A save whose erosion threshold is not above its decay threshold loads with the erosion threshold one step above it.
     */
    public static MapCodec<ShaftVariation> mapCodec(int decayScale, float decayThreshold, float erosionThreshold) {
        return RecordCodecBuilder.<ShaftVariation>mapCodec(instance -> instance.group(
                AeroliteCodecs.defaultedField(BIAS_CODEC, "bias", UNIFORM_BIAS).forGetter(ShaftVariation::bias),
                AeroliteCodecs.defaultedField(DECAY_SCALE_CODEC, "decay_scale", decayScale).forGetter(ShaftVariation::decayScale),
                AeroliteCodecs.defaultedField(Codec.floatRange(0, 1), "decay_threshold", decayThreshold).forGetter(ShaftVariation::decayThreshold),
                AeroliteCodecs.defaultedField(Codec.floatRange(THRESHOLD_STEP, 1), "erosion_threshold", erosionThreshold).forGetter(ShaftVariation::erosionThreshold),
                BlockState.CODEC.fieldOf("interior").forGetter(ShaftVariation::interior),
                BlockState.CODEC.optionalFieldOf("junction").forGetter(variation -> Optional.of(variation.junction())),
                BlockState.CODEC.fieldOf("wall").forGetter(ShaftVariation::wall),
                BlockState.CODEC.fieldOf("path").forGetter(ShaftVariation::path)
        ).apply(instance, (bias, scale, decay, erosion, interior, junction, wall, path) ->
                new ShaftVariation(bias, scale, decay, erosion, interior, junction.orElse(interior), wall, path))).xmap(ShaftVariation::withErosionAboveDecay, variation -> variation);
    }

    /** A variation with the default decay values. Its junction block is its interior block. */
    public static ShaftVariation standard(float bias, BlockState interior, BlockState wall, BlockState path) {
        return new ShaftVariation(bias, DEFAULT_DECAY_SCALE, DEFAULT_DECAY_THRESHOLD, DEFAULT_EROSION_THRESHOLD, interior, interior, wall, path);
    }

    /** The lowest erosion threshold that keeps one step above a decay threshold. */
    public static float lowestErosion(float decayThreshold) {
        return Math.min(1, decayThreshold + THRESHOLD_STEP);
    }

    /** A save from before the erosion threshold can hold a decay threshold above the default erosion threshold. */
    private ShaftVariation withErosionAboveDecay() {
        float lowest = lowestErosion(this.decayThreshold);
        return this.erosionThreshold >= lowest ? this : this.withErosionThreshold(lowest);
    }

    public static float clampBias(float bias) {
        return Mth.clamp(bias, MIN_BIAS, MAX_BIAS);
    }

    public ShaftVariation withBias(float bias) {
        return new ShaftVariation(bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.interior, this.junction, this.wall, this.path);
    }

    public ShaftVariation withDecayScale(int scale) {
        return new ShaftVariation(this.bias, scale, this.decayThreshold, this.erosionThreshold, this.interior, this.junction, this.wall, this.path);
    }

    public ShaftVariation withDecayThreshold(float threshold) {
        return new ShaftVariation(this.bias, this.decayScale, threshold, this.erosionThreshold, this.interior, this.junction, this.wall, this.path);
    }

    public ShaftVariation withErosionThreshold(float threshold) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, threshold, this.interior, this.junction, this.wall, this.path);
    }

    public ShaftVariation withInterior(BlockState interior) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, interior, this.junction, this.wall, this.path);
    }

    public ShaftVariation withJunction(BlockState junction) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.interior, junction, this.wall, this.path);
    }

    public ShaftVariation withWall(BlockState wall) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.interior, this.junction, wall, this.path);
    }

    public ShaftVariation withPath(BlockState path) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.interior, this.junction, this.wall, path);
    }

}
