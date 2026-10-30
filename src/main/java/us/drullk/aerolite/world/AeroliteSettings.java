package us.drullk.aerolite.world;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings of the preset. The player sets them in the world creation menu.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 *
 * @param minShaftSpacing the smallest center distance of two neighbor horizontal shafts on one axis
 * @param maxShaftSpacing the largest center distance of two neighbor horizontal shafts on one axis
 * @param columnChance    the chance that a crossing of two shafts in plan view gets a column
 * @param decayScale      the size of one decay region along a shaft, a power of two
 * @param decayThreshold  decay noise below the negative threshold keeps only maze walls, above it only maze paths
 * @param erosionThreshold decay noise beyond this threshold in either direction erodes shafts and junction boxes
 * @param cloudHeight     the Y level of the clouds in the Overworld
 * @param variations      the shaft variations of each listed dimension
 * @param shaftsInUnlistedDimensions true when a dimension outside the map uses the Overworld variations
 */
public record AeroliteSettings(
        int minShaftSpacing,
        int maxShaftSpacing,
        float columnChance,
        int decayScale,
        float decayThreshold,
        float erosionThreshold,
        int cloudHeight,
        Map<ResourceKey<Level>, List<ShaftVariation>> variations,
        boolean shaftsInUnlistedDimensions
) {

    public static final int MIN_SHAFT_SPACING = 32;
    public static final int MAX_SHAFT_SPACING = 1024;
    public static final int MIN_DECAY_SCALE = 32;
    public static final int MAX_DECAY_SCALE = 1024;
    /** The smallest step from the decay threshold to the erosion threshold. */
    public static final float THRESHOLD_STEP = 0.05F;
    public static final int MIN_CLOUD_HEIGHT = -64;
    public static final int MAX_CLOUD_HEIGHT = 320;

    public static final AeroliteSettings DEFAULT = new AeroliteSettings(
            128,
            384,
            0.25F,
            256,
            0.5F,
            0.7F,
            -32,
            ImmutableMap.of(
                    Level.OVERWORLD, List.of(ShaftVariation.STONE, ShaftVariation.TRIAL_CHAMBER),
                    Level.NETHER, List.of(ShaftVariation.NETHER),
                    Level.END, List.of(ShaftVariation.END)
            ),
            false
    );

    private static final Codec<Integer> DECAY_SCALE_CODEC = Codec.intRange(MIN_DECAY_SCALE, MAX_DECAY_SCALE).validate(scale -> Mth.isPowerOfTwo(scale)
            ? DataResult.success(scale)
            : DataResult.error(() -> "Decay scale is not a power of two: " + scale));

    private static final Codec<Integer> SPACING_CODEC = Codec.intRange(MIN_SHAFT_SPACING, MAX_SHAFT_SPACING);

    private static final Codec<List<ShaftVariation>> VARIATION_LIST_CODEC = ShaftVariation.CODEC.listOf().validate(list -> list.stream().anyMatch(variation -> variation.weight() > 0)
            ? DataResult.success(list)
            : DataResult.error(() -> "A variation list holds no variation with a positive weight"));

    private static final Codec<Map<ResourceKey<Level>, List<ShaftVariation>>> VARIATIONS_CODEC = Codec.unboundedMap(ResourceKey.codec(Registries.DIMENSION), VARIATION_LIST_CODEC);

    public static final Codec<AeroliteSettings> CODEC = RecordCodecBuilder.<AeroliteSettings>create(instance -> instance.group(
            SPACING_CODEC.optionalFieldOf("min_shaft_spacing", DEFAULT.minShaftSpacing).forGetter(AeroliteSettings::minShaftSpacing),
            SPACING_CODEC.optionalFieldOf("max_shaft_spacing", DEFAULT.maxShaftSpacing).forGetter(AeroliteSettings::maxShaftSpacing),
            Codec.floatRange(0, 1).optionalFieldOf("column_chance", DEFAULT.columnChance).forGetter(AeroliteSettings::columnChance),
            DECAY_SCALE_CODEC.optionalFieldOf("decay_scale", DEFAULT.decayScale).forGetter(AeroliteSettings::decayScale),
            Codec.floatRange(0, 1).optionalFieldOf("decay_threshold", DEFAULT.decayThreshold).forGetter(AeroliteSettings::decayThreshold),
            Codec.floatRange(THRESHOLD_STEP, 1).optionalFieldOf("erosion_threshold", DEFAULT.erosionThreshold).forGetter(AeroliteSettings::erosionThreshold),
            Codec.intRange(MIN_CLOUD_HEIGHT, MAX_CLOUD_HEIGHT).optionalFieldOf("cloud_height", DEFAULT.cloudHeight).forGetter(AeroliteSettings::cloudHeight),
            VARIATIONS_CODEC.optionalFieldOf("variations", DEFAULT.variations).forGetter(AeroliteSettings::variations),
            Codec.BOOL.optionalFieldOf("shafts_in_unlisted_dimensions", DEFAULT.shaftsInUnlistedDimensions).forGetter(AeroliteSettings::shaftsInUnlistedDimensions)
    ).apply(instance, AeroliteSettings::new)).validate(settings -> settings.minShaftSpacing <= settings.maxShaftSpacing
            ? DataResult.success(settings)
            : DataResult.error(() -> "Minimum shaft spacing exceeds maximum shaft spacing: " + settings.minShaftSpacing + " > " + settings.maxShaftSpacing)
    ).xmap(AeroliteSettings::withErosionAboveDecay, settings -> settings);

    /** A save from before the erosion threshold can hold a decay threshold above the default erosion threshold. */
    private AeroliteSettings withErosionAboveDecay() {
        float lowest = Math.min(1, this.decayThreshold + THRESHOLD_STEP);
        return this.erosionThreshold >= lowest ? this : this.withErosionThreshold(lowest);
    }

    /** The cell size of the jittered grid of shaft positions. */
    public int meanShaftSpacing() {
        return (this.minShaftSpacing + this.maxShaftSpacing) / 2;
    }

    public AeroliteSettings withShaftSpacing(int min, int max) {
        return new AeroliteSettings(min, max, this.columnChance, this.decayScale, this.decayThreshold, this.erosionThreshold, this.cloudHeight, this.variations, this.shaftsInUnlistedDimensions);
    }

    public AeroliteSettings withColumnChance(float chance) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, chance, this.decayScale, this.decayThreshold, this.erosionThreshold, this.cloudHeight, this.variations, this.shaftsInUnlistedDimensions);
    }

    public AeroliteSettings withDecayScale(int scale) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, scale, this.decayThreshold, this.erosionThreshold, this.cloudHeight, this.variations, this.shaftsInUnlistedDimensions);
    }

    public AeroliteSettings withErosionThreshold(float threshold) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, this.decayScale, this.decayThreshold, threshold, this.cloudHeight, this.variations, this.shaftsInUnlistedDimensions);
    }

    public AeroliteSettings withDecayThreshold(float threshold) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, this.decayScale, threshold, this.erosionThreshold, this.cloudHeight, this.variations, this.shaftsInUnlistedDimensions);
    }

    public AeroliteSettings withCloudHeight(int height) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, this.decayScale, this.decayThreshold, this.erosionThreshold, height, this.variations, this.shaftsInUnlistedDimensions);
    }

    /**
     * The variations of a dimension. A dimension outside the map takes the Overworld list when
     * {@link #shaftsInUnlistedDimensions} is true. An empty list means no shafts.
     */
    public List<ShaftVariation> variations(ResourceKey<Level> dimension) {
        List<ShaftVariation> listed = this.variations.get(dimension);
        if (listed != null) {
            return listed;
        }
        return this.shaftsInUnlistedDimensions ? this.variations.getOrDefault(Level.OVERWORLD, List.of()) : List.of();
    }

    public AeroliteSettings withShaftsInUnlistedDimensions(boolean shafts) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, this.decayScale, this.decayThreshold, this.erosionThreshold, this.cloudHeight, this.variations, shafts);
    }

    /** Replaces the variation list of one dimension. */
    public AeroliteSettings withVariations(ResourceKey<Level> dimension, List<ShaftVariation> list) {
        Map<ResourceKey<Level>, List<ShaftVariation>> map = new LinkedHashMap<>(this.variations);
        map.put(dimension, List.copyOf(list));
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, this.decayScale, this.decayThreshold, this.erosionThreshold, this.cloudHeight, ImmutableMap.copyOf(map), this.shaftsInUnlistedDimensions);
    }

    /** Replaces the variation at {@code index} in the list of one dimension. */
    public AeroliteSettings withVariation(ResourceKey<Level> dimension, int index, ShaftVariation variation) {
        List<ShaftVariation> list = new ArrayList<>(this.variations.getOrDefault(dimension, List.of()));
        list.set(index, variation);
        return this.withVariations(dimension, list);
    }

    /** Appends a variation to the list of one dimension. */
    public AeroliteSettings withAddedVariation(ResourceKey<Level> dimension, ShaftVariation variation) {
        List<ShaftVariation> list = new ArrayList<>(this.variations.getOrDefault(dimension, List.of()));
        list.add(variation);
        return this.withVariations(dimension, list);
    }

    /** Removes the variation at {@code index} from the list of one dimension. */
    public AeroliteSettings withoutVariation(ResourceKey<Level> dimension, int index) {
        List<ShaftVariation> list = new ArrayList<>(this.variations.getOrDefault(dimension, List.of()));
        list.remove(index);
        return this.withVariations(dimension, list);
    }

}
