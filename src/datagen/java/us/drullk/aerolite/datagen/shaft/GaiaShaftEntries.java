package us.drullk.aerolite.datagen.shaft;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.*;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.List;
import java.util.Optional;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

@Component
public final class GaiaShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> GAIA_AMETHYST_PILLAR = variation("gaia_amethyst_pillar");
    public static final ResourceKey<ShaftVariation> GAIA_COPAL_PILLAR = variation("gaia_copal_pillar");
    public static final ResourceKey<ShaftVariation> GAIA_JADE_PILLAR = variation("gaia_jade_pillar");
    public static final ResourceKey<ShaftVariation> GAIA_JET_PILLAR = variation("gaia_jet_pillar");
    public static final ResourceKey<ShaftVariation> GAIA_MALACHITE_PILLAR = variation("gaia_malachite_pillar");

    private static final int CHUNK = 16;

    public GaiaShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return ModdedDimensions.GAIA;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(8 * CHUNK, 32 * CHUNK, 0.75f);
    }

    @Override
    public Optional<String> requiredMod() {
        return Optional.of("gaiadimension");
    }

    @Override
    public List<ResourceKey<ShaftVariation>> modVariations() {
        return List.of(GAIA_AMETHYST_PILLAR, GAIA_COPAL_PILLAR, GAIA_JADE_PILLAR, GAIA_JET_PILLAR, GAIA_MALACHITE_PILLAR);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        context.register(GAIA_AMETHYST_PILLAR, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.6f,
                0.7f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.X, false, new SurfacePathSettings(false, 0f, true, false, 0.5f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_amethyst")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_amethyst")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "amethyst_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "blue_agate_tiles")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "crusted_amethyst_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_amethyst"))
        ));
        context.register(GAIA_COPAL_PILLAR, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.6f,
                0.7f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.Y, false, new SurfacePathSettings(false, 0f, true, false, 0.5f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_copal")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_copal")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "copal_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "fossilized_tiles")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "crusted_copal_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_copal"))
        ));
        context.register(GAIA_JADE_PILLAR, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.6f,
                0.7f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, false, new SurfacePathSettings(false, 0f, true, false, 0.5f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_jade")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_jade")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "jade_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "green_agate_tiles")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "crusted_jade_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_jade"))
        ));
        context.register(GAIA_JET_PILLAR, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.6f,
                0.7f,
                new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0f, true, false, 0.5f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_jet")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_jet")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "jet_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "burnt_agate_tiles")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "crusted_jet_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "raw_jet"))
        ));
        context.register(GAIA_MALACHITE_PILLAR, new ShaftVariation(
                ShaftVariation.MIN_BIAS,
                32,
                0.6f,
                0.7f,
                new MazeSettings(MazeSettings.Type.RIBS, 0.95f, MazeSettings.Seam.RANDOM, 1),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "malachite_tiles")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "malachite_tiles")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "malachite_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "malachite_pulsing_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "malachite_pulsing_chisel")),
                BlockStateProvider.simple(getBlockState(blocks, "gaiadimension", "malachite_tiles"))
        ));
    }

    @Override
    public VariationList variationList() {
        return list(
                entry(GAIA_AMETHYST_PILLAR, 4),
                entry(GAIA_COPAL_PILLAR, 4),
                entry(GAIA_JADE_PILLAR, 4),
                entry(GAIA_JET_PILLAR, 4),
                entry(GAIA_MALACHITE_PILLAR, 1)
        );
    }

}
