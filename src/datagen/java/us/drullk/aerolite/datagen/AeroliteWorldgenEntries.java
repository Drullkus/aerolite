package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.ShaftRegistries;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The worldgen entries of the preset: the Overworld dimension type, the void noise settings of each dimension and the preset.
 * The generators of the preset carry no settings, so they take the default settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class AeroliteWorldgenEntries {

    private final AeroliteShaftEntries shaftEntries;

    public AeroliteWorldgenEntries(@Autowired AeroliteShaftEntries shaftEntries) {
        this.shaftEntries = shaftEntries;
    }

    public RegistrySetBuilder builder() {
        return new RegistrySetBuilder()
                .add(Registries.DIMENSION_TYPE, this::dimensionTypes)
                .add(Registries.NOISE_SETTINGS, this::noiseSettings)
                .add(Registries.WORLD_PRESET, this::worldPresets)
                .add(ShaftRegistries.SHAFT_VARIATION, this.shaftEntries::variations)
                .add(ShaftRegistries.SHAFT_PLACEMENT, this.shaftEntries::placements);
    }

    private void dimensionTypes(BootstrapContext<DimensionType> context) {
        // [VANILLACOPY] DimensionTypes#bootstrap overworld, effects id aerolite:overworld
        context.register(AeroliteWorldgen.OVERWORLD_TYPE, new DimensionType(
                OptionalLong.empty(),
                true,
                false,
                false,
                true,
                1.0,
                true,
                false,
                -64,
                384,
                384,
                BlockTags.INFINIBURN_OVERWORLD,
                AeroliteWorldgen.OVERWORLD_EFFECTS,
                0.0F,
                new DimensionType.MonsterSettings(false, true, UniformInt.of(0, 7), 0)
        ));
    }

    private void noiseSettings(BootstrapContext<NoiseGeneratorSettings> context) {
        context.register(AeroliteWorldgen.OVERWORLD_NOISE, this.voidSettings(-64, 384, Blocks.STONE.defaultBlockState(), -64));
        context.register(AeroliteWorldgen.NETHER_NOISE, this.voidSettings(0, 256, Blocks.NETHERRACK.defaultBlockState(), 32));
        context.register(AeroliteWorldgen.END_NOISE, this.voidSettings(0, 256, Blocks.END_STONE.defaultBlockState(), 0));
    }

    /**
     * Noise settings whose final density is negative everywhere, so the noise step places only air.
     * A sea level at or below Y -54 keeps the noise step from filling the bottom with lava.
     */
    private NoiseGeneratorSettings voidSettings(int minY, int height, BlockState defaultBlock, int seaLevel) {
        DensityFunction zero = DensityFunctions.zero();
        NoiseRouter router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, DensityFunctions.constant(-1), zero, zero, zero);
        return new NoiseGeneratorSettings(
                NoiseSettings.create(minY, height, 1, 2),
                defaultBlock,
                Blocks.AIR.defaultBlockState(),
                router,
                SurfaceRuleData.air(),
                List.of(),
                seaLevel,
                false,
                false,
                false,
                false
        );
    }

    private void worldPresets(BootstrapContext<WorldPreset> context) {
        HolderGetter<DimensionType> dimensionTypes = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
        HolderGetter<MultiNoiseBiomeSourceParameterList> parameterLists = context.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        LevelStem overworld = new LevelStem(
                dimensionTypes.getOrThrow(AeroliteWorldgen.OVERWORLD_TYPE),
                new AeroliteChunkGenerator(
                        MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.OVERWORLD_NOISE),
                        Optional.empty()
                )
        );
        LevelStem nether = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.NETHER),
                new AeroliteChunkGenerator(
                        MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.NETHER_NOISE),
                        Optional.empty()
                )
        );
        LevelStem end = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.END),
                new AeroliteChunkGenerator(
                        TheEndBiomeSource.create(context.lookup(Registries.BIOME)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.END_NOISE),
                        Optional.empty()
                )
        );
        context.register(AeroliteWorldgen.PRESET, new WorldPreset(Map.of(
                LevelStem.OVERWORLD, overworld,
                LevelStem.NETHER, nether,
                LevelStem.END, end
        )));
    }

}
