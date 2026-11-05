package us.drullk.aerolite.datagen.shaft;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.ModdedDimensions;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import static us.drullk.aerolite.datagen.shaft.DimensionShaftEntries.*;

/**
 * The shipped shaft data of the Aether.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
@Component
public final class AetherShaftEntries implements DimensionShaftEntries {

    public static final ResourceKey<ShaftVariation> HOLYSTONE_PILLAR = variation("holystone_pillar");
    public static final ResourceKey<ShaftVariation> BRONZE_PILLAR = variation("aether_bronze_pillar");
    public static final ResourceKey<ShaftVariation> SILVER_PILLAR = variation("aether_silver_pillar");
    public static final ResourceKey<ShaftVariation> GOLD_PILLAR = variation("aether_gold_pillar");

    private static final int CHUNK = 16;

    public AetherShaftEntries() {
    }

    @Override
    public ResourceKey<Level> dimension() {
        return ModdedDimensions.AETHER;
    }

    @Override
    public ShaftPlacement placement() {
        return new ShaftPlacement(8 * CHUNK, 16 * CHUNK, 0.7f);
    }

    @Override
    public Optional<String> requiredMod() {
        return Optional.of("aether");
    }

    @Override
    public List<ResourceKey<ShaftVariation>> modVariations() {
        return List.of(HOLYSTONE_PILLAR, BRONZE_PILLAR, SILVER_PILLAR, GOLD_PILLAR);
    }

    @Override
    public void variations(BootstrapContext<ShaftVariation> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        context.register(HOLYSTONE_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new MazeSettings(MazeSettings.Type.GILBERT, 0.95f, MazeSettings.Seam.RANDOM, 1),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "aether", "holystone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "holystone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "holystone_bricks")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "mossy_holystone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "holystone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "holystone"))
        ));
        context.register(BRONZE_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, false, new SurfacePathSettings(false, 0f, true, false, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "aether", "carved_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "carved_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "carved_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "sentry_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "sentry_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "carved_stone"))
        ));
        context.register(SILVER_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, false, new SurfacePathSettings(false, 0f, true, false, 0.75f)),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "aether", "angelic_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "angelic_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "angelic_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "light_angelic_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "light_angelic_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "angelic_stone"))
        ));
        context.register(GOLD_PILLAR, new ShaftVariation(
                ShaftVariation.UNIFORM_BIAS,
                128,
                0.3f,
                0.4f,
                new MazeSettings(MazeSettings.Type.LINES, 1f, MazeSettings.Seam.RANDOM, 2),
                PillarShape.PILLAR,
                BlockStateProvider.simple(getBlockState(blocks, "aether", "hellfire_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "hellfire_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "hellfire_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "light_hellfire_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "light_hellfire_stone")),
                BlockStateProvider.simple(getBlockState(blocks, "aether", "hellfire_stone"))
        ));
    }

    @Override
    public VariationList variationList() {
        return list(
                entry(HOLYSTONE_PILLAR, 16),
                entry(BRONZE_PILLAR, 7),
                entry(SILVER_PILLAR, 3),
                entry(GOLD_PILLAR, 1)
        );
    }

}
