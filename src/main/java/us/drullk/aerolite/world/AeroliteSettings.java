package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The settings of the preset. The player sets them in the world creation menu.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 *
 * @param minShaftSpacing the smallest center distance of two neighbor horizontal shafts on one axis
 * @param maxShaftSpacing the largest center distance of two neighbor horizontal shafts on one axis
 * @param columnChance    the chance that a crossing of two shafts in plan view gets a column
 * @param decayScale      the size of one decay region along a shaft, a power of two
 * @param decayThreshold  decay noise below the negative threshold keeps only maze walls, above it only maze paths
 * @param erosionThreshold decay noise beyond this threshold in either direction erodes shafts and junction boxes
 */
public record AeroliteSettings(
        int minShaftSpacing,
        int maxShaftSpacing,
        float columnChance,
        int decayScale,
        float decayThreshold,
        float erosionThreshold,
        BlockState interiorBlock,
        BlockState wallBlock,
        BlockState pathBlock
) {

    public static final int MIN_SHAFT_SPACING = 32;
    public static final int MAX_SHAFT_SPACING = 1024;
    public static final int MIN_DECAY_SCALE = 32;
    public static final int MAX_DECAY_SCALE = 1024;
    /** The smallest step from the decay threshold to the erosion threshold. */
    public static final float THRESHOLD_STEP = 0.05F;

    public static final AeroliteSettings DEFAULT = new AeroliteSettings(
            128,
            384,
            0.25F,
            256,
            0.5F,
            0.7F,
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState()
    );

    private static final Codec<Integer> DECAY_SCALE_CODEC = Codec.intRange(MIN_DECAY_SCALE, MAX_DECAY_SCALE).validate(scale -> Mth.isPowerOfTwo(scale)
            ? DataResult.success(scale)
            : DataResult.error(() -> "Decay scale is not a power of two: " + scale));

    private static final Codec<Integer> SPACING_CODEC = Codec.intRange(MIN_SHAFT_SPACING, MAX_SHAFT_SPACING);

    public static final Codec<AeroliteSettings> CODEC = RecordCodecBuilder.<AeroliteSettings>create(instance -> instance.group(
            SPACING_CODEC.optionalFieldOf("min_shaft_spacing", DEFAULT.minShaftSpacing).forGetter(AeroliteSettings::minShaftSpacing),
            SPACING_CODEC.optionalFieldOf("max_shaft_spacing", DEFAULT.maxShaftSpacing).forGetter(AeroliteSettings::maxShaftSpacing),
            Codec.floatRange(0, 1).optionalFieldOf("column_chance", DEFAULT.columnChance).forGetter(AeroliteSettings::columnChance),
            DECAY_SCALE_CODEC.optionalFieldOf("decay_scale", DEFAULT.decayScale).forGetter(AeroliteSettings::decayScale),
            Codec.floatRange(0, 1).optionalFieldOf("decay_threshold", DEFAULT.decayThreshold).forGetter(AeroliteSettings::decayThreshold),
            Codec.floatRange(THRESHOLD_STEP, 1).optionalFieldOf("erosion_threshold", DEFAULT.erosionThreshold).forGetter(AeroliteSettings::erosionThreshold),
            BlockState.CODEC.optionalFieldOf("interior_block", DEFAULT.interiorBlock).forGetter(AeroliteSettings::interiorBlock),
            BlockState.CODEC.optionalFieldOf("wall_block", DEFAULT.wallBlock).forGetter(AeroliteSettings::wallBlock),
            BlockState.CODEC.optionalFieldOf("path_block", DEFAULT.pathBlock).forGetter(AeroliteSettings::pathBlock)
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
        return new AeroliteSettings(min, max, this.columnChance, this.decayScale, this.decayThreshold, this.erosionThreshold, this.interiorBlock, this.wallBlock, this.pathBlock);
    }

    public AeroliteSettings withColumnChance(float chance) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, chance, this.decayScale, this.decayThreshold, this.erosionThreshold, this.interiorBlock, this.wallBlock, this.pathBlock);
    }

    public AeroliteSettings withDecayScale(int scale) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, scale, this.decayThreshold, this.erosionThreshold, this.interiorBlock, this.wallBlock, this.pathBlock);
    }

    public AeroliteSettings withErosionThreshold(float threshold) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, this.decayScale, this.decayThreshold, threshold, this.interiorBlock, this.wallBlock, this.pathBlock);
    }

    public AeroliteSettings withDecayThreshold(float threshold) {
        return new AeroliteSettings(this.minShaftSpacing, this.maxShaftSpacing, this.columnChance, this.decayScale, threshold, this.erosionThreshold, this.interiorBlock, this.wallBlock, this.pathBlock);
    }

}
