package us.drullk.aerolite.world;

import com.google.common.collect.ImmutableMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.List;
import java.util.Objects;

/**
 * Fixed settings and variations for tests. They do not follow the production defaults.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_18-08_4c0300c8-1a76-4b8e-86b0-0cb9daa7d32a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 */
public final class TestSettings {

    public static final int DECAY_SCALE = 256;
    public static final float DECAY_THRESHOLD = 0.5F;
    public static final float EROSION_THRESHOLD = 0.7F;

    public static final ShaftPlacement PLACEMENT = new ShaftPlacement(128, 384, 0.25F);

    public static final ShaftVariation STONE = new ShaftVariation(ShaftVariation.UNIFORM_BIAS, DECAY_SCALE, DECAY_THRESHOLD, EROSION_THRESHOLD,
            BlockStateProvider.simple(Blocks.STONE), BlockStateProvider.simple(Blocks.STONE), BlockStateProvider.simple(Blocks.STONE_BRICKS), BlockStateProvider.simple(Blocks.COBBLESTONE));
    public static final ShaftVariation TUFF = new ShaftVariation(ShaftVariation.UNIFORM_BIAS, DECAY_SCALE, DECAY_THRESHOLD, EROSION_THRESHOLD,
            BlockStateProvider.simple(Blocks.TUFF), BlockStateProvider.simple(Blocks.TUFF), BlockStateProvider.simple(Blocks.TUFF_BRICKS), BlockStateProvider.simple(Blocks.OXIDIZED_COPPER));
    public static final ShaftVariation NETHER = new ShaftVariation(ShaftVariation.UNIFORM_BIAS, DECAY_SCALE, DECAY_THRESHOLD, EROSION_THRESHOLD,
            BlockStateProvider.simple(Blocks.NETHERRACK), BlockStateProvider.simple(Blocks.NETHERRACK), BlockStateProvider.simple(Blocks.NETHER_BRICKS), BlockStateProvider.simple(Blocks.RED_NETHER_BRICKS));
    public static final ShaftVariation END = new ShaftVariation(ShaftVariation.UNIFORM_BIAS, DECAY_SCALE, DECAY_THRESHOLD, EROSION_THRESHOLD,
            BlockStateProvider.simple(Blocks.END_STONE), BlockStateProvider.simple(Blocks.END_STONE), BlockStateProvider.simple(Blocks.END_STONE_BRICKS), BlockStateProvider.simple(Blocks.PURPUR_BLOCK));

    /** The Overworld settings: stone with weight 7 and tuff with weight 1. */
    public static final DimensionSettings OVERWORLD = new DimensionSettings(PLACEMENT, List.of(new WeightedVariation(7, STONE), new WeightedVariation(1, TUFF)));

    /** Two Overworld variations, one Nether variation and one End variation. */
    public static final AeroliteSettings SETTINGS = new AeroliteSettings(
            -32,
            ImmutableMap.of(
                    Level.OVERWORLD, OVERWORLD,
                    Level.NETHER, new DimensionSettings(PLACEMENT, List.of(one(NETHER))),
                    Level.END, new DimensionSettings(PLACEMENT, List.of(one(END)))
            )
    );

    private TestSettings() {
    }

    /** The state of a simple provider. The test variations hold only simple providers. */
    public static BlockState state(BlockStateProvider provider) {
        return Objects.requireNonNull(StateProviders.simpleState(provider), "The provider is not a simple provider");
    }

    /** The variation with weight 1. */
    public static WeightedVariation one(ShaftVariation variation) {
        return new WeightedVariation(1, variation);
    }

    /** The settings with the decay values set on every variation. */
    public static DimensionSettings withDecay(DimensionSettings settings, int scale, float decayThreshold, float erosionThreshold) {
        return settings.mapVariations(variation -> variation.withDecayScale(scale).withDecayThreshold(decayThreshold).withErosionThreshold(erosionThreshold));
    }

}
