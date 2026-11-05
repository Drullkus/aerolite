package us.drullk.aerolite.datagen.shaft;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseBasedStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.*;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.List;
import java.util.Optional;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

@Component
public final class TwilightForestShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> DEADROCK_PILLAR = variation("deadrock_pillar");
    public static final ResourceKey<ShaftVariation> UNDERBRICK_PILLAR = variation("underbrick_pillar");

    private static final int CHUNK = 16;

    public TwilightForestShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return ModdedDimensions.TWILIGHT_FOREST;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(8 * CHUNK, 16 * CHUNK, 0.65f);
    }

    @Override
    public Optional<String> requiredMod() {
        return Optional.of("twilightforest");
    }

    @Override
    public List<ResourceKey<ShaftVariation>> modVariations() {
        return List.of(DEADROCK_PILLAR, UNDERBRICK_PILLAR);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        context.register(DEADROCK_PILLAR, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                32,
                0.1f,
                0.2f,
                new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0f, true, false, 1f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "twilightforest", "deadrock")),
                BlockStateProvider.simple(getBlockState(blocks, "twilightforest", "deadrock")),
                BlockStateProvider.simple(getBlockState(blocks, "twilightforest", "weathered_deadrock")),
                BlockStateProvider.simple(getBlockState(blocks, "twilightforest", "cracked_deadrock")),
                BlockStateProvider.simple(getBlockState(blocks, "twilightforest", "cracked_deadrock")),
                BlockStateProvider.simple(getBlockState(blocks, "twilightforest", "deadrock"))
        ));

        BlockState underbrick = getBlockState(blocks, "twilightforest", "underbrick");
        BlockState underbrickMossy = getBlockState(blocks, "twilightforest", "mossy_underbrick");
        BlockState underbrickCracked = getBlockState(blocks, "twilightforest", "cracked_underbrick");
        long seed = (long) "twilight".hashCode() << 32 | "forest".hashCode();
        context.register(UNDERBRICK_PILLAR, new ShaftVariation(
                ShaftVariation.MAX_BIAS,
                32,
                0.5f,
                0.85f,
                new BricksSettings(BricksSettings.Size.JUMBLED, new SurfacePathSettings(false, 0f, true, true, 0.3f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(Blocks.COBBLESTONE),
                BlockStateProvider.simple(Blocks.COBBLESTONE),
                new NoiseProvider(seed, new NormalNoise.NoiseParameters(3, 3.0), 0.0625f, List.of(underbrickMossy, underbrick, underbrickCracked)),
                BlockStateProvider.simple(Blocks.CYAN_TERRACOTTA),
                BlockStateProvider.simple(Blocks.COBBLESTONE),
                BlockStateProvider.simple(Blocks.COBBLESTONE)
        ));
    }

    @Override
    public VariationList variationList() {
        return list(
                entry(DEADROCK_PILLAR, 1),
                entry(OverworldShaftEntries.MOSSY_MAZE, 16),
                entry(UNDERBRICK_PILLAR, 11)
        );
    }

}
