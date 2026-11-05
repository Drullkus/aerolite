package us.drullk.aerolite.datagen.shaft;

import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

/**
 * The shipped shaft data of the End.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
@Component
public final class EndShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> END_BRICKS = variation("end_bricks");
    public static final ResourceKey<ShaftVariation> END_MAZE = variation("end_maze");
    public static final ResourceKey<ShaftVariation> PURPUR_PILLAR = variation("purpur_pillar");

    private static final int CHUNK = 16;

    public EndShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return Level.END;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(4 * CHUNK, 16 * CHUNK, 0.9f);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        context.register(END_BRICKS, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                128,
                0.35f,
                0.5f,
                new BricksSettings(BricksSettings.Size.LARGE, new SurfacePathSettings(false, 0f, true, true, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE_BRICKS),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE)
        ));
        context.register(END_MAZE, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                128,
                0.35f,
                0.5f,
                new MazeSettings(MazeSettings.Type.GILBERT, 0.95f, MazeSettings.Seam.RANDOM, MazeSettings.MIN_SPACING),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE_BRICKS),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE),
                BlockStateProvider.simple(Blocks.END_STONE)
        ));
        context.register(PURPUR_PILLAR, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                256,
                0.7f,
                0.85f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, true, new SurfacePathSettings(true, 1f, true, false, 0.25f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.OBSIDIAN),
                BlockStateProvider.simple(Blocks.OBSIDIAN),
                BlockStateProvider.simple(Blocks.PURPUR_BLOCK),
                BlockStateProvider.simple(Blocks.CRYING_OBSIDIAN),
                BlockStateProvider.simple(Blocks.PURPLE_STAINED_GLASS),
                BlockStateProvider.simple(Blocks.OBSIDIAN)
        ));
    }

    @Override
    public VariationList variationList() {
        return list(entry(END_BRICKS, 16), entry(END_MAZE, 3), entry(PURPUR_PILLAR, 1));
    }

}
