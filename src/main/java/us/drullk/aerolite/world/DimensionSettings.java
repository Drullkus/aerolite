package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The shaft settings of one dimension: the placement and the weighted variations.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param placement  the placement of the shafts and columns
 * @param variations the variations that each shaft and column picks from by weight, in pick sequence
 */
public record DimensionSettings(ShaftPlacement placement, List<WeightedVariation> variations) {

    /** The settings of a dimension without shaft data. The empty variation list gives no shafts. */
    public static final DimensionSettings EMPTY = new DimensionSettings(ShaftPlacement.DEFAULT, List.of());

    public DimensionSettings {
        variations = List.copyOf(variations);
    }

    public static final Codec<DimensionSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ShaftPlacement.CODEC.optionalFieldOf("placement", ShaftPlacement.DEFAULT).forGetter(DimensionSettings::placement),
            variationListCodec(WeightedVariation.CODEC).fieldOf("variations").forGetter(DimensionSettings::variations)
    ).apply(instance, DimensionSettings::new));

    /** A list codec of the variations that holds at least one variation with a positive weight. */
    public static Codec<List<WeightedVariation>> variationListCodec(Codec<WeightedVariation> variation) {
        return variation.listOf().validate(list -> list.stream().anyMatch(entry -> entry.weight() > 0)
                ? DataResult.success(list)
                : DataResult.error(() -> "A variation list holds no variation with a positive weight"));
    }

    public DimensionSettings withPlacement(ShaftPlacement placement) {
        return new DimensionSettings(placement, this.variations);
    }

    public DimensionSettings withVariations(List<WeightedVariation> variations) {
        return new DimensionSettings(this.placement, variations);
    }

    /** Applies an edit to every variation. The weights stay. */
    public DimensionSettings mapVariations(UnaryOperator<ShaftVariation> edit) {
        List<WeightedVariation> edited = new ArrayList<>(this.variations.size());
        for (WeightedVariation variation : this.variations) {
            edited.add(variation.withVariation(edit.apply(variation.variation())));
        }
        return this.withVariations(edited);
    }

}
