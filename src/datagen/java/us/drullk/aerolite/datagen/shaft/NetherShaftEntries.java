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
 * The shipped shaft data of the Nether.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
@Component
public final class NetherShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> NETHER_BRICKS = variation("nether_bricks");
    public static final ResourceKey<ShaftVariation> BLACKSTONE_RIBS = variation("blackstone_ribs");
    public static final ResourceKey<ShaftVariation> SOUL_CONDUIT = variation("soul_conduit");
    public static final ResourceKey<ShaftVariation> QUARTZ_BEAM = variation("quartz_beam");

    private static final int CHUNK = 16;

    public NetherShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return Level.NETHER;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(6 * CHUNK, 12 * CHUNK, 0.7f);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        context.register(NETHER_BRICKS, new ShaftVariation(
                0.25F,
                32,
                0.35f,
                0.4f,
                new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0f, true, false, 1f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.NETHERRACK),
                BlockStateProvider.simple(Blocks.NETHERRACK),
                BlockStateProvider.simple(Blocks.NETHER_BRICKS),
                BlockStateProvider.simple(Blocks.NETHER_WART_BLOCK),
                BlockStateProvider.simple(Blocks.RED_NETHER_BRICKS),
                BlockStateProvider.simple(Blocks.NETHERRACK)
        ));
        context.register(BLACKSTONE_RIBS, new ShaftVariation(
                0.1F,
                64,
                0.35f,
                0.6f,
                new MazeSettings(MazeSettings.Type.RIBS, 0.95f, MazeSettings.Seam.RANDOM, MazeSettings.MIN_SPACING),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.BLACKSTONE),
                BlockStateProvider.simple(Blocks.BLACKSTONE),
                BlockStateProvider.simple(Blocks.POLISHED_BLACKSTONE_BRICKS),
                BlockStateProvider.simple(Blocks.POLISHED_BLACKSTONE),
                BlockStateProvider.simple(Blocks.BLACKSTONE),
                BlockStateProvider.simple(Blocks.BLACKSTONE)
        ));
        context.register(SOUL_CONDUIT, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                256,
                0.5f,
                0.65f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.X, false, new SurfacePathSettings(false, 0f, true, true, 0.25f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.SOUL_SOIL),
                BlockStateProvider.simple(Blocks.SOUL_SOIL),
                BlockStateProvider.simple(Blocks.SOUL_SAND),
                BlockStateProvider.simple(Blocks.WARPED_WART_BLOCK),
                BlockStateProvider.simple(Blocks.SOUL_SOIL),
                BlockStateProvider.simple(Blocks.SOUL_SOIL)
        ));
        context.register(QUARTZ_BEAM, new ShaftVariation(
                0.8F,
                128,
                0.5f,
                0.9f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, true, new SurfacePathSettings(true, 1f, true, false, 0.9f)),
                PillarShape.I_BEAM,
                BlockStateProvider.simple(Blocks.SMOOTH_QUARTZ),
                BlockStateProvider.simple(Blocks.SMOOTH_QUARTZ),
                BlockStateProvider.simple(Blocks.QUARTZ_BRICKS),
                BlockStateProvider.simple(Blocks.QUARTZ_BLOCK),
                BlockStateProvider.simple(Blocks.GLOWSTONE),
                BlockStateProvider.simple(Blocks.SMOOTH_QUARTZ)
        ));
    }

    @Override
    public VariationList variationList() {
        return list(entry(NETHER_BRICKS, 16), entry(BLACKSTONE_RIBS, 9), entry(SOUL_CONDUIT, 5), entry(QUARTZ_BEAM, 1));
    }

}
