package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import us.drullk.aerolite.world.pattern.PatternSettings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One set of shaft block providers, maze bias, decay values, pattern and shape. The registry {@code aerolite:shaft_variation} holds the shipped variations.
 * Two variations are equal when {@link StateProviders#same} holds for each provider and the other fields are equal.
 * The defaults of the pattern, the shape, the surface provider and the trusses provider give the shafts of 0.3.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param bias             the weight of axis edges in the maze sampler; ring edges get one minus this weight
 * @param decayScale       the size of one decay region along a shaft, a power of two
 * @param decayThreshold   decay noise below the negative threshold keeps only maze walls, above it only maze paths
 * @param erosionThreshold decay noise beyond this threshold in either direction erodes the shaft and its junction boxes
 * @param pattern          the surface pattern and its options
 * @param shape            the cross section of the shafts
 * @param interior         the provider of the shaft interior block
 * @param junction         the provider of the block of the bezel of a junction
 * @param wall             the provider of the maze wall block
 * @param path             the provider of the maze path block
 * @param surface          the provider of the surface path block
 * @param trusses          the provider of the truss block of a bridge
 */
public record ShaftVariation(float bias, int decayScale, float decayThreshold, float erosionThreshold, PatternSettings pattern, PillarShape shape,
                             BlockStateProvider interior, BlockStateProvider junction, BlockStateProvider wall, BlockStateProvider path,
                             BlockStateProvider surface, BlockStateProvider trusses) {

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

    /** The keys of the fields in their JSON sequence. */
    public static final List<String> KEYS = List.of("bias", "decay_scale", "decay_threshold", "erosion_threshold", "pattern", "shape",
            "interior", "junction", "wall", "path", "surface", "trusses");

    public static final MapCodec<ShaftVariation> MAP_CODEC = mapCodec(DEFAULT_DECAY_SCALE, DEFAULT_DECAY_THRESHOLD, DEFAULT_EROSION_THRESHOLD);

    /** The codec of one entry of the registry {@code aerolite:shaft_variation}. */
    public static final Codec<ShaftVariation> CODEC = MAP_CODEC.codec();

    /** A variation with the 0.3 fields. It takes the default pattern and shape, its path provider as surface and its interior provider as trusses. */
    public ShaftVariation(float bias, int decayScale, float decayThreshold, float erosionThreshold,
                          BlockStateProvider interior, BlockStateProvider junction, BlockStateProvider wall, BlockStateProvider path) {
        this(bias, decayScale, decayThreshold, erosionThreshold, PatternSettings.standard(), PillarShape.PILLAR, interior, junction, wall, path, path, interior);
    }

    /**
     * The variation fields, whose missing decay fields take the given values. The fields are always written, in the sequence of {@link #KEYS}.
     * Each block field reads a provider object or a bare block state. A missing junction provider takes the interior provider.
     * A missing surface provider takes the path provider. A missing trusses provider takes the interior provider.
     * A missing pattern is the uniform maze. A missing shape is the pillar.
     * A save whose erosion threshold is not above its decay threshold loads with the erosion threshold one step above it.
     */
    public static MapCodec<ShaftVariation> mapCodec(int decayScale, float decayThreshold, float erosionThreshold) {
        return AeroliteCodecs.sequenced(RecordCodecBuilder.<ShaftVariation>mapCodec(instance -> instance.group(
                AeroliteCodecs.defaultedField(BIAS_CODEC, "bias", UNIFORM_BIAS).forGetter(ShaftVariation::bias),
                AeroliteCodecs.defaultedField(DECAY_SCALE_CODEC, "decay_scale", decayScale).forGetter(ShaftVariation::decayScale),
                AeroliteCodecs.defaultedField(Codec.floatRange(0, 1), "decay_threshold", decayThreshold).forGetter(ShaftVariation::decayThreshold),
                AeroliteCodecs.defaultedField(Codec.floatRange(THRESHOLD_STEP, 1), "erosion_threshold", erosionThreshold).forGetter(ShaftVariation::erosionThreshold),
                AeroliteCodecs.defaultedField(PatternSettings.CODEC, "pattern", PatternSettings.standard()).forGetter(ShaftVariation::pattern),
                AeroliteCodecs.defaultedField(PillarShape.CODEC, "shape", PillarShape.PILLAR).forGetter(ShaftVariation::shape),
                StateProviders.CODEC.fieldOf("interior").forGetter(ShaftVariation::interior),
                StateProviders.CODEC.optionalFieldOf("junction").forGetter(variation -> Optional.of(variation.junction())),
                StateProviders.CODEC.fieldOf("wall").forGetter(ShaftVariation::wall),
                StateProviders.CODEC.fieldOf("path").forGetter(ShaftVariation::path),
                StateProviders.CODEC.optionalFieldOf("surface").forGetter(variation -> Optional.of(variation.surface())),
                StateProviders.CODEC.optionalFieldOf("trusses").forGetter(variation -> Optional.of(variation.trusses()))
        ).apply(instance, (bias, scale, decay, erosion, pattern, shape, interior, junction, wall, path, surface, trusses) ->
                new ShaftVariation(bias, scale, decay, erosion, pattern, shape, interior, junction.orElse(interior), wall, path,
                        surface.orElse(path), trusses.orElse(interior)))).xmap(ShaftVariation::withErosionAboveDecay, variation -> variation), KEYS);
    }

    /** A variation with the default decay values, pattern and shape. Its junction and trusses providers are its interior provider. Its surface provider is its path provider. */
    public static ShaftVariation standard(float bias, BlockStateProvider interior, BlockStateProvider wall, BlockStateProvider path) {
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
        return new ShaftVariation(bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, this.interior, this.junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withDecayScale(int scale) {
        return new ShaftVariation(this.bias, scale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, this.interior, this.junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withDecayThreshold(float threshold) {
        return new ShaftVariation(this.bias, this.decayScale, threshold, this.erosionThreshold, this.pattern, this.shape, this.interior, this.junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withErosionThreshold(float threshold) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, threshold, this.pattern, this.shape, this.interior, this.junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withPattern(PatternSettings pattern) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, pattern, this.shape, this.interior, this.junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withShape(PillarShape shape) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, shape, this.interior, this.junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withInterior(BlockStateProvider interior) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, interior, this.junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withInterior(BlockState interior) {
        return this.withInterior(BlockStateProvider.simple(interior));
    }

    public ShaftVariation withJunction(BlockStateProvider junction) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, this.interior, junction, this.wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withJunction(BlockState junction) {
        return this.withJunction(BlockStateProvider.simple(junction));
    }

    public ShaftVariation withWall(BlockStateProvider wall) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, this.interior, this.junction, wall, this.path, this.surface, this.trusses);
    }

    public ShaftVariation withWall(BlockState wall) {
        return this.withWall(BlockStateProvider.simple(wall));
    }

    public ShaftVariation withPath(BlockStateProvider path) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, this.interior, this.junction, this.wall, path, this.surface, this.trusses);
    }

    public ShaftVariation withPath(BlockState path) {
        return this.withPath(BlockStateProvider.simple(path));
    }

    public ShaftVariation withSurface(BlockStateProvider surface) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, this.interior, this.junction, this.wall, this.path, surface, this.trusses);
    }

    public ShaftVariation withSurface(BlockState surface) {
        return this.withSurface(BlockStateProvider.simple(surface));
    }

    public ShaftVariation withTrusses(BlockStateProvider trusses) {
        return new ShaftVariation(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape, this.interior, this.junction, this.wall, this.path, this.surface, trusses);
    }

    public ShaftVariation withTrusses(BlockState trusses) {
        return this.withTrusses(BlockStateProvider.simple(trusses));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ShaftVariation variation
                && Float.compare(this.bias, variation.bias) == 0
                && this.decayScale == variation.decayScale
                && Float.compare(this.decayThreshold, variation.decayThreshold) == 0
                && Float.compare(this.erosionThreshold, variation.erosionThreshold) == 0
                && this.pattern.equals(variation.pattern)
                && this.shape == variation.shape
                && StateProviders.same(this.interior, variation.interior)
                && StateProviders.same(this.junction, variation.junction)
                && StateProviders.same(this.wall, variation.wall)
                && StateProviders.same(this.path, variation.path)
                && StateProviders.same(this.surface, variation.surface)
                && StateProviders.same(this.trusses, variation.trusses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.bias, this.decayScale, this.decayThreshold, this.erosionThreshold, this.pattern, this.shape,
                StateProviders.hash(this.interior), StateProviders.hash(this.junction), StateProviders.hash(this.wall), StateProviders.hash(this.path),
                StateProviders.hash(this.surface), StateProviders.hash(this.trusses));
    }

}
