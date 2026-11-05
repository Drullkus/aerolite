package us.drullk.aerolite.datagen.shaft;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.*;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.List;
import java.util.Optional;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

@Component
public final class BumblezoneShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> BIG_COMB_PILLAR = variation("big_comb_pillar");
    public static final ResourceKey<ShaftVariation> SMALL_COMB_PILLAR = variation("small_comb_pillar");

    private static final int CHUNK = 16;

    public BumblezoneShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return ModdedDimensions.BUMBLEZONE;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(8 * CHUNK, 16 * CHUNK, 0.1f);
    }

    @Override
    public Optional<String> requiredMod() {
        return Optional.of("the_bumblezone");
    }

    @Override
    public List<ResourceKey<ShaftVariation>> modVariations() {
        return List.of(BIG_COMB_PILLAR, SMALL_COMB_PILLAR);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        context.register(BIG_COMB_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                64,
                0.5f,
                0.65f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.Y, false, new SurfacePathSettings(true, 1f, true, false, 1f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK),
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK),
                BlockStateProvider.simple(getBlockState(blocks, "the_bumblezone", "porous_honeycomb_block")),
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK),
                BlockStateProvider.simple(getBlockState(blocks, "the_bumblezone", "filled_porous_honeycomb_block")),
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK)
        ));
        context.register(SMALL_COMB_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                64,
                0.5f,
                0.65f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.X, false, new SurfacePathSettings(true, 1f, true, false, 1f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK),
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK),
                BlockStateProvider.simple(getBlockState(blocks, "the_bumblezone", "porous_honeycomb_block")),
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK),
                BlockStateProvider.simple(getBlockState(blocks, "the_bumblezone", "filled_porous_honeycomb_block")),
                BlockStateProvider.simple(Blocks.HONEYCOMB_BLOCK)
        ));
    }

    @Override
    public VariationList variationList() {
        return list(
                entry(BIG_COMB_PILLAR, 1),
                entry(SMALL_COMB_PILLAR, 5)
        );
    }

}
