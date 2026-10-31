package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/**
 * One variation and its pick weight among the variations of one dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param weight    the pick weight; a weight of 0 never picks
 * @param variation the blocks, the maze bias and the decay values
 */
public record WeightedVariation(int weight, ShaftVariation variation) {

    public static final int MAX_WEIGHT = 16;

    /** The weight sits next to the variation fields in one JSON object. */
    public static final Codec<WeightedVariation> CODEC = codec(ShaftVariation.DEFAULT_DECAY_SCALE, ShaftVariation.DEFAULT_DECAY_THRESHOLD, ShaftVariation.DEFAULT_EROSION_THRESHOLD);

    /** A codec whose missing decay fields take the given values. */
    public static Codec<WeightedVariation> codec(int decayScale, float decayThreshold, float erosionThreshold) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("weight", 1).forGetter(WeightedVariation::weight),
                ShaftVariation.mapCodec(decayScale, decayThreshold, erosionThreshold).forGetter(WeightedVariation::variation)
        ).apply(instance, WeightedVariation::new));
    }

    public WeightedVariation withWeight(int weight) {
        return new WeightedVariation(weight, this.variation);
    }

    public WeightedVariation withVariation(ShaftVariation variation) {
        return new WeightedVariation(this.weight, variation);
    }

}
