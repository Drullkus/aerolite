package us.drullk.aerolite.gametest;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tamaized.beanification.Autowired;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.IslandGenerators;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.IslandTerrain;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.VoronoiLattice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Checks the Islands preset: its dimensions, the wired island density function, the surface floor, the island settings edit,
 * the blocks of a generated chunk and the terrain fill of the preview.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class IslandGameTests {

    private static final long SEED = 1234L;
    private static final Map<ResourceKey<LevelStem>, ResourceKey<NoiseGeneratorSettings>> NOISE_SETTINGS = Map.of(
            LevelStem.OVERWORLD, AeroliteWorldgen.ISLANDS_OVERWORLD_NOISE,
            LevelStem.NETHER, AeroliteWorldgen.ISLANDS_NETHER_NOISE,
            LevelStem.END, AeroliteWorldgen.ISLANDS_END_NOISE
    );

    // GameTest methods are static. Beanification fills this field.
    @Autowired
    private static IslandGenerators generators;

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

    /** The island density function behind the final density of the wired router, inside its holders and markers. */
    private static VoronoiIslands islands(GameTestHelper helper, RandomState randomState) {
        DensityFunction function = randomState.router().finalDensity();
        while (true) {
            if (function instanceof DensityFunctions.HolderHolder holder) {
                function = holder.function().value();
            } else if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                function = marker.wrapped();
            } else {
                break;
            }
        }
        helper.assertTrue(function instanceof VoronoiIslands, "The final density is not the island density function");
        return (VoronoiIslands) function;
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
        }
        helper.assertTrue(dimensions.dimensions().get(LevelStem.OVERWORLD).type().is(AeroliteWorldgen.OVERWORLD_TYPE), "The Overworld has not the Aerolite dimension type");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void randomStateSeedsTheIslandLattice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = generator(level, LevelStem.OVERWORLD);
        VoronoiIslands wired = islands(helper, randomState(level, generator));
        helper.assertTrue(wired.lattice().seed() != 0L, "The wired island function has the unwired seed");
        VoronoiIslands again = islands(helper, randomState(level, generator));
        helper.assertTrue(again.lattice().seed() == wired.lattice().seed(), "Two random states of one seed gave two lattice seeds");
        RandomState other = RandomState.create(generator.generatorSettings().value(), level.registryAccess().lookupOrThrow(Registries.NOISE), SEED + 1);
        helper.assertTrue(islands(helper, other).lattice().seed() != wired.lattice().seed(), "Two world seeds gave one lattice seed");
        helper.succeed();
    }

    /** The site column of the first empty cell of the row 0 along X from the origin. */
    private static BlockPos emptyColumn(GameTestHelper helper, VoronoiIslands islands) {
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

    private static void assertSurfaceFloor(GameTestHelper helper, NoiseRouter router, VoronoiIslands islands, int minY, String dimension) {
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

    @GameTest(template = "empty")
    public static void islandSettingsEditRebuildsOnlyTheTerrainFunctions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldDimensions preset = presetDimensions(level);
        IslandSettings shipped = generators.settings(preset).orElseThrow();
        helper.assertTrue(shipped.equals(IslandSettings.DEFAULT), "The shipped island settings differ from the default settings");
        WorldDimensions same = generators.withSettings(preset, shipped);
        IslandSettings edited = shipped.withDistortion(shipped.distortion() + 10).withCellSize(512);
        WorldDimensions changed = generators.withSettings(preset, edited);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        for (ResourceKey<LevelStem> stem : NOISE_SETTINGS.keySet()) {
            String dimension = stem.location().toString();
            NoiseBasedChunkGenerator before = (NoiseBasedChunkGenerator) preset.dimensions().get(stem).generator();
            helper.assertTrue(same.dimensions().get(stem) == preset.dimensions().get(stem), "The shipped distortion rebuilt " + dimension);
            LevelStem editedStem = changed.dimensions().get(stem);
            helper.assertTrue(editedStem.type() == preset.dimensions().get(stem).type(), "The distortion edit changed the dimension type of " + dimension);
            NoiseBasedChunkGenerator after = (NoiseBasedChunkGenerator) editedStem.generator();
            helper.assertTrue(after.getBiomeSource() == before.getBiomeSource(), "The distortion edit changed the biome source of " + dimension);
            helper.assertFalse(after.generatorSettings().is(NOISE_SETTINGS.get(stem)), "The distortion edit kept the registry settings of " + dimension);
            helper.assertTrue(IslandSettings.of(generators.islands(after).orElseThrow()).equals(edited), "The edit did not reach the island function of " + dimension);
            NoiseRouter beforeRouter = before.generatorSettings().value().noiseRouter();
            NoiseRouter afterRouter = after.generatorSettings().value().noiseRouter();
            helper.assertTrue(afterRouter.temperature() == beforeRouter.temperature() && afterRouter.erosion() == beforeRouter.erosion(),
                    "The distortion edit rebuilt a climate function of " + dimension);
            RandomState wired = randomState(level, after);
            helper.assertTrue(islands(helper, wired).lattice().seed() == islands(helper, randomState(level, before)).lattice().seed(),
                    "The distortion edit changed the lattice seed of " + dimension);
            helper.assertTrue(IslandSettings.of(islands(helper, wired)).equals(edited), "The wired island function of " + dimension + " lost the edit");
            int minY = after.generatorSettings().value().noiseSettings().minY();
            assertSurfaceFloor(helper, wired.router(), islands(helper, wired), minY, dimension);
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
        VoronoiIslands islands = islands(helper, randomState);
        VoronoiLattice.Cell cell = firstIsland(helper, islands);
        BlockPos site = BlockPos.containing(cell.siteX(), cell.siteY(), cell.siteZ());
        CompletableFuture<ChunkAccess> fill = fill(level, generator, randomState, new ChunkPos(site));
        helper.succeedWhen(() -> {
            helper.assertTrue(fill.isDone(), "The chunk fill is not done");
            ChunkAccess chunk = fill.join();
            helper.assertTrue(columnHolds(chunk, site, Blocks.STONE), "The site column of a filled cell holds no stone");
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
    private static VoronoiLattice.Cell firstIsland(GameTestHelper helper, VoronoiIslands islands) {
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

}
