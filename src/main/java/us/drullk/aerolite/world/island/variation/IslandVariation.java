package us.drullk.aerolite.world.island.variation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.VoronoiIslands;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * One island variation: an island or Empty. Each cell of the row 0 picks one from an island variation list.
 * An island holds its density function, its biome source, its cell weight range, its fill range and its distortion.
 * An Empty variation holds its biome source and its cell weight range. Its cells hold no island.
 * A field without a value takes the default of the Basic variation, the values of 0.5.0.
 * The codec writes every field of the variation. An island writes no {@code empty} key.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 *
 * @param densityFunction the density function of an island, empty for an Empty variation
 * @param distortion the largest shift of the island body node, in blocks
 * @param distortionScale the wavelength of the first distortion octave, in blocks
 */
public record IslandVariation(boolean empty, Optional<Holder<DensityFunction>> densityFunction, BiomeSource biomeSource, UnitRange cellWeight, UnitRange fill,
                              double distortion, int distortionScale, int distortionOctaves) {

    /** The default cell weight range. */
    public static final UnitRange DEFAULT_CELL_WEIGHT = new UnitRange(0.5, 0.5);
    /** The default fill range: the fill bounds of 0.5.0. */
    public static final UnitRange DEFAULT_FILL = new UnitRange(IslandSettings.DEFAULT.fillMin(), IslandSettings.DEFAULT.fillMax());
    public static final double DEFAULT_DISTORTION = IslandSettings.DEFAULT.distortion();
    public static final int DEFAULT_DISTORTION_SCALE = IslandSettings.DEFAULT.distortionScale();
    public static final int DEFAULT_DISTORTION_OCTAVES = IslandSettings.DEFAULT.distortionOctaves();
    /** The slider step of the cell weight range. The slider runs from 0 to 1. */
    public static final double CELL_WEIGHT_STEP = 0.05;
    /** The slider step of the fill range. The slider runs from 0% to 100%. */
    public static final double FILL_STEP = 0.05;
    /** The slider bounds of the distortion. They are the bounds of {@link VoronoiIslands}. */
    public static final int MIN_DISTORTION = VoronoiIslands.MIN_DISTORTION;
    public static final int MAX_DISTORTION = VoronoiIslands.MAX_DISTORTION;
    public static final int MIN_DISTORTION_SCALE = VoronoiIslands.MIN_DISTORTION_SCALE;
    public static final int MAX_DISTORTION_SCALE = VoronoiIslands.MAX_DISTORTION_SCALE;
    public static final int MIN_DISTORTION_OCTAVES = VoronoiIslands.MIN_DISTORTION_OCTAVES;
    public static final int MAX_DISTORTION_OCTAVES = VoronoiIslands.MAX_DISTORTION_OCTAVES;

    private static final String EMPTY_KEY = "empty";

    private static final MapCodec<IslandVariation> ISLAND_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("density_function").forGetter(variation -> variation.densityFunction.orElseThrow()),
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(IslandVariation::biomeSource),
            withDefault(UnitRange.CODEC, "cell_weight", DEFAULT_CELL_WEIGHT).forGetter(IslandVariation::cellWeight),
            withDefault(UnitRange.CODEC, "fill", DEFAULT_FILL).forGetter(IslandVariation::fill),
            withDefault(Codec.doubleRange(MIN_DISTORTION, MAX_DISTORTION), "distortion", DEFAULT_DISTORTION).forGetter(IslandVariation::distortion),
            withDefault(Codec.intRange(MIN_DISTORTION_SCALE, MAX_DISTORTION_SCALE), "distortion_scale", DEFAULT_DISTORTION_SCALE).forGetter(IslandVariation::distortionScale),
            withDefault(Codec.intRange(MIN_DISTORTION_OCTAVES, MAX_DISTORTION_OCTAVES), "distortion_octaves", DEFAULT_DISTORTION_OCTAVES).forGetter(IslandVariation::distortionOctaves)
    ).apply(instance, IslandVariation::island));

    private static final MapCodec<IslandVariation> EMPTY_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(IslandVariation::biomeSource),
            withDefault(UnitRange.CODEC, "cell_weight", DEFAULT_CELL_WEIGHT).forGetter(IslandVariation::cellWeight)
    ).apply(instance, IslandVariation::empty));

    /** The codec of an island variation. The key {@code empty} picks the field set. */
    public static final MapCodec<IslandVariation> MAP_CODEC = new MapCodec<>() {

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.concat(Stream.of(ops.createString(EMPTY_KEY)), ISLAND_CODEC.keys(ops));
        }

        @Override
        public <T> DataResult<IslandVariation> decode(DynamicOps<T> ops, MapLike<T> input) {
            T emptyValue = input.get(EMPTY_KEY);
            DataResult<Boolean> empty = emptyValue == null ? DataResult.success(false) : Codec.BOOL.parse(ops, emptyValue);
            return empty.flatMap(isEmpty -> (isEmpty ? EMPTY_CODEC : ISLAND_CODEC).decode(ops, input));
        }

        @Override
        public <T> RecordBuilder<T> encode(IslandVariation input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            if (input.empty) {
                prefix.add(EMPTY_KEY, ops.createBoolean(true));
                return EMPTY_CODEC.encode(input, ops, prefix);
            }
            return ISLAND_CODEC.encode(input, ops, prefix);
        }
    };
    public static final Codec<IslandVariation> CODEC = MAP_CODEC.codec();

    public IslandVariation {
        if (empty == densityFunction.isPresent()) {
            throw new IllegalArgumentException(empty ? "An Empty variation holds no density function" : "An island holds a density function");
        }
    }

    /** An island with its density function. */
    public static IslandVariation island(Holder<DensityFunction> densityFunction, BiomeSource biomeSource, UnitRange cellWeight, UnitRange fill,
                                         double distortion, int distortionScale, int distortionOctaves) {
        return new IslandVariation(false, Optional.of(densityFunction), biomeSource, cellWeight, fill, distortion, distortionScale, distortionOctaves);
    }

    /** An Empty variation. Its fill and distortion fields keep the defaults, and the codec does not write them. */
    public static IslandVariation empty(BiomeSource biomeSource, UnitRange cellWeight) {
        return new IslandVariation(true, Optional.empty(), biomeSource, cellWeight, DEFAULT_FILL, DEFAULT_DISTORTION, DEFAULT_DISTORTION_SCALE, DEFAULT_DISTORTION_OCTAVES);
    }

    /** A field that reads its default when the key is absent. The encode writes the value, also when it equals the default. */
    private static <A> MapCodec<A> withDefault(Codec<A> codec, String name, A defaultValue) {
        return codec.optionalFieldOf(name).xmap(value -> value.orElse(defaultValue), Optional::of);
    }

}
