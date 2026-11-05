package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The placement of the shafts and columns of one dimension. The registry {@code aerolite:shaft_placement} holds one placement per dimension id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param minShaftSpacing the smallest center distance of two neighbor horizontal shafts on one axis
 * @param maxShaftSpacing the largest center distance of two neighbor horizontal shafts on one axis
 * @param columnChance    the chance that a crossing of two shafts in plan view gets a column
 */
public record ShaftPlacement(int minShaftSpacing, int maxShaftSpacing, float columnChance) {

    public static final int MIN_SHAFT_SPACING = 32;
    public static final int MAX_SHAFT_SPACING = 1024;

    public static final ShaftPlacement DEFAULT = new ShaftPlacement(64, 128, 0.3F);

    private static final Codec<Integer> SPACING_CODEC = Codec.intRange(MIN_SHAFT_SPACING, MAX_SHAFT_SPACING);

    /** The fields of the placement. They are always written. A 0.1.0 save holds them at the top level of the settings. */
    public static final MapCodec<ShaftPlacement> MAP_CODEC = RecordCodecBuilder.<ShaftPlacement>mapCodec(instance -> instance.group(
            AeroliteCodecs.defaultedField(SPACING_CODEC, "min_shaft_spacing", DEFAULT.minShaftSpacing).forGetter(ShaftPlacement::minShaftSpacing),
            AeroliteCodecs.defaultedField(SPACING_CODEC, "max_shaft_spacing", DEFAULT.maxShaftSpacing).forGetter(ShaftPlacement::maxShaftSpacing),
            AeroliteCodecs.defaultedField(Codec.floatRange(0, 1), "column_chance", DEFAULT.columnChance).forGetter(ShaftPlacement::columnChance)
    ).apply(instance, ShaftPlacement::new)).validate(ShaftPlacement::validate);

    public static final Codec<ShaftPlacement> CODEC = MAP_CODEC.codec();

    /** The minimum spacing stays at or below the maximum spacing. */
    public static DataResult<ShaftPlacement> validate(ShaftPlacement placement) {
        return placement.minShaftSpacing <= placement.maxShaftSpacing
                ? DataResult.success(placement)
                : DataResult.error(() -> "Minimum shaft spacing exceeds maximum shaft spacing: " + placement.minShaftSpacing + " > " + placement.maxShaftSpacing);
    }

    /** The cell size of the jittered grid of shaft positions. */
    public int meanShaftSpacing() {
        return (this.minShaftSpacing + this.maxShaftSpacing) / 2;
    }

    public ShaftPlacement withShaftSpacing(int min, int max) {
        return new ShaftPlacement(min, max, this.columnChance);
    }

    public ShaftPlacement withColumnChance(float chance) {
        return new ShaftPlacement(this.minShaftSpacing, this.maxShaftSpacing, chance);
    }

}
