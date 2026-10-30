package us.drullk.aerolite.world;

import com.google.common.collect.ImmutableMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Fixed settings and variations for tests. They do not follow the production defaults.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_18-08_4c0300c8-1a76-4b8e-86b0-0cb9daa7d32a.md}.
 */
public final class TestSettings {

    public static final ShaftVariation STONE = new ShaftVariation(7, ShaftVariation.UNIFORM_BIAS,
            Blocks.STONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
    public static final ShaftVariation TUFF = new ShaftVariation(1, ShaftVariation.UNIFORM_BIAS,
            Blocks.TUFF.defaultBlockState(), Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.OXIDIZED_COPPER.defaultBlockState());
    public static final ShaftVariation NETHER = new ShaftVariation(1, ShaftVariation.UNIFORM_BIAS,
            Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState());
    public static final ShaftVariation END = new ShaftVariation(1, ShaftVariation.UNIFORM_BIAS,
            Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState());

    /** Two Overworld variations, one Nether variation and one End variation. */
    public static final AeroliteSettings SETTINGS = new AeroliteSettings(
            128,
            384,
            0.25F,
            256,
            0.5F,
            0.7F,
            -32,
            ImmutableMap.of(
                    Level.OVERWORLD, List.of(STONE, TUFF),
                    Level.NETHER, List.of(NETHER),
                    Level.END, List.of(END)
            )
    );

    private TestSettings() {
    }

}
