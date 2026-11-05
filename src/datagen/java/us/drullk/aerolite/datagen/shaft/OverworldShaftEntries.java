package us.drullk.aerolite.datagen.shaft;

import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

/**
 * The shipped shaft data of the Overworld.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
@Component
public final class OverworldShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> STONE = ShaftRegistries.STONE;
    public static final ResourceKey<ShaftVariation> MOSSY_MAZE = variation("mossy_maze");
    public static final ResourceKey<ShaftVariation> TUFF_BRICKS = variation("tuff_bricks");
    public static final ResourceKey<ShaftVariation> DEEPSLATE = variation("deepslate");
    public static final ResourceKey<ShaftVariation> COPPER_BRIDGE = variation("copper_bridge");
    public static final ResourceKey<ShaftVariation> MUD_TILES = variation("mud_tiles");
    public static final ResourceKey<ShaftVariation> ANDESITE = variation("andesite");
    public static final ResourceKey<ShaftVariation> DIORITE = variation("diorite");
    public static final ResourceKey<ShaftVariation> GRANITE = variation("granite");
    public static final ResourceKey<ShaftVariation> PRISMARINE_SCALES = variation("prismarine_scales");

    public OverworldShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return Level.OVERWORLD;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(64, 256, 0.3F);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        register(context, STONE, ShaftVariation.UNIFORM_BIAS, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE);
        context.register(MOSSY_MAZE, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.2f,
                0.6f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.X, false, new SurfacePathSettings(false, 0f, true, true, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.MOSSY_COBBLESTONE),
                BlockStateProvider.simple(Blocks.MOSSY_COBBLESTONE),
                BlockStateProvider.simple(Blocks.MOSSY_STONE_BRICKS),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.MOSSY_COBBLESTONE)
        ));
        context.register(TUFF_BRICKS, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                128,
                0.5f,
                0.85f,
                new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0f, true, true, 1f)),
                PillarShape.I_BEAM,
                BlockStateProvider.simple(Blocks.TUFF),
                BlockStateProvider.simple(Blocks.TUFF),
                BlockStateProvider.simple(Blocks.TUFF_BRICKS),
                BlockStateProvider.simple(Blocks.POLISHED_TUFF),
                BlockStateProvider.simple(Blocks.POLISHED_TUFF),
                BlockStateProvider.simple(Blocks.TUFF)
        ));
        register(context, DEEPSLATE, 0.3F, Blocks.DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE);
        context.register(COPPER_BRIDGE, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                128,
                0.85f,
                0.95f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, false, new SurfacePathSettings(false, 0f, true, true, 1f)),
                PillarShape.BRIDGE,
                BlockStateProvider.simple(Blocks.OXIDIZED_COPPER),
                BlockStateProvider.simple(Blocks.OXIDIZED_COPPER),
                BlockStateProvider.simple(Blocks.OXIDIZED_CUT_COPPER),
                BlockStateProvider.simple(Blocks.OXIDIZED_COPPER_BULB),
                BlockStateProvider.simple(Blocks.OXIDIZED_CHISELED_COPPER),
                BlockStateProvider.simple(Blocks.WAXED_EXPOSED_COPPER)
        ));
        context.register(MUD_TILES, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.2f,
                0.6f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.Y, false, new SurfacePathSettings(false, 0f, true, true, 1f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.PACKED_MUD),
                BlockStateProvider.simple(Blocks.PACKED_MUD),
                BlockStateProvider.simple(Blocks.MUD_BRICKS),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.MOSS_BLOCK),
                BlockStateProvider.simple(Blocks.PACKED_MUD)
        ));
        register(context, ANDESITE, ShaftVariation.UNIFORM_BIAS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE);
        register(context, DIORITE, ShaftVariation.UNIFORM_BIAS, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE);
        register(context, GRANITE, ShaftVariation.UNIFORM_BIAS, Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.POLISHED_ANDESITE);
        context.register(PRISMARINE_SCALES, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                64,
                0.35f,
                0.5f,
                new ScalesSettings(new SurfacePathSettings(true, 1f, true, false, 0.35f), true),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE_BRICKS),
                BlockStateProvider.simple(Blocks.DARK_PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE),
                BlockStateProvider.simple(Blocks.PRISMARINE)
        ));
    }


    private static void register(BootstrapContext<ShaftVariation> context, ResourceKey<ShaftVariation> key, float bias, Block interior, Block wall, Block path) {
        context.register(key, ShaftVariation.standard(bias, BlockStateProvider.simple(interior), BlockStateProvider.simple(wall), BlockStateProvider.simple(path)));
    }

    @Override
    public VariationList variationList() {
        return list(
                entry(STONE, 16),
                entry(MOSSY_MAZE, 4),
                entry(TUFF_BRICKS, 4),
                entry(DEEPSLATE, 2),
                entry(COPPER_BRIDGE, 1),
                entry(MUD_TILES, 1),
                entry(ANDESITE, 1),
                entry(DIORITE, 1),
                entry(GRANITE, 1),
                entry(PRISMARINE_SCALES, 1)
        );
    }

}
