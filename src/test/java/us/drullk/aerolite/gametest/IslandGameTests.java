package us.drullk.aerolite.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.util.Mth;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tamaized.beanification.Autowired;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.GraphSettings;
import us.drullk.aerolite.world.island.IslandBiomeSource;
import us.drullk.aerolite.world.island.IslandCells;
import us.drullk.aerolite.world.island.IslandGenerators;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.IslandTerrain;
import us.drullk.aerolite.world.island.LatticeSettings;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.VoronoiLattice;
import us.drullk.aerolite.world.island.node.CellRandom;
import us.drullk.aerolite.world.island.node.IslandCarrier;
import us.drullk.aerolite.world.island.node.IslandFunction;
import us.drullk.aerolite.world.island.node.IslandValue;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.PreviewOverride;
import us.drullk.aerolite.world.island.variation.UnitRange;
import us.drullk.aerolite.world.island.variation.VariationLookup;
import us.drullk.aerolite.world.island.graph.FunctionGraph;
import us.drullk.aerolite.world.island.graph.FunctionNode;
import us.drullk.aerolite.world.island.graph.GraphJson;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Checks the Islands preset: its dimensions, its island biome source, the wired island function, the surface floor,
 * the blocks of a generated chunk, an Empty cell and the terrain fill of the preview.
 * It checks the biome of a block in a cell of the row 0 and above the row 0. It also checks the island settings edit of a world of 0.5.0,
 * the chunk fill of the noise settings of 0.5.0 and the terrain build from the graphs of the island editor.
 * It checks the island function in the climate sampler of a noise chunk, the chunk wrappers in a variation graph and a parallel fill.
 * It checks the dropped caches of a variation graph. It checks a variation edit of the flow in the built router.
 * It checks that a list problem and a missing carrier fail the build.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class IslandGameTests {

    private static final long SEED = 1234L;
    private static final Map<ResourceKey<LevelStem>, ResourceKey<NoiseGeneratorSettings>> NOISE_SETTINGS = Map.of(
            LevelStem.OVERWORLD, AeroliteWorldgen.ISLAND_VARIATIONS_OVERWORLD_NOISE,
            LevelStem.NETHER, AeroliteWorldgen.ISLAND_VARIATIONS_NETHER_NOISE,
            LevelStem.END, AeroliteWorldgen.ISLAND_VARIATIONS_END_NOISE
    );
    /** The noise settings of 0.5.0. A world of 0.5.0 names them. */
    private static final Map<ResourceKey<LevelStem>, ResourceKey<NoiseGeneratorSettings>> OLD_NOISE_SETTINGS = Map.of(
            LevelStem.OVERWORLD, AeroliteWorldgen.ISLANDS_OVERWORLD_NOISE,
            LevelStem.NETHER, AeroliteWorldgen.ISLANDS_NETHER_NOISE,
            LevelStem.END, AeroliteWorldgen.ISLANDS_END_NOISE
    );
    private static final Map<ResourceKey<LevelStem>, ResourceKey<Level>> LEVELS = Map.of(
            LevelStem.OVERWORLD, Level.OVERWORLD,
            LevelStem.NETHER, Level.NETHER,
            LevelStem.END, Level.END
    );
    /** The span of the brute force search for the nearest cell of the row 0, in boxes on each side. */
    private static final int ROW_SEARCH = 8;

    // GameTest methods are static. Beanification fills this field.
    @Autowired
    private static IslandGenerators generators;
    @Autowired
    private static GraphSettings graphSettings;

    private IslandGameTests() {
    }

    private static WorldDimensions presetDimensions(ServerLevel level) {
        Holder<WorldPreset> preset = level.registryAccess().registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(AeroliteWorldgen.ISLANDS_PRESET);
        return preset.value().createWorldDimensions();
    }

    private static NoiseBasedChunkGenerator generator(ServerLevel level, ResourceKey<LevelStem> stem) {
        return (NoiseBasedChunkGenerator) presetDimensions(level).dimensions().get(stem).generator();
    }

    private static RandomState randomState(ServerLevel level, NoiseBasedChunkGenerator generator) {
        return RandomState.create(generator.generatorSettings().value(), level.registryAccess().lookupOrThrow(Registries.NOISE), SEED);
    }

    /** The function inside the holders and markers. */
    private static DensityFunction unwrap(DensityFunction function) {
        while (true) {
            if (function instanceof DensityFunctions.HolderHolder holder) {
                function = holder.function().value();
            } else if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                function = marker.wrapped();
            } else {
                return function;
            }
        }
    }

    /** The island function behind the final density of the wired router, inside its holders and markers. */
    private static IslandFunction islands(GameTestHelper helper, RandomState randomState) {
        DensityFunction function = unwrap(randomState.router().finalDensity());
        helper.assertTrue(function instanceof IslandFunction, "The final density is not the island function");
        return (IslandFunction) function;
    }

    /** The island density function of 0.5.0 behind the final density of the wired router. */
    private static VoronoiIslands oldIslands(GameTestHelper helper, RandomState randomState) {
        DensityFunction function = unwrap(randomState.router().finalDensity());
        helper.assertTrue(function instanceof VoronoiIslands, "The final density is not the island density function of 0.5.0");
        return (VoronoiIslands) function;
    }

    /** The dimensions of a world of 0.5.0: the vanilla biome sources and the noise settings of 0.5.0. */
    private static WorldDimensions oldDimensions(ServerLevel level) {
        WorldDimensions preset = presetDimensions(level);
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>(preset.dimensions());
        OLD_NOISE_SETTINGS.forEach((stem, settings) -> stems.put(stem, new LevelStem(preset.dimensions().get(stem).type(),
                new NoiseBasedChunkGenerator(vanillaBiomes(level, stem), level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(settings)))));
        return new WorldDimensions(stems);
    }

    /** The vanilla biome source of a preset dimension. */
    private static BiomeSource vanillaBiomes(ServerLevel level, ResourceKey<LevelStem> stem) {
        if (stem.equals(LevelStem.END)) {
            return TheEndBiomeSource.create(level.registryAccess().lookupOrThrow(Registries.BIOME));
        }
        ResourceKey<MultiNoiseBiomeSourceParameterList> preset = stem.equals(LevelStem.NETHER)
                ? MultiNoiseBiomeSourceParameterLists.NETHER
                : MultiNoiseBiomeSourceParameterLists.OVERWORLD;
        return MultiNoiseBiomeSource.createFromPreset(level.registryAccess().registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getHolderOrThrow(preset));
    }

    @GameTest(template = "empty")
    public static void islandsPresetIsNormalAndUsesIslandSettings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<WorldPreset> preset = level.registryAccess().registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(AeroliteWorldgen.ISLANDS_PRESET);
        helper.assertTrue(preset.is(WorldPresetTags.NORMAL), "The Islands preset is not in the normal world type list");
        WorldDimensions dimensions = presetDimensions(level);
        for (ResourceKey<LevelStem> stem : List.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END)) {
            LevelStem levelStem = dimensions.dimensions().get(stem);
            helper.assertTrue(levelStem.generator().getClass() == NoiseBasedChunkGenerator.class, "A dimension of the Islands preset has no vanilla noise generator");
            NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) levelStem.generator();
            helper.assertTrue(generator.generatorSettings().is(NOISE_SETTINGS.get(stem)), "A dimension of the Islands preset has other noise settings");
            helper.assertTrue(generator.generatorSettings().value().seaLevel() <= -54 || generator.generatorSettings().value().noiseSettings().minY() >= -54,
                    "The sea level of a dimension lets the noise step place lava");
            helper.assertTrue(generator.generatorSettings().value().defaultFluid().isAir(), "The default fluid of a dimension is not air");
            helper.assertTrue(generator.getBiomeSource() instanceof IslandBiomeSource source
                    && source.variations().equals(IslandVariationRegistries.listOf(LEVELS.get(stem))), "A dimension of the Islands preset has no island biome source of its list");
            Set<Holder<Biome>> vanilla = vanillaBiomes(level, stem).possibleBiomes();
            helper.assertTrue(generator.getBiomeSource().possibleBiomes().equals(vanilla), "The possible biomes of " + stem.location() + " are not its vanilla biomes");
        }
        helper.assertTrue(dimensions.dimensions().get(LevelStem.OVERWORLD).type().is(AeroliteWorldgen.OVERWORLD_TYPE), "The Overworld has not the Aerolite dimension type");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void randomStateSeedsTheIslandLattice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = generator(level, LevelStem.OVERWORLD);
        IslandFunction wired = islands(helper, randomState(level, generator));
        helper.assertTrue(wired.lattice().seed() != 0L, "The wired island function has the unwired seed");
        IslandFunction again = islands(helper, randomState(level, generator));
        helper.assertTrue(again.lattice().seed() == wired.lattice().seed(), "Two random states of one seed gave two lattice seeds");
        RandomState other = RandomState.create(generator.generatorSettings().value(), level.registryAccess().lookupOrThrow(Registries.NOISE), SEED + 1);
        helper.assertTrue(islands(helper, other).lattice().seed() != wired.lattice().seed(), "Two world seeds gave one lattice seed");
        helper.succeed();
    }

    /** The site column of the first empty cell of the row 0 along X from the origin. */
    private static BlockPos emptyColumn(GameTestHelper helper, IslandCells islands) {
        for (int ix = 0; ix < 32; ix++) {
            VoronoiLattice.Cell candidate = islands.lattice().cell(ix, 0, 0);
            if (!islands.hasIsland(candidate)) {
                return BlockPos.containing(candidate.siteX(), 0, candidate.siteZ());
            }
        }
        helper.fail("No empty cell in the first 32 cells");
        throw new IllegalStateException("unreachable");
    }

    /** The surface threshold of the preliminary surface search of the noise chunk. */
    private static final double SURFACE_THRESHOLD = 0.390625;

    private static void assertSurfaceFloor(GameTestHelper helper, NoiseRouter router, IslandCells islands, int minY, String dimension) {
        BlockPos column = emptyColumn(helper, islands);
        DensityFunction initial = router.initialDensityWithoutJaggedness();
        helper.assertTrue(initial.compute(new DensityFunction.SinglePointContext(column.getX(), minY, column.getZ())) > SURFACE_THRESHOLD,
                "The bottom of " + dimension + " has no surface floor");
        helper.assertTrue(initial.compute(new DensityFunction.SinglePointContext(column.getX(), minY + 4, column.getZ())) <= SURFACE_THRESHOLD,
                "The surface floor of " + dimension + " is more than 4 blocks tall");
        helper.assertTrue(router.finalDensity().compute(new DensityFunction.SinglePointContext(column.getX(), minY, column.getZ())) < 0,
                "The surface floor of " + dimension + " places blocks");
    }

    @GameTest(template = "empty")
    public static void columnsOutsideTheIslandsHaveASurfaceFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (ResourceKey<LevelStem> stem : NOISE_SETTINGS.keySet()) {
            NoiseBasedChunkGenerator generator = generator(level, stem);
            RandomState randomState = randomState(level, generator);
            int minY = generator.generatorSettings().value().noiseSettings().minY();
            assertSurfaceFloor(helper, randomState.router(), islands(helper, randomState), minY, stem.location().toString());
        }
        helper.succeed();
    }

    /** The island settings edit of a world of 0.5.0. */
    @GameTest(template = "empty")
    public static void islandSettingsEditRebuildsOnlyTheTerrainFunctions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldDimensions preset = oldDimensions(level);
        IslandSettings shipped = generators.settings(preset).orElseThrow();
        helper.assertTrue(shipped.equals(IslandSettings.DEFAULT), "The shipped island settings differ from the default settings");
        WorldDimensions same = generators.withSettings(preset, shipped);
        IslandSettings edited = shipped.withDistortion(shipped.distortion() + 10).withCellSize(512);
        WorldDimensions changed = generators.withSettings(preset, edited);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        for (ResourceKey<LevelStem> stem : OLD_NOISE_SETTINGS.keySet()) {
            String dimension = stem.location().toString();
            NoiseBasedChunkGenerator before = (NoiseBasedChunkGenerator) preset.dimensions().get(stem).generator();
            helper.assertTrue(same.dimensions().get(stem) == preset.dimensions().get(stem), "The shipped distortion rebuilt " + dimension);
            LevelStem editedStem = changed.dimensions().get(stem);
            helper.assertTrue(editedStem.type() == preset.dimensions().get(stem).type(), "The distortion edit changed the dimension type of " + dimension);
            NoiseBasedChunkGenerator after = (NoiseBasedChunkGenerator) editedStem.generator();
            helper.assertTrue(after.getBiomeSource() == before.getBiomeSource(), "The distortion edit changed the biome source of " + dimension);
            helper.assertFalse(after.generatorSettings().is(OLD_NOISE_SETTINGS.get(stem)), "The distortion edit kept the registry settings of " + dimension);
            helper.assertTrue(IslandSettings.of(generators.islands(after).orElseThrow()).equals(edited), "The edit did not reach the island function of " + dimension);
            NoiseRouter beforeRouter = before.generatorSettings().value().noiseRouter();
            NoiseRouter afterRouter = after.generatorSettings().value().noiseRouter();
            helper.assertTrue(afterRouter.temperature() == beforeRouter.temperature() && afterRouter.erosion() == beforeRouter.erosion(),
                    "The distortion edit rebuilt a climate function of " + dimension);
            RandomState wired = randomState(level, after);
            helper.assertTrue(oldIslands(helper, wired).lattice().seed() == oldIslands(helper, randomState(level, before)).lattice().seed(),
                    "The distortion edit changed the lattice seed of " + dimension);
            helper.assertTrue(IslandSettings.of(oldIslands(helper, wired)).equals(edited), "The wired island function of " + dimension + " lost the edit");
            int minY = after.generatorSettings().value().noiseSettings().minY();
            assertSurfaceFloor(helper, wired.router(), oldIslands(helper, wired), minY, dimension);
            JsonElement json = LevelStem.CODEC.encodeStart(ops, editedStem).getOrThrow(message -> new IllegalStateException("The edited " + dimension + " does not encode: " + message));
            LevelStem parsed = LevelStem.CODEC.parse(ops, json).getOrThrow(message -> new IllegalStateException("The edited " + dimension + " does not parse: " + message));
            helper.assertTrue(IslandSettings.of(generators.islands(parsed.generator()).orElseThrow()).equals(edited), "The parsed " + dimension + " lost the edit");
        }
        helper.succeed();
    }

    private static IslandTerrain terrain(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState randomState) {
        return new IslandTerrain(generator.generatorSettings().value(), randomState, generator.getBiomeSource(),
                (x, y, z) -> generator.getBiomeSource().getNoiseBiome(x, y, z, randomState.sampler()),
                level.registryAccess().registryOrThrow(Registries.BIOME), SEED);
    }

    private static ProtoChunk protoChunk(ServerLevel level, ChunkPos pos) {
        return new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
    }

    /** The terrain fill of the preview writes the blocks of the chunk fill of the generator. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void previewTerrainFillMatchesTheGeneratorFill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = generator(level, LevelStem.OVERWORLD);
        RandomState randomState = randomState(level, generator);
        VoronoiLattice.Cell cell = firstIsland(helper, islands(helper, randomState));
        ChunkPos pos = new ChunkPos(BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ()));
        ProtoChunk preview = protoChunk(level, pos);
        terrain(level, generator, randomState).fill(preview);
        CompletableFuture<ChunkAccess> fill = fill(level, generator, randomState, pos);
        helper.succeedWhen(() -> {
            helper.assertTrue(fill.isDone(), "The chunk fill is not done");
            ChunkAccess chunk = fill.join();
            int differences = 0;
            int solids = 0;
            BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
            for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        block.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                        if (!chunk.getBlockState(block).isAir()) {
                            solids++;
                        }
                        if (chunk.getBlockState(block) != preview.getBlockState(block)) {
                            differences++;
                        }
                    }
                }
            }
            helper.assertTrue(solids > 0, "The generator fill placed no block");
            helper.assertTrue(differences == 0, differences + " blocks of the preview fill differ from the generator fill");
        });
    }

    /** The surface of the preview terrain follows the surface rules of the biome. */
    @GameTest(template = "empty")
    public static void previewTerrainSurfaceGivesThePlainsIslandAGrassTop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator shipped = generator(level, LevelStem.OVERWORLD);
        Holder<net.minecraft.world.level.biome.Biome> plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), shipped.generatorSettings());
        RandomState randomState = randomState(level, generator);
        VoronoiLattice.Cell cell = firstIsland(helper, islands(helper, randomState));
        BlockPos site = BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ());
        ProtoChunk chunk = protoChunk(level, new ChunkPos(site));
        terrain(level, generator, randomState).generate(chunk);
        BlockPos.MutableBlockPos block = site.mutable();
        for (int y = chunk.getMaxBuildHeight() - 1; y >= chunk.getMinBuildHeight(); y--) {
            block.setY(y);
            if (!chunk.getBlockState(block).isAir()) {
                helper.assertTrue(chunk.getBlockState(block).is(Blocks.GRASS_BLOCK), "The top block of the site column is " + chunk.getBlockState(block) + ", not grass");
                helper.assertTrue(chunk.getBlockState(block.below()).is(Blocks.DIRT), "The block under the top is not dirt");
                helper.succeed();
                return;
            }
        }
        helper.fail("The site column holds no block");
    }

    /**
     * The chunk fill runs on a worker thread and loads chunks of the test level for the structure starts.
     * The test checks the chunk on a later tick, so the server thread stays free for those loads.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void islandChunkHoldsStoneAndNoLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = generator(level, LevelStem.OVERWORLD);
        RandomState randomState = randomState(level, generator);
        IslandFunction islands = islands(helper, randomState);
        VoronoiLattice.Cell cell = firstIsland(helper, islands);
        BlockPos site = BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ());
        CompletableFuture<ChunkAccess> fill = fill(level, generator, randomState, new ChunkPos(site));
        helper.succeedWhen(() -> {
            helper.assertTrue(fill.isDone(), "The chunk fill is not done");
            ChunkAccess chunk = fill.join();
            helper.assertTrue(columnHolds(chunk, site, Blocks.STONE), "The site column of a filled cell holds no stone");
            helper.assertTrue(islands.compute(new DensityFunction.SinglePointContext(site.getX(), site.getY(), site.getZ())) > 0,
                    "The island function is not positive at the site");
            helper.assertTrue(chunk.getBlockState(new BlockPos(site.getX(), -60, site.getZ())).isAir(), "The row below the islands holds a block");
            helper.assertTrue(chunk.getBlockState(new BlockPos(site.getX(), 300, site.getZ())).isAir(), "The row above the islands holds a block");
            assertNoLava(helper, chunk);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void netherAndEndIslandChunksHoldTheirDefaultBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<ResourceKey<LevelStem>, Block> defaultBlocks = Map.of(LevelStem.NETHER, Blocks.NETHERRACK, LevelStem.END, Blocks.END_STONE);
        List<Runnable> checks = new ArrayList<>();
        for (Map.Entry<ResourceKey<LevelStem>, Block> entry : defaultBlocks.entrySet()) {
            NoiseBasedChunkGenerator generator = generator(level, entry.getKey());
            RandomState randomState = randomState(level, generator);
            VoronoiLattice.Cell cell = firstIsland(helper, islands(helper, randomState));
            BlockPos site = BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ());
            CompletableFuture<ChunkAccess> fill = fill(level, generator, randomState, new ChunkPos(site));
            checks.add(() -> {
                helper.assertTrue(fill.isDone(), "The chunk fill of " + entry.getKey().location() + " is not done");
                ChunkAccess chunk = fill.join();
                helper.assertTrue(columnHolds(chunk, site, entry.getValue()), "The site column of a filled cell holds no default block of " + entry.getKey().location());
                assertNoLava(helper, chunk);
            });
        }
        helper.succeedWhen(() -> checks.forEach(Runnable::run));
    }

    /** The first island of the row 0 along X from the origin. */
    private static VoronoiLattice.Cell firstIsland(GameTestHelper helper, IslandCells islands) {
        for (int ix = 0; ix < 8; ix++) {
            VoronoiLattice.Cell candidate = islands.lattice().cell(ix, 0, 0);
            if (islands.hasIsland(candidate)) {
                return candidate;
            }
        }
        helper.fail("No filled cell in the first 8 cells");
        throw new IllegalStateException("unreachable");
    }

    private static CompletableFuture<ChunkAccess> fill(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState randomState, ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        return generator.fillFromNoise(Blender.empty(), randomState, level.structureManager(), chunk);
    }

    /** True while a block of the column of the position is the block. */
    private static boolean columnHolds(ChunkAccess chunk, BlockPos column, Block block) {
        BlockPos.MutableBlockPos pos = column.mutable();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
            if (chunk.getBlockState(pos.setY(y)).is(block)) {
                return true;
            }
        }
        return false;
    }

    private static void assertNoLava(GameTestHelper helper, ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            helper.assertFalse(section.maybeHas(state -> state.is(Blocks.LAVA)), "A section of the chunk holds lava");
        }
    }

    /** The settings that the router graph of the Overworld builds. */
    private static NoiseGeneratorSettings built(ServerLevel level, IslandGraphs graphs, NoiseBasedChunkGenerator generator) {
        return graphSettings.build(graphs, Level.OVERWORLD, generator.generatorSettings().value(), level.registryAccess(), graphSettings.noises(level.registryAccess()))
                .getOrThrow(message -> new IllegalStateException("The router graph does not build: " + message)).settings();
    }

    /** The start router graph links every output to the shipped function nodes and fills a chunk like the shipped noise settings. */
    @GameTest(template = "empty")
    public static void startRouterGraphBuildsTheShippedTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IslandGraphs graphs = graphSettings.start(presetDimensions(level), level.registryAccess());
        helper.assertTrue(graphs.dimensions().size() == 3, "The preset gave " + graphs.dimensions().size() + " router graphs, not 3");
        RouterGraph router = graphs.router(Level.OVERWORLD);
        helper.assertTrue(router != null && RouterGraph.FIELDS.stream().allMatch(field -> router.linked(field) != null),
                "An output of the start router graph has no link");
        helper.assertTrue(GraphJson.writeRouter(router, graphs.shipped(Level.OVERWORLD)).result().filter(graphs.shipped(Level.OVERWORLD)::equals).isPresent(),
                "The start router graph does not save the shipped router");
        helper.assertTrue(graphs.lattice(Level.OVERWORLD, graphSettings.loader(level.registryAccess())).orElseThrow().equals(LatticeSettings.DEFAULT),
                "The sliders of the start graphs do not read the shipped lattice settings");
        NoiseBasedChunkGenerator shipped = generator(level, LevelStem.OVERWORLD);
        NoiseBasedChunkGenerator built = new NoiseBasedChunkGenerator(shipped.getBiomeSource(), Holder.direct(built(level, graphs, shipped)));
        RandomState shippedState = randomState(level, shipped);
        RandomState builtState = randomState(level, built);
        VoronoiLattice.Cell cell = firstIsland(helper, islands(helper, shippedState));
        ChunkPos pos = new ChunkPos(BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ()));
        ProtoChunk expected = protoChunk(level, pos);
        terrain(level, shipped, shippedState).fill(expected);
        ProtoChunk actual = protoChunk(level, pos);
        terrain(level, built, builtState).fill(actual);
        int differences = 0;
        int solids = 0;
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (int y = expected.getMinBuildHeight(); y < expected.getMaxBuildHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    block.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    if (!expected.getBlockState(block).isAir()) {
                        solids++;
                    }
                    if (expected.getBlockState(block) != actual.getBlockState(block)) {
                        differences++;
                    }
                }
            }
        }
        helper.assertTrue(solids > 0, "The shipped fill placed no block");
        helper.assertTrue(differences == 0, differences + " blocks of the graph fill differ from the shipped fill");
        helper.succeed();
    }

    /** A slider write reaches the built router. A second write changes the same function node. */
    @GameTest(template = "empty")
    public static void sliderWriteReachesTheBuiltRouter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IslandGraphs graphs = graphSettings.start(presetDimensions(level), level.registryAccess());
        IslandGraphs.EntryLoader loader = graphSettings.loader(level.registryAccess());
        NoiseBasedChunkGenerator shipped = generator(level, LevelStem.OVERWORLD);
        LatticeSettings first = LatticeSettings.DEFAULT.withCellSize(384).withJitter(0.5);
        LatticeSettings second = first.withCellHeight(192);
        RouterGraph router = graphs.router(Level.OVERWORLD);
        helper.assertTrue(router != null, "The Overworld has no router graph");
        int count = router.functionNodes().size();
        JsonObject shippedRouter = graphs.shipped(Level.OVERWORLD);
        for (LatticeSettings written : List.of(first, second)) {
            helper.assertTrue(graphs.setLattice(Level.OVERWORLD, written, loader), "The Overworld router graph reaches no island function node");
            helper.assertTrue(graphs.edited().size() == 1, "The write edited " + graphs.edited().size() + " entries, not 1");
            helper.assertTrue(router.functionNodes().size() == count, "The write changed the function nodes of the router graph");
            helper.assertTrue(GraphJson.writeRouter(router, shippedRouter).result().filter(shippedRouter::equals).isPresent(),
                    "The write changed the router graph");
            DensityFunction finalDensity = unwrap(built(level, graphs, shipped).noiseRouter().finalDensity());
            helper.assertTrue(finalDensity instanceof IslandFunction, "The built final density holds no island function");
            IslandFunction islands = (IslandFunction) finalDensity;
            LatticeSettings built = new LatticeSettings(islands.cellSize(), islands.cellHeight(), islands.layerMinY(), islands.jitter());
            helper.assertTrue(built.equals(written), "The built router holds " + built + ", not " + written);
        }
        helper.succeed();
    }

    /** A constant final density builds a router with that constant. A constant out of range does not build. */
    @GameTest(template = "empty")
    public static void constantFinalDensityBuildsThatConstant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IslandGraphs graphs = graphSettings.start(presetDimensions(level), level.registryAccess());
        RouterGraph router = graphs.router(Level.OVERWORLD);
        helper.assertTrue(router != null, "The Overworld has no router graph");
        double constant = -0.25;
        router.removeTree(router.linked("final_density"));
        router.set("final_density", new RouterGraph.Constant(constant));
        NoiseBasedChunkGenerator shipped = generator(level, LevelStem.OVERWORLD);
        DensityFunction finalDensity = built(level, graphs, shipped).noiseRouter().finalDensity();
        helper.assertTrue(finalDensity.minValue() == constant && finalDensity.maxValue() == constant, "The final density is not the constant " + constant);
        helper.assertTrue(finalDensity.compute(new DensityFunction.SinglePointContext(0, 0, 0)) == constant, "The final density does not compute the constant");
        router.set("final_density", new RouterGraph.Constant(2000000));
        helper.assertTrue(graphSettings.build(graphs, Level.OVERWORLD, shipped.generatorSettings().value(), level.registryAccess(),
                graphSettings.noises(level.registryAccess())).error().isPresent(), "A constant out of range built");
        helper.succeed();
    }


    /** The noise settings of 0.5.0 still fill a chunk with the island density function of 0.5.0. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void oldNoiseSettingsStillFillAChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) oldDimensions(level).dimensions().get(LevelStem.OVERWORLD).generator();
        helper.assertTrue(generator.generatorSettings().is(AeroliteWorldgen.ISLANDS_OVERWORLD_NOISE), "The generator does not name the noise settings of 0.5.0");
        RandomState randomState = randomState(level, generator);
        VoronoiLattice.Cell cell = firstIsland(helper, oldIslands(helper, randomState));
        BlockPos site = BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ());
        CompletableFuture<ChunkAccess> fill = fill(level, generator, randomState, new ChunkPos(site));
        helper.succeedWhen(() -> {
            helper.assertTrue(fill.isDone(), "The chunk fill is not done");
            ChunkAccess chunk = fill.join();
            helper.assertTrue(columnHolds(chunk, site, Blocks.STONE), "The site column of a filled cell of 0.5.0 holds no stone");
            assertNoLava(helper, chunk);
        });
    }

    /** Every block of the chunk that lies in an Empty cell is air. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void emptyCellHoldsNoBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = generator(level, LevelStem.OVERWORLD);
        RandomState randomState = randomState(level, generator);
        IslandFunction islands = islands(helper, randomState);
        VoronoiLattice.Cell empty = null;
        for (int ix = 0; ix < 64 && empty == null; ix++) {
            VoronoiLattice.Cell candidate = islands.lattice().cell(ix, 0, 0);
            IslandVariation variation = islands.variation(candidate);
            if (variation != null && variation.empty()) {
                empty = candidate;
            }
        }
        helper.assertTrue(empty != null, "No Empty cell in the first 64 cells");
        VoronoiLattice.Cell cell = empty;
        ChunkPos pos = new ChunkPos(BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ()));
        CompletableFuture<ChunkAccess> fill = fill(level, generator, randomState, pos);
        helper.succeedWhen(() -> {
            helper.assertTrue(fill.isDone(), "The chunk fill is not done");
            ChunkAccess chunk = fill.join();
            int inCell = 0;
            BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
            for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        block.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                        if (islands.lattice().nearest(block.getX(), block.getY(), block.getZ()).equals(cell)) {
                            inCell++;
                            helper.assertTrue(chunk.getBlockState(block).isAir(), "The Empty cell holds " + chunk.getBlockState(block) + " at " + block);
                        }
                    }
                }
            }
            helper.assertTrue(inCell > 0, "The chunk holds no block of the Empty cell");
        });
    }

    /**
     * The biome of a block is the biome of the variation of its cell. A block above the row 0 takes the variation of the row 0 cell
     * with the least power rule value. The test variations hold three fixed biome sources, so each cell shows its variation.
     */
    @GameTest(template = "empty")
    public static void biomeFollowsTheVariationOfTheCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Map<ResourceKey<IslandVariation>, IslandVariation> variations = new LinkedHashMap<>();
        List<ResourceKey<Biome>> fixed = List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.THE_VOID);
        List<IslandVariationList.Entry> entries = new ArrayList<>();
        for (ResourceKey<Biome> biome : fixed) {
            ResourceKey<IslandVariation> key = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, biome.location().withPrefix("test_"));
            FixedBiomeSource source = new FixedBiomeSource(biomes.getHolderOrThrow(biome));
            variations.put(key, biome.equals(Biomes.THE_VOID)
                    ? IslandVariation.empty(source, IslandVariation.DEFAULT_CELL_WEIGHT)
                    : IslandVariation.island(Holder.direct(IslandValue.Kind.ISLAND_BODY.function()), source, new UnitRange(0.3, 0.7), IslandVariation.DEFAULT_FILL, 0, 64, 3));
            entries.add(IslandVariationList.Entry.variation(key, 1));
        }
        ResourceKey<IslandVariationList> list = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "test_biomes"));
        VariationLookup lookup = VariationLookup.of(variations, Map.of(list, new IslandVariationList(entries)));
        NoiseBasedChunkGenerator shipped = generator(level, LevelStem.OVERWORLD);
        NoiseGeneratorSettings settings = shipped.generatorSettings().value();
        IslandFunction function = new IslandFunction(new DensityFunction.NoiseHolder(level.registryAccess().registryOrThrow(Registries.NOISE).getHolderOrThrow(AeroliteWorldgen.ISLANDS_NOISE)),
                256, 128, 0, 0.8, list, lookup);
        NoiseRouter router = settings.noiseRouter();
        NoiseRouter carried = new NoiseRouter(router.barrierNoise(), router.fluidLevelFloodednessNoise(), router.fluidLevelSpreadNoise(), router.lavaNoise(),
                new IslandCarrier(function, router.temperature()), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                router.initialDensityWithoutJaggedness(), router.finalDensity(), router.veinToggle(), router.veinRidged(), router.veinGap());
        RandomState randomState = RandomState.create(GraphSettings.withRouter(settings, carried), level.registryAccess().lookupOrThrow(Registries.NOISE), SEED);
        IslandBiomeSource source = new IslandBiomeSource(list, lookup, Optional.of(biomes.getHolderOrThrow(Biomes.PLAINS)));
        IslandFunction wired = IslandBiomeSource.find(randomState.sampler());
        helper.assertTrue(wired != null, "The sampler holds no carrier with an island function");
        helper.assertTrue(source.possibleBiomes().size() == 3, "The possible biomes are not the three biomes of the variations");
        Set<ResourceKey<Biome>> seen = new HashSet<>();
        for (int ix = 0; ix < 24; ix++) {
            for (int iz = 0; iz < 4; iz++) {
                VoronoiLattice.Cell site = wired.lattice().cell(ix, 0, iz);
                int qx = QuartPos.fromBlock(Mth.floor(site.siteX()));
                int qy = QuartPos.fromBlock(Mth.floor(site.siteY()));
                int qz = QuartPos.fromBlock(Mth.floor(site.siteZ()));
                VoronoiLattice.Cell cell = wired.lattice().nearest(QuartPos.toBlock(qx), QuartPos.toBlock(qy), QuartPos.toBlock(qz));
                helper.assertTrue(cell.iy() == 0, "A quart block next to a site left the row 0");
                ResourceKey<Biome> expected = biomeKey(wired.variation(cell));
                ResourceKey<Biome> actual = source.getNoiseBiome(qx, qy, qz, randomState.sampler()).unwrapKey().orElseThrow();
                helper.assertTrue(actual.equals(expected), "The biome " + actual.location() + " of a row 0 cell is not the biome " + expected.location() + " of its variation");
                seen.add(actual);
            }
        }
        helper.assertTrue(seen.size() == 3, "The sample did not reach every variation");
        Random random = new Random(SEED);
        int outside = 0;
        for (int i = 0; i < 200; i++) {
            int qx = random.nextInt(-400, 400);
            int qy = QuartPos.fromBlock(random.nextBoolean() ? 200 + random.nextInt(100) : -60 + random.nextInt(50));
            int qz = random.nextInt(-400, 400);
            int x = QuartPos.toBlock(qx);
            int y = QuartPos.toBlock(qy);
            int z = QuartPos.toBlock(qz);
            if (wired.lattice().nearest(x, y, z).iy() == 0) {
                continue;
            }
            outside++;
            VoronoiLattice.Cell nearest = nearestRowCell(wired.lattice(), x, y, z);
            ResourceKey<Biome> expected = biomeKey(wired.variation(nearest));
            ResourceKey<Biome> actual = source.getNoiseBiome(qx, qy, qz, randomState.sampler()).unwrapKey().orElseThrow();
            helper.assertTrue(actual.equals(expected), "The biome " + actual.location() + " above or below the row 0 is not the biome " + expected.location()
                    + " of the nearest row 0 cell");
        }
        helper.assertTrue(outside > 100, "Only " + outside + " blocks of the sample lie outside the row 0");
        helper.succeed();
    }

    /** The biome of a fixed biome source of a test variation. */
    private static ResourceKey<Biome> biomeKey(@Nullable IslandVariation variation) {
        if (variation == null) {
            throw new IllegalStateException("A cell of the test list has no variation");
        }
        return variation.biomeSource().possibleBiomes().iterator().next().unwrapKey().orElseThrow();
    }

    /** The cell of the row 0 with the least power rule value, by a brute force search over a wide window. */
    private static VoronoiLattice.Cell nearestRowCell(VoronoiLattice lattice, double x, double y, double z) {
        int bx = Math.floorDiv((int) x, lattice.cellSize());
        int bz = Math.floorDiv((int) z, lattice.cellSize());
        VoronoiLattice.Cell best = null;
        double bestValue = Double.POSITIVE_INFINITY;
        for (int dx = -ROW_SEARCH; dx <= ROW_SEARCH; dx++) {
            for (int dz = -ROW_SEARCH; dz <= ROW_SEARCH; dz++) {
                VoronoiLattice.Cell cell = lattice.cell(bx + dx, 0, bz + dz);
                double value = lattice.powerValue(cell, x, y, z);
                if (value < bestValue) {
                    bestValue = value;
                    best = cell;
                }
            }
        }
        return Objects.requireNonNull(best);
    }

    /** The biome of the shipped preset at a block of an island is a biome of the vanilla source of its variation. */
    @GameTest(template = "empty")
    public static void presetBiomeComesFromTheSourceOfTheCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (ResourceKey<LevelStem> stem : NOISE_SETTINGS.keySet()) {
            NoiseBasedChunkGenerator generator = generator(level, stem);
            RandomState randomState = randomState(level, generator);
            IslandFunction islands = islands(helper, randomState);
            VoronoiLattice.Cell cell = firstIsland(helper, islands);
            IslandVariation variation = Objects.requireNonNull(islands.variation(cell));
            int qx = QuartPos.fromBlock(Mth.floor(cell.siteX()));
            int qy = QuartPos.fromBlock(Mth.floor(cell.siteY()));
            int qz = QuartPos.fromBlock(Mth.floor(cell.siteZ()));
            Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(qx, qy, qz, randomState.sampler());
            helper.assertTrue(variation.biomeSource().possibleBiomes().contains(biome), "The biome of an island of " + stem.location() + " is not a biome of its variation");
            helper.assertTrue(biome.equals(variation.biomeSource().getNoiseBiome(qx, qy, qz, randomState.sampler())),
                    "The island biome source of " + stem.location() + " did not ask the source of the variation");
        }
        helper.succeed();
    }

    /** A beardifier without structures. The chunk sampler tests need no structure. */
    private static final DensityFunctions.BeardifierOrMarker NO_STRUCTURES = new DensityFunctions.BeardifierOrMarker() {

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return 0;
        }

        @Override
        public double minValue() {
            return 0;
        }

        @Override
        public double maxValue() {
            return 0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BeardifierOrMarker.CODEC;
        }
    };

    /** The noise chunk of one chunk. It opens the climate sampler of the biome step, whose functions hold the cache wrappers of the chunk. */
    private static final class SamplerChunk extends NoiseChunk {

        private SamplerChunk(RandomState randomState, ChunkPos pos, NoiseGeneratorSettings settings, LevelHeightAccessor height) {
            super(16 / settings.noiseSettings().getCellWidth(), randomState, pos.getMinBlockX(), pos.getMinBlockZ(), settings.noiseSettings().clampToHeightAccessor(height),
                    NO_STRUCTURES, settings, (x, y, z) -> new Aquifer.FluidStatus(settings.seaLevel(), Blocks.AIR.defaultBlockState()), Blender.empty());
        }

        private Climate.Sampler sampler(RandomState randomState) {
            return this.cachedClimateSampler(randomState.router(), List.of());
        }
    }

    /** The island biome source finds the island function in the climate sampler of a noise chunk and answers like with the sampler of the random state. */
    @GameTest(template = "empty")
    public static void chunkSamplerHoldsTheIslandFunction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (ResourceKey<LevelStem> stem : NOISE_SETTINGS.keySet()) {
            NoiseBasedChunkGenerator generator = generator(level, stem);
            RandomState randomState = randomState(level, generator);
            IslandBiomeSource source = (IslandBiomeSource) generator.getBiomeSource();
            VoronoiLattice.Cell cell = firstIsland(helper, islands(helper, randomState));
            BlockPos site = BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ());
            ProtoChunk chunk = protoChunk(level, new ChunkPos(site));
            Climate.Sampler sampler = new SamplerChunk(randomState, chunk.getPos(), generator.generatorSettings().value(), chunk).sampler(randomState);
            IslandFunction islands = IslandBiomeSource.find(sampler);
            helper.assertTrue(islands != null, "The chunk sampler of " + stem.location() + " holds no carrier with an island function");
            helper.assertTrue(islands.lattice().seed() == islands(helper, randomState).lattice().seed(), "The chunk sampler of " + stem.location() + " has another lattice seed");
            for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y += 4) {
                int qx = QuartPos.fromBlock(site.getX());
                int qy = QuartPos.fromBlock(y);
                int qz = QuartPos.fromBlock(site.getZ());
                IslandVariation variation = Objects.requireNonNull(islands.biomeVariation(QuartPos.toBlock(qx), QuartPos.toBlock(qy), QuartPos.toBlock(qz)));
                helper.assertTrue(source.getNoiseBiome(qx, qy, qz, sampler).equals(variation.biomeSource().getNoiseBiome(qx, qy, qz, sampler)),
                        "The island biome source of " + stem.location() + " did not ask the source of the variation with the chunk sampler");
            }
        }
        helper.succeed();
    }

    /**
     * A marker of the type interpolated, cache_once or cache_all_in_cell inside the graph of a variation computes its argument with the island context.
     * The noise chunk wraps each marker of the graph. A chunk sampler then gives the values of the sampler of the random state.
     */
    @GameTest(template = "empty")
    public static void chunkWrappersInAVariationGraphComputeWithTheIslandContext(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseGeneratorSettings shipped = generator(level, LevelStem.OVERWORLD).generatorSettings().value();
        Holder<NormalNoise.NoiseParameters> noise = level.registryAccess().registryOrThrow(Registries.NOISE).getHolderOrThrow(AeroliteWorldgen.ISLANDS_NOISE);
        DensityFunction value = new CellRandom(0, 1, 7);
        Map<String, DensityFunction> graphs = Map.of(
                "interpolated", DensityFunctions.interpolated(value),
                "cache_once", DensityFunctions.cacheOnce(value),
                "cache_all_in_cell", DensityFunctions.cacheAllInCell(value));
        ResourceKey<IslandVariation> key = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "test_marker"));
        ResourceKey<IslandVariationList> list = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "test_markers"));
        for (Map.Entry<String, DensityFunction> graph : graphs.entrySet()) {
            IslandVariation variation = IslandVariation.island(Holder.direct(graph.getValue()), new FixedBiomeSource(level.registryAccess().registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(Biomes.PLAINS)), IslandVariation.DEFAULT_CELL_WEIGHT, IslandVariation.DEFAULT_FILL, 0, 64, 3);
            VariationLookup lookup = VariationLookup.of(Map.of(key, variation), Map.of(list, new IslandVariationList(List.of(IslandVariationList.Entry.variation(key, 1)))));
            IslandFunction function = new IslandFunction(new DensityFunction.NoiseHolder(noise), 256, 128, 0, 0.8, list, lookup);
            NoiseRouter router = shipped.noiseRouter();
            NoiseRouter carried = new NoiseRouter(router.barrierNoise(), router.fluidLevelFloodednessNoise(), router.fluidLevelSpreadNoise(), router.lavaNoise(),
                    function, router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                    router.initialDensityWithoutJaggedness(), router.finalDensity(), router.veinToggle(), router.veinRidged(), router.veinGap());
            NoiseGeneratorSettings settings = GraphSettings.withRouter(shipped, carried);
            RandomState randomState = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), SEED);
            int inIslands = 0;
            for (int chunkX = 0; chunkX < 8; chunkX++) {
                ChunkPos pos = new ChunkPos(chunkX * 3, 0);
                Climate.Sampler sampler = new SamplerChunk(randomState, pos, settings, protoChunk(level, pos)).sampler(randomState);
                for (int x = 0; x < 16; x += 3) {
                    for (int z = 0; z < 16; z += 3) {
                        for (int y = -8; y < 140; y += 4) {
                            DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                            double direct = randomState.sampler().temperature().compute(context);
                            if (direct != function.lowest()) {
                                inIslands++;
                            }
                            helper.assertTrue(sampler.temperature().compute(context) == direct, "The marker " + graph.getKey() + " of a variation graph gave "
                                    + sampler.temperature().compute(context) + ", not " + direct + " at " + context.blockX() + " " + y + " " + context.blockZ());
                        }
                    }
                }
            }
            helper.assertTrue(inIslands > 0, "No sample of " + graph.getKey() + " lies in an island");
        }
        helper.succeed();
    }

    /**
     * A variation graph holds {@code flat_cache} over cell random, or {@code cache_2d} over the cell gauge. Both give the direct values.
     * The island function drops both markers. So the chunk sampler of a noise chunk gives the values of the random state sampler.
     */
    @GameTest(template = "empty")
    public static void droppedCachesInAVariationGraphGiveTheDirectValues(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseGeneratorSettings shipped = generator(level, LevelStem.OVERWORLD).generatorSettings().value();
        Holder<NormalNoise.NoiseParameters> noise = level.registryAccess().registryOrThrow(Registries.NOISE).getHolderOrThrow(AeroliteWorldgen.ISLANDS_NOISE);
        Map<String, DensityFunction> graphs = Map.of(
                "flat_cache", DensityFunctions.flatCache(new CellRandom(0, 1, 7)),
                "cache_2d", DensityFunctions.cache2d(IslandValue.Kind.CELL_GAUGE.function()));
        ResourceKey<IslandVariation> key = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "test_dropped"));
        ResourceKey<IslandVariationList> list = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "test_dropped"));
        for (Map.Entry<String, DensityFunction> graph : graphs.entrySet()) {
            IslandVariation variation = IslandVariation.island(Holder.direct(graph.getValue()), new FixedBiomeSource(level.registryAccess().registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(Biomes.PLAINS)), IslandVariation.DEFAULT_CELL_WEIGHT, IslandVariation.DEFAULT_FILL, 0, 64, 3);
            VariationLookup lookup = VariationLookup.of(Map.of(key, variation), Map.of(list, new IslandVariationList(List.of(IslandVariationList.Entry.variation(key, 1)))));
            IslandFunction function = new IslandFunction(new DensityFunction.NoiseHolder(noise), 256, 128, 0, 0.8, list, lookup);
            NoiseRouter router = shipped.noiseRouter();
            NoiseRouter carried = new NoiseRouter(router.barrierNoise(), router.fluidLevelFloodednessNoise(), router.fluidLevelSpreadNoise(), router.lavaNoise(),
                    function, router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                    router.initialDensityWithoutJaggedness(), router.finalDensity(), router.veinToggle(), router.veinRidged(), router.veinGap());
            NoiseGeneratorSettings settings = GraphSettings.withRouter(shipped, carried);
            RandomState randomState = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), SEED);
            int inIslands = 0;
            int distinct = 0;
            for (int chunkX = 0; chunkX < 8; chunkX++) {
                ChunkPos pos = new ChunkPos(chunkX * 3, 0);
                Climate.Sampler sampler = new SamplerChunk(randomState, pos, settings, protoChunk(level, pos)).sampler(randomState);
                for (int x = 0; x < 16; x += 3) {
                    for (int z = 0; z < 16; z += 3) {
                        double previous = Double.NaN;
                        for (int y = -8; y < 140; y += 4) {
                            DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                            double direct = randomState.sampler().temperature().compute(context);
                            if (direct != function.lowest()) {
                                inIslands++;
                                if (direct != 0 && direct != previous) {
                                    distinct++;
                                }
                                previous = direct;
                            }
                            double chunk = sampler.temperature().compute(context);
                            helper.assertTrue(chunk == direct, "The marker " + graph.getKey() + " of a variation graph gave " + chunk + ", not " + direct
                                    + " at " + context.blockX() + " " + y + " " + context.blockZ());
                        }
                    }
                }
            }
            helper.assertTrue(inIslands > 0, "No sample of " + graph.getKey() + " lies in an island");
            helper.assertTrue(distinct > 1, "The samples of " + graph.getKey() + " hold fewer than two distinct values");
        }
        helper.succeed();
    }

    /**
     * An edit of the Basic variation reaches the built router and changes the blocks of a chunk.
     * A larger fill adds blocks. Solo on the Empty variation removes every block. A constant graph below zero for the variation removes every block.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void variationEditReachesTheBuiltRouterAndChangesTheBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess registries = level.registryAccess();
        IslandGraphs graphs = graphSettings.start(presetDimensions(level), registries);
        IslandGraphs.VariationLoader variations = graphSettings.variationLoader(registries);
        IslandGraphs.EntryLoader loader = graphSettings.loader(registries);
        NoiseBasedChunkGenerator shipped = generator(level, LevelStem.OVERWORLD);
        VoronoiLattice.Cell cell = firstIsland(helper, islands(helper, randomState(level, shipped)));
        ChunkPos pos = new ChunkPos(BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ()));
        int before = solids(level, shipped, graphSettings.build(graphs, Level.OVERWORLD, shipped.generatorSettings().value(), registries, graphSettings.noises(registries))
                .getOrThrow(message -> new IllegalStateException("The start graphs do not build: " + message)).settings(), pos);
        helper.assertTrue(before > 0, "The start graphs fill no block in the chunk of the first island");

        ResourceLocation basic = IslandVariationRegistries.basicOf(Level.OVERWORLD).location();
        JsonObject json = graphs.variation(basic, variations).getOrThrow();
        JsonArray fill = new JsonArray();
        fill.add(1.0);
        fill.add(1.0);
        json.add("fill", fill);
        graphs.putVariation(basic, json);
        GraphSettings.Built built = graphSettings.build(graphs, Level.OVERWORLD, shipped.generatorSettings().value(), registries, graphSettings.noises(registries))
                .getOrThrow(message -> new IllegalStateException("The edited graphs do not build: " + message));
        helper.assertTrue(built.variations().getAsJsonObject("variations").getAsJsonObject(basic.toString()).get("fill").equals(fill),
                "The build JSON does not hold the edited fill");
        IslandFunction function = (IslandFunction) unwrap(built.settings().noiseRouter().finalDensity());
        helper.assertTrue(function.picks().variations().stream().anyMatch(variation -> variation.fill().equals(new UnitRange(1.0, 1.0))),
                "The island function of the build does not read the edited variation");
        int edited = solids(level, shipped, built.settings(), pos);
        helper.assertTrue(edited > before, "The fill edit gave " + edited + " blocks, not more than " + before);

        PreviewOverride solo = PreviewOverride.NONE.withSolo(IslandVariationRegistries.emptyOf(Level.OVERWORLD));
        NoiseGeneratorSettings soloSettings = graphSettings.build(graphs, Level.OVERWORLD, shipped.generatorSettings().value(), registries,
                graphSettings.noises(registries), solo).getOrThrow(message -> new IllegalStateException("The Solo build fails: " + message)).settings();
        helper.assertTrue(solids(level, shipped, soloSettings, pos) == 0, "Solo on the Empty variation left blocks");

        IslandGraphs.VariationFunction entry = graphs.variationFunction(basic, variations, loader).getOrThrow();
        FunctionGraph graph = graphs.function(entry.entry(), loader).getOrThrow();
        Integer body = graph.output();
        helper.assertTrue(body != null, "The graph of the Basic variation has no output");
        graph.removeTree(body);
        graph.setOutput(graph.add(FunctionNode.Typed.bareConstant(-1.0)));
        graphs.markEdited(entry.entry(), graph);
        NoiseGeneratorSettings constant = graphSettings.build(graphs, Level.OVERWORLD, shipped.generatorSettings().value(), registries, graphSettings.noises(registries))
                .getOrThrow(message -> new IllegalStateException("The constant variation graph does not build: " + message)).settings();
        helper.assertTrue(solids(level, shipped, constant, pos) == 0, "A constant variation graph below zero left blocks");
        helper.succeed();
    }

    /** A list with a missing entry fails the build. So does a router whose carrier leaves its climate outputs. */
    @GameTest(template = "empty")
    public static void islandProblemFailsTheBuild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess registries = level.registryAccess();
        NoiseGeneratorSettings base = generator(level, LevelStem.OVERWORLD).generatorSettings().value();
        IslandGraphs graphs = graphSettings.start(presetDimensions(level), registries);
        ResourceLocation list = IslandVariationRegistries.listOf(Level.OVERWORLD).location();
        JsonObject broken = graphs.list(list, graphSettings.variationLoader(registries)).getOrThrow();
        broken.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("variation", "aerolite:missing_variation");
        graphs.putList(list, broken);
        String error = graphSettings.build(graphs, Level.OVERWORLD, base, registries, graphSettings.noises(registries)).error()
                .map(DataResult.Error::message).orElse("");
        helper.assertTrue(error.contains("aerolite:missing_variation"), "A list with a missing entry built: " + error);

        IslandGraphs carrierless = graphSettings.start(presetDimensions(level), registries);
        RouterGraph router = carrierless.router(Level.OVERWORLD);
        helper.assertTrue(router != null, "The Overworld has no router graph");
        for (String field : List.of("temperature", "vegetation", "continents", "erosion", "depth", "ridges")) {
            Integer root = router.linked(field);
            if (root != null) {
                router.removeTree(root);
            }
            router.set(field, new RouterGraph.Constant(0.0));
        }
        helper.assertTrue(graphSettings.build(carrierless, Level.OVERWORLD, base, registries, graphSettings.noises(registries)).isError(),
                "A router without a carrier built");
        helper.succeed();
    }

    /** The solid blocks of the chunk that the preview terrain fill of the settings writes. */
    private static int solids(ServerLevel level, NoiseBasedChunkGenerator shipped, NoiseGeneratorSettings settings, ChunkPos pos) {
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(shipped.getBiomeSource(), Holder.direct(settings));
        ProtoChunk chunk = protoChunk(level, pos);
        terrain(level, generator, randomState(level, generator)).fill(chunk);
        int solids = 0;
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!chunk.getBlockState(block.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z)).isAir()) {
                        solids++;
                    }
                }
            }
        }
        return solids;
    }

    /** Twelve threads fill the chunks of a region in reverse sequence with the blocks of one thread in sequence. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void parallelFillMatchesSequentialFill(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = generator(level, LevelStem.OVERWORLD);
        List<ChunkPos> positions = new ArrayList<>();
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                positions.add(new ChunkPos(x + 3, z - 2));
            }
        }
        List<ProtoChunk> sequential = fillRegion(level, generator, positions, 1, false);
        List<ProtoChunk> parallel = fillRegion(level, generator, positions, 12, true);
        int solids = 0;
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (int i = 0; i < positions.size(); i++) {
            ChunkPos pos = positions.get(i);
            for (int y = sequential.get(i).getMinBuildHeight(); y < sequential.get(i).getMaxBuildHeight(); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        block.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                        if (!sequential.get(i).getBlockState(block).isAir()) {
                            solids++;
                        }
                        helper.assertTrue(sequential.get(i).getBlockState(block) == parallel.get(i).getBlockState(block), "The parallel fill differs at " + block);
                    }
                }
            }
        }
        helper.assertTrue(solids > 0, "The region holds no block");
        helper.succeed();
    }

    /** The terrain fill of each chunk with a new random state, on the threads in the sequence or in reverse. */
    private static List<ProtoChunk> fillRegion(ServerLevel level, NoiseBasedChunkGenerator generator, List<ChunkPos> positions, int threads, boolean reverse) throws Exception {
        IslandTerrain terrain = terrain(level, generator, randomState(level, generator));
        List<ProtoChunk> chunks = positions.stream().map(pos -> protoChunk(level, pos)).toList();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> fills = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ProtoChunk chunk = chunks.get(reverse ? chunks.size() - 1 - i : i);
                fills.add(pool.submit(() -> terrain.fill(chunk)));
            }
            for (Future<?> fill : fills) {
                fill.get();
            }
        } finally {
            pool.shutdown();
        }
        return chunks;
    }

}
