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
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.List;
import java.util.Optional;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

@Component
public final class UndergardenShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> DEPTHROCK_PILLAR = variation("depthrock_pillar");
    public static final ResourceKey<ShaftVariation> SHIVERSTONE_PILLAR = variation("shiverstone_pillar");

    private static final int CHUNK = 16;

    public UndergardenShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return ModdedDimensions.UNDERGARDEN;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(8 * CHUNK, 16 * CHUNK, 0.1f);
    }

    @Override
    public Optional<String> requiredMod() {
        return Optional.of("undergarden");
    }

    @Override
    public List<ResourceKey<ShaftVariation>> modVariations() {
        return List.of(DEPTHROCK_PILLAR, SHIVERSTONE_PILLAR);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        context.register(DEPTHROCK_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                32,
                0.2f,
                0.35f,
                new BricksSettings(BricksSettings.Size.JUMBLED, new SurfacePathSettings(false, 0f, true, false, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "depthrock")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "depthrock")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "polished_depthrock")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "depthrock_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "depthrock_tiles")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "depthrock"))
        ));
        context.register(SHIVERSTONE_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                64,
                0.5f,
                0.65f,
                new BricksSettings(BricksSettings.Size.SMALL, new SurfacePathSettings(false, 0f, true, false, 0f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "shiverstone")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "shiverstone")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "shiverstone_bricks")),
                BlockStateProvider.simple(Blocks.PACKED_ICE),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "cracked_shiverstone_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "undergarden", "shiverstone"))
        ));
    }

    @Override
    public VariationList variationList() {
        return list(
                entry(DEPTHROCK_PILLAR, 4),
                entry(SHIVERSTONE_PILLAR, 1)
        );
    }

}
