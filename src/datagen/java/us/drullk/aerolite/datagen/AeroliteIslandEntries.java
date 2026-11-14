package us.drullk.aerolite.datagen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
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
import us.drullk.aerolite.world.island.IslandBiomeSource;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.LatticeSettings;
import us.drullk.aerolite.world.island.node.IslandCarrier;
import us.drullk.aerolite.world.island.node.IslandFunction;
import us.drullk.aerolite.world.island.node.IslandValue;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The worldgen entries of the Islands preset: the lattice noise, the island density functions, the noise settings,
 * the island variations, the island variation lists and the preset.
 * The entries of 0.5.0 stay under their ids: the density functions and the noise settings {@code aerolite:islands/<dimension>}.
 * So a world of 0.5.0 keeps its terrain. {@link IslandSettings#DEFAULT} holds their values.
 * The entries of the island variations are new: the island function entry and the noise settings {@code aerolite:island_variations/<dimension>},
 * the graph of the Basic variation, and the list, the Basic variation and the Empty variation of each dimension.
 * Each new router is the router of 0.5.0 with the island function entry as its terrain and the carrier on its temperature output.
 * The Basic variation holds the island body node alone with the values of 0.5.0. Each list holds Empty at weight 1 and Basic at weight 9.
 * The Basic variation and the Empty variation of a dimension hold the vanilla biome source of that dimension.
 * The preset gives each dimension the island biome source and the new noise settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
@Component
public final class AeroliteIslandEntries {

    /** The height of the surface floor above the bottom of a dimension, in blocks. */
    public static final int FLOOR_HEIGHT = 4;

    private static final ResourceKey<DensityFunction> SHIFT_X = vanillaFunction("shift_x");
    private static final ResourceKey<DensityFunction> SHIFT_Z = vanillaFunction("shift_z");
    /** The pick weight of the Empty variation of each shipped list. Its probability is the empty chance of 0.5.0. */
    private static final int EMPTY_WEIGHT = 1;
    /** The pick weight of the Basic variation of each shipped list. */
    private static final int BASIC_WEIGHT = 9;
    /** The dimensions of the preset, in the sequence of the shipped entries. */
    private static final List<ResourceKey<Level>> DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

    public AeroliteIslandEntries() {
    }

    public void noiseParameters(BootstrapContext<NormalNoise.NoiseParameters> context) {
        context.register(AeroliteWorldgen.ISLANDS_NOISE, new NormalNoise.NoiseParameters(-7, 1.0));
    }

    public void densityFunctions(BootstrapContext<DensityFunction> context) {
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        DensityFunction.NoiseHolder noise = new DensityFunction.NoiseHolder(noises.getOrThrow(AeroliteWorldgen.ISLANDS_NOISE));
        for (ResourceKey<DensityFunction> key : List.of(AeroliteWorldgen.OVERWORLD_ISLANDS, AeroliteWorldgen.NETHER_ISLANDS, AeroliteWorldgen.END_ISLANDS)) {
            context.register(key, IslandSettings.DEFAULT.function(noise));
        }
        LatticeSettings lattice = LatticeSettings.DEFAULT;
        for (ResourceKey<Level> dimension : DIMENSIONS) {
            context.register(AeroliteWorldgen.islandVariationsOf(dimension), new IslandFunction(noise, lattice.cellSize(), lattice.cellHeight(), lattice.layerMinY(),
                    lattice.jitter(), IslandVariationRegistries.listOf(dimension), VariationLookup.NONE));
        }
        context.register(AeroliteWorldgen.BASIC_ISLAND, IslandValue.Kind.ISLAND_BODY.function());
    }

    /** The Basic variation and the Empty variation of each dimension. */
    public void variations(BootstrapContext<IslandVariation> context) {
        HolderGetter<DensityFunction> functions = context.lookup(Registries.DENSITY_FUNCTION);
        HolderGetter<MultiNoiseBiomeSourceParameterList> parameterLists = context.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        Map<ResourceKey<Level>, Supplier<BiomeSource>> biomeSources = Map.of(
                Level.OVERWORLD, () -> MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                Level.NETHER, () -> MultiNoiseBiomeSource.createFromPreset(parameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)),
                Level.END, () -> TheEndBiomeSource.create(context.lookup(Registries.BIOME)));
        for (ResourceKey<Level> dimension : DIMENSIONS) {
            context.register(IslandVariationRegistries.basicOf(dimension), IslandVariation.island(functions.getOrThrow(AeroliteWorldgen.BASIC_ISLAND),
                    biomeSources.get(dimension).get(), IslandVariation.DEFAULT_CELL_WEIGHT, IslandVariation.DEFAULT_FILL, IslandVariation.DEFAULT_DISTORTION,
                    IslandVariation.DEFAULT_DISTORTION_SCALE, IslandVariation.DEFAULT_DISTORTION_OCTAVES));
            context.register(IslandVariationRegistries.emptyOf(dimension), IslandVariation.empty(biomeSources.get(dimension).get(), IslandVariation.DEFAULT_CELL_WEIGHT));
        }
    }

    /** The island variation list of each dimension: Empty, then Basic. */
    public void lists(BootstrapContext<IslandVariationList> context) {
        for (ResourceKey<Level> dimension : DIMENSIONS) {
            context.register(IslandVariationRegistries.listOf(dimension), new IslandVariationList(List.of(
                    IslandVariationList.Entry.variation(IslandVariationRegistries.emptyOf(dimension), EMPTY_WEIGHT),
                    IslandVariationList.Entry.variation(IslandVariationRegistries.basicOf(dimension), BASIC_WEIGHT))));
        }
    }

    public void noiseSettings(BootstrapContext<NoiseGeneratorSettings> context) {
        HolderGetter<DensityFunction> functions = context.lookup(Registries.DENSITY_FUNCTION);
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        context.register(AeroliteWorldgen.ISLANDS_OVERWORLD_NOISE, settings(-64, 384, Blocks.STONE.defaultBlockState(), -64,
                overworldRouter(functions, noises, -64, AeroliteWorldgen.OVERWORLD_ISLANDS, false), SurfaceRuleData.overworld()));
        context.register(AeroliteWorldgen.ISLANDS_NETHER_NOISE, settings(0, 256, Blocks.NETHERRACK.defaultBlockState(), 0,
                netherRouter(functions, noises, 0, AeroliteWorldgen.NETHER_ISLANDS, false), SurfaceRuleData.nether()));
        context.register(AeroliteWorldgen.ISLANDS_END_NOISE, settings(0, 256, Blocks.END_STONE.defaultBlockState(), 0,
                endRouter(functions, 0, AeroliteWorldgen.END_ISLANDS, false), SurfaceRuleData.end()));
        context.register(AeroliteWorldgen.ISLAND_VARIATIONS_OVERWORLD_NOISE, settings(-64, 384, Blocks.STONE.defaultBlockState(), -64,
                overworldRouter(functions, noises, -64, AeroliteWorldgen.OVERWORLD_ISLAND_VARIATIONS, true), SurfaceRuleData.overworld()));
        context.register(AeroliteWorldgen.ISLAND_VARIATIONS_NETHER_NOISE, settings(0, 256, Blocks.NETHERRACK.defaultBlockState(), 0,
                netherRouter(functions, noises, 0, AeroliteWorldgen.NETHER_ISLAND_VARIATIONS, true), SurfaceRuleData.nether()));
        context.register(AeroliteWorldgen.ISLAND_VARIATIONS_END_NOISE, settings(0, 256, Blocks.END_STONE.defaultBlockState(), 0,
                endRouter(functions, 0, AeroliteWorldgen.END_ISLAND_VARIATIONS, true), SurfaceRuleData.end()));
    }

    /** The temperature function, with the carrier of the island function entry around it when {@code carrier} is set. */
    private static DensityFunction temperature(HolderGetter<DensityFunction> functions, DensityFunction temperature, ResourceKey<DensityFunction> islands, boolean carrier) {
        return carrier ? new IslandCarrier(function(functions, islands), temperature) : temperature;
    }

    /**
     * Noise settings whose final density is the interpolated island density function. The default fluid is air.
     * The noise step samples the final density once per 4x4x4 cell and interpolates. The initial density stays exact.
     * The initial density holds the surface floor. The vanilla surface rules read it.
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

    /**
     * The initial density: the island density over the surface floor. The floor is a Y gradient from 1 at the bottom of the dimension
     * to -1 {@value #FLOOR_HEIGHT} blocks up. So a column outside every island has a preliminary surface level at the bottom.
     * Without it, a surface grid corner outside every island has no preliminary surface level, and the vanilla rules give the island edge no surface.
     */
    private static DensityFunction initialDensity(DensityFunction islands, int minY) {
        return DensityFunctions.max(islands, DensityFunctions.yClampedGradient(minY, minY + FLOOR_HEIGHT, 1.0, -1.0));
    }

    /** The vanilla Overworld climate over the island density. */
    private static NoiseRouter overworldRouter(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, int minY,
                                               ResourceKey<DensityFunction> islandsKey, boolean carrier) {
        // [VANILLACOPY] NoiseRouterData#overworld, the climate functions only; the island density replaces the terrain
        DensityFunction shiftX = function(functions, SHIFT_X);
        DensityFunction shiftZ = function(functions, SHIFT_Z);
        DensityFunction temperature = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.TEMPERATURE));
        DensityFunction vegetation = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.VEGETATION));
        DensityFunction islands = function(functions, islandsKey);
        DensityFunction terrain = DensityFunctions.interpolated(islands);
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(
                zero,
                zero,
                zero,
                zero,
                temperature(functions, temperature, islandsKey, carrier),
                vegetation,
                function(functions, NoiseRouterData.CONTINENTS),
                function(functions, NoiseRouterData.EROSION),
                function(functions, NoiseRouterData.DEPTH),
                function(functions, NoiseRouterData.RIDGES),
                initialDensity(islands, minY),
                terrain,
                zero,
                zero,
                zero
        );
    }

    /** The vanilla Nether climate over the island density. */
    private static NoiseRouter netherRouter(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, int minY,
                                            ResourceKey<DensityFunction> islandsKey, boolean carrier) {
        // [VANILLACOPY] NoiseRouterData#noNewCaves, the climate functions only; the island density replaces the terrain
        DensityFunction shiftX = function(functions, SHIFT_X);
        DensityFunction shiftZ = function(functions, SHIFT_Z);
        DensityFunction temperature = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.TEMPERATURE));
        DensityFunction vegetation = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.VEGETATION));
        DensityFunction islands = function(functions, islandsKey);
        DensityFunction terrain = DensityFunctions.interpolated(islands);
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(zero, zero, zero, zero, temperature(functions, temperature, islandsKey, carrier), vegetation, zero, zero, zero, zero, initialDensity(islands, minY), terrain, zero, zero, zero);
    }

    /** The vanilla End erosion over the island density. The End biome source reads the erosion. */
    private static NoiseRouter endRouter(HolderGetter<DensityFunction> functions, int minY, ResourceKey<DensityFunction> islandsKey, boolean carrier) {
        // [VANILLACOPY] NoiseRouterData#end, the erosion only; the island density replaces the terrain
        DensityFunction erosion = DensityFunctions.cache2d(DensityFunctions.endIslands(0L));
        DensityFunction islands = function(functions, islandsKey);
        DensityFunction terrain = DensityFunctions.interpolated(islands);
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(zero, zero, zero, zero, temperature(functions, zero, islandsKey, carrier), zero, zero, erosion, zero, zero, initialDensity(islands, minY), terrain, zero, zero, zero);
    }

    /** The preset: each dimension gets the island biome source of its list and the noise settings of the island variations. */
    public void worldPreset(BootstrapContext<WorldPreset> context) {
        HolderGetter<DimensionType> dimensionTypes = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
        LevelStem overworld = new LevelStem(
                dimensionTypes.getOrThrow(AeroliteWorldgen.OVERWORLD_TYPE),
                new NoiseBasedChunkGenerator(islandBiomes(Level.OVERWORLD), noiseSettings.getOrThrow(AeroliteWorldgen.ISLAND_VARIATIONS_OVERWORLD_NOISE))
        );
        LevelStem nether = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.NETHER),
                new NoiseBasedChunkGenerator(islandBiomes(Level.NETHER), noiseSettings.getOrThrow(AeroliteWorldgen.ISLAND_VARIATIONS_NETHER_NOISE))
        );
        LevelStem end = new LevelStem(
                dimensionTypes.getOrThrow(BuiltinDimensionTypes.END),
                new NoiseBasedChunkGenerator(islandBiomes(Level.END), noiseSettings.getOrThrow(AeroliteWorldgen.ISLAND_VARIATIONS_END_NOISE))
        );
        context.register(AeroliteWorldgen.ISLANDS_PRESET, new WorldPreset(Map.of(
                LevelStem.OVERWORLD, overworld,
                LevelStem.NETHER, nether,
                LevelStem.END, end
        )));
    }

    /** The island biome source of the list of the dimension. The codec writes only the list id. */
    private static IslandBiomeSource islandBiomes(ResourceKey<Level> dimension) {
        return new IslandBiomeSource(IslandVariationRegistries.listOf(dimension), VariationLookup.NONE, Optional.empty());
    }

    private static DensityFunction function(HolderGetter<DensityFunction> functions, ResourceKey<DensityFunction> key) {
        return new DensityFunctions.HolderHolder(functions.getOrThrow(key));
    }

    private static ResourceKey<DensityFunction> vanillaFunction(String path) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace(path));
    }

}
