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
 * @param shaftSpacing   the mean distance between horizontal shafts along one axis
 * @param columnChance   the chance that a crossing of two shafts in plan view gets a column
 * @param decayScale     the size of one decay region along a shaft, a power of two
 * @param decayThreshold decay noise below the negative threshold keeps only maze walls, above it only maze paths
 */
public record AeroliteSettings(
        int shaftSpacing,
        float columnChance,
        int decayScale,
        float decayThreshold,
        BlockState interiorBlock,
        BlockState wallBlock,
        BlockState pathBlock
) {

    public static final int MIN_SHAFT_SPACING = 32;
    public static final int MAX_SHAFT_SPACING = 4096;
    public static final int MIN_DECAY_SCALE = 32;
    public static final int MAX_DECAY_SCALE = 1024;

    public static final AeroliteSettings DEFAULT = new AeroliteSettings(
            256,
            0.25F,
            256,
            0.5F,
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState()
    );

    private static final Codec<Integer> DECAY_SCALE_CODEC = Codec.intRange(MIN_DECAY_SCALE, MAX_DECAY_SCALE).validate(scale -> Mth.isPowerOfTwo(scale)
            ? DataResult.success(scale)
            : DataResult.error(() -> "Decay scale is not a power of two: " + scale));

    public static final Codec<AeroliteSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(MIN_SHAFT_SPACING, MAX_SHAFT_SPACING).optionalFieldOf("shaft_spacing", DEFAULT.shaftSpacing).forGetter(AeroliteSettings::shaftSpacing),
            Codec.floatRange(0, 1).optionalFieldOf("column_chance", DEFAULT.columnChance).forGetter(AeroliteSettings::columnChance),
            DECAY_SCALE_CODEC.optionalFieldOf("decay_scale", DEFAULT.decayScale).forGetter(AeroliteSettings::decayScale),
            Codec.floatRange(0, 1).optionalFieldOf("decay_threshold", DEFAULT.decayThreshold).forGetter(AeroliteSettings::decayThreshold),
            BlockState.CODEC.optionalFieldOf("interior_block", DEFAULT.interiorBlock).forGetter(AeroliteSettings::interiorBlock),
            BlockState.CODEC.optionalFieldOf("wall_block", DEFAULT.wallBlock).forGetter(AeroliteSettings::wallBlock),
            BlockState.CODEC.optionalFieldOf("path_block", DEFAULT.pathBlock).forGetter(AeroliteSettings::pathBlock)
    ).apply(instance, AeroliteSettings::new));

    public AeroliteSettings withDecayScale(int scale) {
        return new AeroliteSettings(this.shaftSpacing, this.columnChance, scale, this.decayThreshold, this.interiorBlock, this.wallBlock, this.pathBlock);
    }

    public AeroliteSettings withDecayThreshold(float threshold) {
        return new AeroliteSettings(this.shaftSpacing, this.columnChance, this.decayScale, threshold, this.interiorBlock, this.wallBlock, this.pathBlock);
    }

}
