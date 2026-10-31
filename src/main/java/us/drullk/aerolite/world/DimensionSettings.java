package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The shaft settings of one dimension: the placement and the variations.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_e43f73bd-d689-4553-8382-7b011c0c142a.md}.
 *
 * @param placement  the placement of the shafts and columns
 * @param variations the variations that each shaft and column picks from by weight
 */
public record DimensionSettings(ShaftPlacement placement, List<ShaftVariation> variations) {

    /** The settings of a dimension outside the map. The empty variation list gives no shafts. */
    public static final DimensionSettings EMPTY = new DimensionSettings(ShaftPlacement.DEFAULT, List.of());

    public DimensionSettings {
        variations = List.copyOf(variations);
    }

    public static final Codec<DimensionSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ShaftPlacement.CODEC.optionalFieldOf("placement", ShaftPlacement.DEFAULT).forGetter(DimensionSettings::placement),
            variationListCodec(ShaftVariation.CODEC).fieldOf("variations").forGetter(DimensionSettings::variations)
    ).apply(instance, DimensionSettings::new));

    /** A list codec of the variations that holds at least one variation with a positive weight. */
    public static Codec<List<ShaftVariation>> variationListCodec(Codec<ShaftVariation> variation) {
        return variation.listOf().validate(list -> list.stream().anyMatch(entry -> entry.weight() > 0)
                ? DataResult.success(list)
                : DataResult.error(() -> "A variation list holds no variation with a positive weight"));
    }

    /** The default placement and the default variation list of a dimension. */
    public static DimensionSettings defaults(ResourceKey<Level> dimension) {
        return new DimensionSettings(ShaftPlacement.DEFAULT, ShaftVariation.defaultList(dimension));
    }

    public DimensionSettings withPlacement(ShaftPlacement placement) {
        return new DimensionSettings(placement, this.variations);
    }

    public DimensionSettings withVariations(List<ShaftVariation> variations) {
        return new DimensionSettings(this.placement, variations);
    }

    /** Applies an edit to every variation. */
    public DimensionSettings mapVariations(UnaryOperator<ShaftVariation> edit) {
        List<ShaftVariation> edited = new ArrayList<>(this.variations.size());
        for (ShaftVariation variation : this.variations) {
            edited.add(edit.apply(variation));
        }
        return this.withVariations(edited);
    }

}
