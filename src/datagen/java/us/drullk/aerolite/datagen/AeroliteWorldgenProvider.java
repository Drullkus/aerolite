package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.SurfaceRuleData;
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
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.AeroliteWorldgen;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the worldgen JSON of the preset: the void noise settings of each dimension and the preset.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public class AeroliteWorldgenProvider extends DatapackBuiltinEntriesProvider {

    private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.NOISE_SETTINGS, AeroliteWorldgenProvider::noiseSettings)
            .add(Registries.WORLD_PRESET, AeroliteWorldgenProvider::worldPresets);

    public AeroliteWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(Aerolite.MODID));
    }

    private static void noiseSettings(BootstrapContext<NoiseGeneratorSettings> context) {
        context.register(AeroliteWorldgen.OVERWORLD_NOISE, voidSettings(-64, 384, Blocks.STONE.defaultBlockState(), -64));
        context.register(AeroliteWorldgen.NETHER_NOISE, voidSettings(0, 256, Blocks.NETHERRACK.defaultBlockState(), 32));
        context.register(AeroliteWorldgen.END_NOISE, voidSettings(0, 256, Blocks.END_STONE.defaultBlockState(), 0));
    }

    /**
     * Noise settings whose final density is negative everywhere, so the noise step places only air.
     * A sea level at or below Y -54 keeps the noise step from filling the bottom with lava.
     */
    private static NoiseGeneratorSettings voidSettings(int minY, int height, BlockState defaultBlock, int seaLevel) {
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

    private static void worldPresets(BootstrapContext<WorldPreset> context) {
        HolderGetter<DimensionType> dimensionTypes = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
        HolderGetter<MultiNoiseBiomeSourceParameterList> parameterLists = context.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        LevelStem overworld = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.OVERWORLD),
                new AeroliteChunkGenerator(
                        MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.OVERWORLD_NOISE),
                        AeroliteSettings.DEFAULT
                )
        );
        LevelStem nether = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.NETHER),
                new AeroliteChunkGenerator(
                        MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.NETHER_NOISE),
                        AeroliteSettings.DEFAULT
                )
        );
        LevelStem end = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.END),
                new AeroliteChunkGenerator(
                        TheEndBiomeSource.create(context.lookup(Registries.BIOME)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.END_NOISE),
                        AeroliteSettings.DEFAULT
                )
        );
        context.register(AeroliteWorldgen.PRESET, new WorldPreset(Map.of(
                LevelStem.OVERWORLD, overworld,
                LevelStem.NETHER, nether,
                LevelStem.END, end
        )));
    }

}
