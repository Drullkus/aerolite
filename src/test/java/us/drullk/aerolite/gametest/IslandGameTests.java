package us.drullk.aerolite.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.VoronoiLattice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Checks the Islands preset: its dimensions, the wired island density function and the blocks of a generated chunk.
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
            helper.assertTrue(chunk.getBlockState(site).is(Blocks.STONE), "The site of a filled cell is not stone");
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
                helper.assertTrue(chunk.getBlockState(site).is(entry.getValue()), "The site of a filled cell is not the default block of " + entry.getKey().location());
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

    private static void assertNoLava(GameTestHelper helper, ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            helper.assertFalse(section.maybeHas(state -> state.is(Blocks.LAVA)), "A section of the chunk holds lava");
        }
    }

}
