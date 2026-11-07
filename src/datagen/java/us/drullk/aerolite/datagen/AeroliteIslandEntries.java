package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.VoronoiIslands;

import java.util.List;
import java.util.Map;

/**
 * The worldgen entries of the Islands preset: the lattice noise, the island density function of each dimension,
 * the island noise settings and the preset. Each dimension keeps its vanilla biome source and its vanilla climate functions.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
@Component
public final class AeroliteIslandEntries {

    /** The island cell size along X and Z, in blocks. */
    public static final int CELL_SIZE = 256;
    /** The island cell height, in blocks. It is the height of the island height band. */
    public static final int CELL_HEIGHT = 128;
    /** The bottom of the island height band. */
    public static final int LAYER_MIN_Y = 0;
    public static final double JITTER = 0.8;
    public static final double FILL_MIN = 0.3;
    public static final double FILL_MAX = 0.9;
    public static final double EMPTY_CHANCE = 0.1;

    private static final ResourceKey<DensityFunction> SHIFT_X = vanillaFunction("shift_x");
    private static final ResourceKey<DensityFunction> SHIFT_Z = vanillaFunction("shift_z");

    public AeroliteIslandEntries() {
    }

    public void noiseParameters(BootstrapContext<NormalNoise.NoiseParameters> context) {
        context.register(AeroliteWorldgen.ISLANDS_NOISE, new NormalNoise.NoiseParameters(-7, 1.0));
    }

    public void densityFunctions(BootstrapContext<DensityFunction> context) {
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        DensityFunction.NoiseHolder noise = new DensityFunction.NoiseHolder(noises.getOrThrow(AeroliteWorldgen.ISLANDS_NOISE));
        for (ResourceKey<DensityFunction> key : List.of(AeroliteWorldgen.OVERWORLD_ISLANDS, AeroliteWorldgen.NETHER_ISLANDS, AeroliteWorldgen.END_ISLANDS)) {
            context.register(key, new VoronoiIslands(noise, CELL_SIZE, CELL_HEIGHT, LAYER_MIN_Y, JITTER, FILL_MIN, FILL_MAX, EMPTY_CHANCE));
        }
    }

    public void noiseSettings(BootstrapContext<NoiseGeneratorSettings> context) {
        HolderGetter<DensityFunction> functions = context.lookup(Registries.DENSITY_FUNCTION);
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        context.register(AeroliteWorldgen.ISLANDS_OVERWORLD_NOISE,
                settings(-64, 384, Blocks.STONE.defaultBlockState(), -64, overworldRouter(functions, noises), SurfaceRuleData.overworld()));
        context.register(AeroliteWorldgen.ISLANDS_NETHER_NOISE,
                settings(0, 256, Blocks.NETHERRACK.defaultBlockState(), 0, netherRouter(functions, noises), SurfaceRuleData.nether()));
        context.register(AeroliteWorldgen.ISLANDS_END_NOISE,
                settings(0, 256, Blocks.END_STONE.defaultBlockState(), 0, endRouter(functions), SurfaceRuleData.end()));
    }

    /**
     * Noise settings whose final density is the interpolated island density function. The default fluid is air.
     * The noise step samples the final density once per 4x4x4 cell and interpolates. The initial density stays exact.
     * A sea level at or below Y -54 keeps the noise step from filling the bottom with lava.
     */
    private static NoiseGeneratorSettings settings(int minY, int height, BlockState defaultBlock, int seaLevel, NoiseRouter router, SurfaceRules.RuleSource surfaceRule) {
        return new NoiseGeneratorSettings(
                NoiseSettings.create(minY, height, 1, 1),
                defaultBlock,
                Blocks.AIR.defaultBlockState(),
                router,
                surfaceRule,
                List.of(),
                seaLevel,
                false,
                false,
                false,
                false
        );
    }

    /** The vanilla Overworld climate over the island density. */
    private static NoiseRouter overworldRouter(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        // [VANILLACOPY] NoiseRouterData#overworld, the climate functions only; the island density replaces the terrain
        DensityFunction shiftX = function(functions, SHIFT_X);
        DensityFunction shiftZ = function(functions, SHIFT_Z);
        DensityFunction temperature = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.TEMPERATURE));
        DensityFunction vegetation = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.VEGETATION));
        DensityFunction islands = function(functions, AeroliteWorldgen.OVERWORLD_ISLANDS);
        DensityFunction terrain = DensityFunctions.interpolated(islands);
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(
                zero,
                zero,
                zero,
                zero,
                temperature,
                vegetation,
                function(functions, NoiseRouterData.CONTINENTS),
                function(functions, NoiseRouterData.EROSION),
                function(functions, NoiseRouterData.DEPTH),
                function(functions, NoiseRouterData.RIDGES),
                islands,
                terrain,
                zero,
                zero,
                zero
        );
    }

    /** The vanilla Nether climate over the island density. */
    private static NoiseRouter netherRouter(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        // [VANILLACOPY] NoiseRouterData#noNewCaves, the climate functions only; the island density replaces the terrain
        DensityFunction shiftX = function(functions, SHIFT_X);
        DensityFunction shiftZ = function(functions, SHIFT_Z);
        DensityFunction temperature = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.TEMPERATURE));
        DensityFunction vegetation = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.VEGETATION));
        DensityFunction islands = function(functions, AeroliteWorldgen.NETHER_ISLANDS);
        DensityFunction terrain = DensityFunctions.interpolated(islands);
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(zero, zero, zero, zero, temperature, vegetation, zero, zero, zero, zero, islands, terrain, zero, zero, zero);
    }

    /** The vanilla End erosion over the island density. The End biome source reads the erosion. */
    private static NoiseRouter endRouter(HolderGetter<DensityFunction> functions) {
        // [VANILLACOPY] NoiseRouterData#end, the erosion only; the island density replaces the terrain
        DensityFunction erosion = DensityFunctions.cache2d(DensityFunctions.endIslands(0L));
        DensityFunction islands = function(functions, AeroliteWorldgen.END_ISLANDS);
        DensityFunction terrain = DensityFunctions.interpolated(islands);
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, erosion, zero, zero, islands, terrain, zero, zero, zero);
    }

    public void worldPreset(BootstrapContext<WorldPreset> context) {
        HolderGetter<DimensionType> dimensionTypes = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
        HolderGetter<MultiNoiseBiomeSourceParameterList> parameterLists = context.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        LevelStem overworld = new LevelStem(
                dimensionTypes.getOrThrow(AeroliteWorldgen.OVERWORLD_TYPE),
                new NoiseBasedChunkGenerator(
                        MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.ISLANDS_OVERWORLD_NOISE)
                )
        );
        LevelStem nether = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.NETHER),
                new NoiseBasedChunkGenerator(
                        MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.ISLANDS_NETHER_NOISE)
                )
        );
        LevelStem end = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.END),
                new NoiseBasedChunkGenerator(
                        TheEndBiomeSource.create(context.lookup(Registries.BIOME)),
                        noiseSettings.getOrThrow(AeroliteWorldgen.ISLANDS_END_NOISE)
                )
        );
        context.register(AeroliteWorldgen.ISLANDS_PRESET, new WorldPreset(Map.of(
                LevelStem.OVERWORLD, overworld,
                LevelStem.NETHER, nether,
                LevelStem.END, end
        )));
    }

    private static DensityFunction function(HolderGetter<DensityFunction> functions, ResourceKey<DensityFunction> key) {
        return new DensityFunctions.HolderHolder(functions.getOrThrow(key));
    }

    private static ResourceKey<DensityFunction> vanillaFunction(String path) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace(path));
    }

}
