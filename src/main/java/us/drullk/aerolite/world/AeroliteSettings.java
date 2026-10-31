package us.drullk.aerolite.world;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The settings of the preset. The player sets them in the world creation menu. The generator carries them.
 * The default settings come from the shipped registry entries, see {@link ShaftLookup#defaultSettings()}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param cloudHeight the Y level of the clouds in the Overworld
 * @param dimensions  the shaft settings of each listed dimension; a dimension outside the map gets no shafts
 */
public record AeroliteSettings(int cloudHeight, Map<ResourceKey<Level>, DimensionSettings> dimensions) {

    public static final int MIN_CLOUD_HEIGHT = -64;
    public static final int MAX_CLOUD_HEIGHT = 320;

    public static final int DEFAULT_CLOUD_HEIGHT = -32;

    public static final Codec<Integer> CLOUD_HEIGHT_CODEC = Codec.intRange(MIN_CLOUD_HEIGHT, MAX_CLOUD_HEIGHT);

    private static final Codec<ResourceKey<Level>> DIMENSION_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);

    /** The layout since 0.2.0. A save with the field {@code dimensions} uses it. */
    private static final MapCodec<AeroliteSettings> CURRENT_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CLOUD_HEIGHT_CODEC.optionalFieldOf("cloud_height", DEFAULT_CLOUD_HEIGHT).forGetter(AeroliteSettings::cloudHeight),
            Codec.unboundedMap(DIMENSION_KEY_CODEC, DimensionSettings.CODEC).fieldOf("dimensions").forGetter(AeroliteSettings::dimensions)
    ).apply(instance, AeroliteSettings::new));

    /** The top-level values of a 0.1.0 save. The placement and the decay values then apply to every dimension. */
    private record LegacyValues(ShaftPlacement placement, int decayScale, float decayThreshold, float erosionThreshold, int cloudHeight) {

        private static final MapCodec<LegacyValues> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ShaftPlacement.MAP_CODEC.forGetter(LegacyValues::placement),
                ShaftVariation.DECAY_SCALE_CODEC.optionalFieldOf("decay_scale", ShaftVariation.DEFAULT_DECAY_SCALE).forGetter(LegacyValues::decayScale),
                Codec.floatRange(0, 1).optionalFieldOf("decay_threshold", ShaftVariation.DEFAULT_DECAY_THRESHOLD).forGetter(LegacyValues::decayThreshold),
                Codec.floatRange(ShaftVariation.THRESHOLD_STEP, 1).optionalFieldOf("erosion_threshold", ShaftVariation.DEFAULT_EROSION_THRESHOLD).forGetter(LegacyValues::erosionThreshold),
                CLOUD_HEIGHT_CODEC.optionalFieldOf("cloud_height", DEFAULT_CLOUD_HEIGHT).forGetter(LegacyValues::cloudHeight)
        ).apply(instance, LegacyValues::new));

        /** The erosion threshold of the save, at least one step above its decay threshold. */
        private float foldedErosion() {
            return Math.max(this.erosionThreshold, ShaftVariation.lowestErosion(this.decayThreshold));
        }

        /** The variation map codec of the save. A variation without decay fields takes the top-level values. */
        private Codec<Map<ResourceKey<Level>, List<WeightedVariation>>> variationsCodec() {
            Codec<WeightedVariation> variation = WeightedVariation.codec(this.decayScale, this.decayThreshold, this.foldedErosion());
            return Codec.unboundedMap(DIMENSION_KEY_CODEC, DimensionSettings.variationListCodec(variation));
        }

        /** The default variations with the decay values of the save. */
        private Map<ResourceKey<Level>, List<WeightedVariation>> defaultVariations(ShaftLookup lookup) {
            Map<ResourceKey<Level>, List<WeightedVariation>> map = new LinkedHashMap<>();
            lookup.defaultSettings().dimensions.forEach((dimension, settings) -> map.put(dimension, settings.mapVariations(variation -> variation
                    .withDecayScale(this.decayScale)
                    .withDecayThreshold(this.decayThreshold)
                    .withErosionThreshold(this.foldedErosion())).variations()));
            return map;
        }

        private AeroliteSettings settings(Map<ResourceKey<Level>, List<WeightedVariation>> variations) {
            Map<ResourceKey<Level>, DimensionSettings> dimensions = new LinkedHashMap<>();
            variations.forEach((dimension, list) -> dimensions.put(dimension, new DimensionSettings(this.placement, list)));
            return new AeroliteSettings(this.cloudHeight, ImmutableMap.copyOf(dimensions));
        }
    }

    /** Reads the 0.1.0 layout and the current layout. Writes only the current layout. */
    private static final MapCodec<AeroliteSettings> MAP_CODEC = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return CURRENT_CODEC.keys(ops);
        }

        @Override
        public <T> DataResult<AeroliteSettings> decode(DynamicOps<T> ops, MapLike<T> input) {
            return input.get("dimensions") != null ? CURRENT_CODEC.decode(ops, input) : decodeLegacy(ops, input);
        }

        @Override
        public <T> RecordBuilder<T> encode(AeroliteSettings input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return CURRENT_CODEC.encode(input, ops, prefix);
        }
    };

    public static final Codec<AeroliteSettings> CODEC = MAP_CODEC.codec();

    /** A 0.1.0 save without a variation map reads the default lists from the registries of the ops. */
    private static <T> DataResult<AeroliteSettings> decodeLegacy(DynamicOps<T> ops, MapLike<T> input) {
        return LegacyValues.CODEC.decode(ops, input).flatMap(values -> {
            T variations = input.get("variations");
            DataResult<Map<ResourceKey<Level>, List<WeightedVariation>>> lists = variations == null
                    ? ShaftLookup.of(ops).map(values::defaultVariations)
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Settings without dimensions need the shaft registries"))
                    : values.variationsCodec().parse(ops, variations);
            return lists.map(values::settings);
        });
    }

    /** The settings of a dimension. A dimension outside the map gets empty settings, so no shafts. */
    public DimensionSettings dimension(ResourceKey<Level> dimension) {
        return this.dimensions.getOrDefault(dimension, DimensionSettings.EMPTY);
    }

    /** The variations of a dimension. A dimension outside the map gets an empty list, so no shafts. */
    public List<WeightedVariation> variations(ResourceKey<Level> dimension) {
        return this.dimension(dimension).variations();
    }

    /** The placement of a dimension. A dimension outside the map gets the default placement. */
    public ShaftPlacement placement(ResourceKey<Level> dimension) {
        return this.dimension(dimension).placement();
    }

    public AeroliteSettings withCloudHeight(int height) {
        return new AeroliteSettings(height, this.dimensions);
    }

    /** Puts the settings of one dimension into the map. A listed dimension gets its settings replaced. */
    public AeroliteSettings withDimension(ResourceKey<Level> dimension, DimensionSettings settings) {
        Map<ResourceKey<Level>, DimensionSettings> map = new LinkedHashMap<>(this.dimensions);
        map.put(dimension, settings);
        return new AeroliteSettings(this.cloudHeight, ImmutableMap.copyOf(map));
    }

    /** Puts the variation list of one dimension into the map. A dimension outside the map gets the default placement. */
    public AeroliteSettings withDimension(ResourceKey<Level> dimension, List<WeightedVariation> list) {
        return this.withDimension(dimension, this.dimension(dimension).withVariations(list));
    }

    /** Removes one dimension and its settings from the map, so the dimension gets no shafts. */
    public AeroliteSettings withoutDimension(ResourceKey<Level> dimension) {
        if (!this.dimensions.containsKey(dimension)) {
            return this;
        }
        Map<ResourceKey<Level>, DimensionSettings> map = new LinkedHashMap<>(this.dimensions);
        map.remove(dimension);
        return new AeroliteSettings(this.cloudHeight, ImmutableMap.copyOf(map));
    }

    /** Replaces the placement of a listed dimension. A dimension outside the map stays outside. */
    public AeroliteSettings withPlacement(ResourceKey<Level> dimension, ShaftPlacement placement) {
        DimensionSettings settings = this.dimensions.get(dimension);
        return settings == null ? this : this.withDimension(dimension, settings.withPlacement(placement));
    }

    /** Replaces the variation at {@code index} in the list of one dimension. The weight stays. */
    public AeroliteSettings withVariation(ResourceKey<Level> dimension, int index, ShaftVariation variation) {
        List<WeightedVariation> list = new ArrayList<>(this.variations(dimension));
        list.set(index, list.get(index).withVariation(variation));
        return this.withDimension(dimension, list);
    }

    /** Replaces the weight of the variation at {@code index} in the list of one dimension. */
    public AeroliteSettings withWeight(ResourceKey<Level> dimension, int index, int weight) {
        List<WeightedVariation> list = new ArrayList<>(this.variations(dimension));
        list.set(index, list.get(index).withWeight(weight));
        return this.withDimension(dimension, list);
    }

    /** Appends a variation to the list of one dimension. */
    public AeroliteSettings withAddedVariation(ResourceKey<Level> dimension, WeightedVariation variation) {
        List<WeightedVariation> list = new ArrayList<>(this.variations(dimension));
        list.add(variation);
        return this.withDimension(dimension, list);
    }

    /** Removes the variation at {@code index} from the list of one dimension. */
    public AeroliteSettings withoutVariation(ResourceKey<Level> dimension, int index) {
        List<WeightedVariation> list = new ArrayList<>(this.variations(dimension));
        list.remove(index);
        return this.withDimension(dimension, list);
    }

}
