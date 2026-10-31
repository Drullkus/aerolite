package us.drullk.aerolite.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tamaized.beanification.Autowired;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.shaft.ShaftFrame;
import us.drullk.aerolite.world.shaft.ShaftFrames;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;
import us.drullk.aerolite.world.status.AeroliteChunkStatuses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GameTests of the shafts chunk step and the blocks it writes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_18-08_4c0300c8-1a76-4b8e-86b0-0cb9daa7d32a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-01_7b2c6d29-0ea3-4452-a1db-06e54416a4d5.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class ShaftGameTests {

    // GameTest methods are static. Beanification fills these fields.
    @Autowired
    private static AeroliteChunkStatuses statuses;
    @Autowired
    private static ShaftMazes mazes;
    @Autowired
    private static ShaftPainter painter;
    @Autowired
    private static Ring ring;
    @Autowired
    private static ShaftFrames frames;

    private ShaftGameTests() {
    }

    @GameTest(template = "empty")
    public static void shaftsStepSitsBetweenCarversAndFeatures(GameTestHelper helper) {
        ChunkStatus shafts = statuses.shafts();
        helper.assertTrue(shafts.getParent() == ChunkStatus.CARVERS, "The shafts status does not follow the carvers status");
        helper.assertTrue(ChunkStatus.FEATURES.getParent() == shafts, "The features status does not follow the shafts status");
        helper.assertTrue(ChunkPyramid.GENERATION_PYRAMID.getStepTo(shafts).targetStatus() == shafts, "The generation pyramid has no shafts step");
        helper.assertTrue(ChunkPyramid.LOADING_PYRAMID.getStepTo(shafts).targetStatus() == shafts, "The loading pyramid has no shafts step");
        helper.assertTrue(ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).getAccumulatedRadiusOf(shafts) >= 1,
                "The features step does not wait for the shafts of its neighbors");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void painterBuildsAShaftCrossSection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = new ShaftLayout(mazes, 42, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), twoVariations());
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
        ShaftVariation variation = layout.variation(shaft);
        int chunkZ = 0;
        while (!layout.columnsOn(shaft, 16 * chunkZ - 16, 16 * chunkZ + 32).isEmpty()) {
            chunkZ++;
        }
        ChunkPos pos = new ChunkPos(SectionPos.blockToSectionCoord(shaft.position()), chunkZ);
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        painter.paint(layout, chunk);

        int x = shaft.position();
        int y = shaft.centerY();
        int paths = 0;
        for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
            helper.assertTrue(chunk.getBlockState(new BlockPos(x, y, z)) == variation.interior(), "The shaft core is not the interior block of its variation");
            for (int block = 0; block < Ring.BLOCKS; block++) {
                int dy = ring.first(block);
                int dx = ring.second(block);
                if (SectionPos.blockToSectionCoord(x + dx) != pos.x) {
                    continue;
                }
                BlockState outer = chunk.getBlockState(new BlockPos(x + dx, y + dy, z));
                if (outer.isAir()) {
                    paths++;
                    BlockPos beneath = new BlockPos(x + clampInterior(dx), y + clampInterior(dy), z);
                    helper.assertTrue(chunk.getBlockState(beneath) == variation.path(), "A maze path has no maze path block of its variation under it");
                } else {
                    helper.assertTrue(outer == variation.wall(), "An outer layer block is not the maze wall block of its variation");
                }
            }
            helper.assertTrue(chunk.getBlockState(new BlockPos(x, y + Ring.OUTER + 1, z)).isAir(), "The block above the shaft is not air");
        }
        helper.assertTrue(paths > 0, "The shaft has no maze path");
        helper.assertTrue(chunk.getBlockState(new BlockPos(x, level.getMinBuildHeight(), pos.getMinBlockZ())).is(Blocks.AIR), "The void holds a block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void erosionKeepsTheCore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DimensionSettings settings = TestSettings.withDecay(TestSettings.OVERWORLD, 32, 0, ShaftVariation.THRESHOLD_STEP);
        ShaftLayout layout = new ShaftLayout(mazes, 42, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
        int eroded = 0;
        for (int chunkZ = 0; chunkZ < 6; chunkZ++) {
            if (!layout.columnsOn(shaft, 16 * chunkZ - 16, 16 * chunkZ + 32).isEmpty()) {
                continue;
            }
            ChunkPos pos = new ChunkPos(SectionPos.blockToSectionCoord(shaft.position()), chunkZ);
            ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
            painter.paint(layout, chunk);
            BlockState interior = layout.variation(shaft).interior();
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
                    for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                        int x = shaft.position() + second;
                        if (SectionPos.blockToSectionCoord(x) != pos.x) {
                            continue;
                        }
                        BlockState state = chunk.getBlockState(new BlockPos(x, shaft.centerY() + first, z));
                        if (Math.max(Math.abs(first), Math.abs(second)) <= 1) {
                            helper.assertTrue(state == interior, "Erosion removed a block of the 3x3 core");
                        } else if (Math.max(Math.abs(first), Math.abs(second)) < Ring.OUTER && state.isAir()) {
                            eroded++;
                        }
                    }
                }
            }
        }
        helper.assertTrue(eroded > 0, "Erosion removed no interior block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void erosionKeepsTheShaftCoresThroughJunctionBoxes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DimensionSettings settings = TestSettings.withDecay(TestSettings.OVERWORLD, 32, 0, ShaftVariation.THRESHOLD_STEP);
        ShaftLayout layout = new ShaftLayout(mazes, 42, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        ShaftLayout.Column column = null;
        for (int cell = 0; column == null; cell++) {
            for (int other = 0; column == null && other < 40; other++) {
                column = layout.column(cell, other).orElse(null);
            }
        }
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        int boxAir = 0;
        for (ColumnJunction junction : column.junctions()) {
            for (int a = -Ring.BOX; a <= Ring.BOX; a++) {
                for (int u = -1; u <= 1; u++) {
                    for (int v = -1; v <= 1; v++) {
                        BlockPos columnCore = new BlockPos(column.x() + u, junction.center() + a, column.z() + v);
                        BlockPos shaftCore = junction.alongX()
                                ? new BlockPos(column.x() + a, junction.center() + u, column.z() + v)
                                : new BlockPos(column.x() + v, junction.center() + u, column.z() + a);
                        helper.assertFalse(paintedAt(chunks, layout, level, columnCore).isAir(), "Erosion cut the column core in a junction box");
                        helper.assertFalse(paintedAt(chunks, layout, level, shaftCore).isAir(), "Erosion cut the shaft core in a junction box");
                    }
                }
            }
            for (int d = -Ring.BOX; d <= Ring.BOX; d++) {
                if (paintedAt(chunks, layout, level, new BlockPos(column.x() + Ring.BOX, junction.center() + d, column.z() + Ring.BOX)).isAir()) {
                    boxAir++;
                }
            }
        }
        helper.assertTrue(boxAir > 0, "Erosion removed no box edge block");
        helper.succeed();
    }

    /** True when the maze grid block at the offset and its maze path block lie in two chunks. */
    private static boolean straddlesChunkEdge(int center, int offset) {
        return SectionPos.blockToSectionCoord(center + offset) != SectionPos.blockToSectionCoord(center + clampInterior(offset));
    }

    /**
     * Checks that every maze path of a shaft or column in a range along it has its maze path block.
     * Returns the count of maze paths whose maze path block lies in another chunk.
     */
    private static int checkMazePaths(GameTestHelper helper, Map<Long, ProtoChunk> chunks, ShaftLayout layout, ShaftFrame frame, ShaftVariation variation, int min, int max) {
        ServerLevel level = helper.getLevel();
        int crossings = 0;
        for (int along = min; along <= max; along++) {
            for (int block = 0; block < Ring.BLOCKS; block++) {
                int first = ring.first(block);
                int second = ring.second(block);
                BlockPos surface = frame.at(along, first, second);
                if (!paintedAt(chunks, layout, level, surface).isAir()) {
                    continue;
                }
                BlockPos beneath = frame.at(along, clampInterior(first), clampInterior(second));
                helper.assertTrue(paintedAt(chunks, layout, level, beneath) == variation.path(), "A maze path has no maze path block under it");
                if (new ChunkPos(surface).toLong() != new ChunkPos(beneath).toLong()) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    @GameTest(template = "empty")
    public static void mazePathBlockLiesInTheNextChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DimensionSettings settings = twoVariations().withPlacement(TestSettings.PLACEMENT.withColumnChance(1));
        ShaftLayout layout = new ShaftLayout(mazes, 42, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        ShaftLayout.Shaft shaft = null;
        for (int cell = 0; shaft == null && cell < 64; cell++) {
            ShaftLayout.Shaft candidate = layout.shaft(ShaftLayout.Axis.Z, cell).orElseThrow();
            if (straddlesChunkEdge(candidate.position(), Ring.OUTER) || straddlesChunkEdge(candidate.position(), -Ring.OUTER)) {
                shaft = candidate;
            }
        }
        helper.assertTrue(shaft != null, "No Z shaft of the first cells straddles a chunk edge");
        int chunkZ = 0;
        while (!layout.columnsOn(shaft, 16 * chunkZ - 16, 16 * chunkZ + 32).isEmpty()) {
            chunkZ++;
        }
        int shaftCrossings = checkMazePaths(helper, chunks, layout, frames.of(shaft), layout.variation(shaft), 16 * chunkZ, 16 * chunkZ + 15);
        helper.assertTrue(shaftCrossings > 0, "No maze path of the shaft has its maze path block in the next chunk");

        ShaftLayout.Column column = null;
        for (int cell = 0; column == null && cell < 64; cell++) {
            for (int other = 0; column == null && other < 64; other++) {
                ShaftLayout.Column candidate = layout.column(cell, other).orElse(null);
                if (candidate != null && (straddlesChunkEdge(candidate.x(), Ring.OUTER) || straddlesChunkEdge(candidate.x(), -Ring.OUTER)
                        || straddlesChunkEdge(candidate.z(), Ring.OUTER) || straddlesChunkEdge(candidate.z(), -Ring.OUTER))) {
                    column = candidate;
                }
            }
        }
        helper.assertTrue(column != null, "No column of the first cells straddles a chunk edge");
        int bottom = column.junctions().getFirst().center() + Ring.BOX + 1;
        int top = column.junctions().getLast().center() - Ring.BOX - 1;
        int columnCrossings = checkMazePaths(helper, chunks, layout, frames.of(column), layout.variation(column), bottom, top);
        helper.assertTrue(columnCrossings > 0, "No maze path of the column has its maze path block in the next chunk");
        helper.succeed();
    }

    /** The world seed and the position of a report of exposed interior blocks at a chunk edge. */
    @GameTest(template = "empty")
    public static void reportedShaftsKeepTheirMazePathBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = 3446441043220923556L;
        BlockPos around = new BlockPos(421, 220, 166);
        int reach = 32;
        DimensionSettings settings = TestSettings.withDecay(DimensionSettings.defaults(Level.OVERWORLD), ShaftVariation.DEFAULT_DECAY_SCALE, 1, 1);
        ShaftLayout layout = new ShaftLayout(mazes, seed, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        int crossings = 0;
        for (ShaftLayout.Shaft shaft : layout.shafts(ShaftLayout.Axis.Z, around.getX() - reach, around.getX() + reach)) {
            crossings += checkMazePaths(helper, chunks, layout, frames.of(shaft), layout.variation(shaft), around.getZ() - reach, around.getZ() + reach);
        }
        for (ShaftLayout.Shaft shaft : layout.shafts(ShaftLayout.Axis.X, around.getZ() - reach, around.getZ() + reach)) {
            crossings += checkMazePaths(helper, chunks, layout, frames.of(shaft), layout.variation(shaft), around.getX() - reach, around.getX() + reach);
        }
        helper.assertTrue(crossings > 0, "No maze path near the position has its maze path block in the next chunk");
        helper.succeed();
    }

    private static BlockState paintedAt(Map<Long, ProtoChunk> chunks, ShaftLayout layout, ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        ProtoChunk chunk = chunks.computeIfAbsent(chunkPos.toLong(), key -> {
            ProtoChunk painted = new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
            painter.paint(layout, painted);
            return painted;
        });
        return chunk.getBlockState(pos);
    }

    @GameTest(template = "empty")
    public static void presetIsNormalAndBuildsShafts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<WorldPreset> presets = level.registryAccess().registryOrThrow(Registries.WORLD_PRESET);
        Holder<WorldPreset> preset = presets.getHolderOrThrow(AeroliteWorldgen.PRESET);
        helper.assertTrue(preset.is(WorldPresetTags.NORMAL), "The preset is not in the normal world type list");
        WorldDimensions dimensions = preset.value().createWorldDimensions();
        for (ResourceKey<LevelStem> stem : List.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END)) {
            helper.assertTrue(dimensions.dimensions().get(stem).generator() instanceof AeroliteChunkGenerator, "A dimension of the preset has no Aerolite generator");
        }
        AeroliteChunkGenerator generator = (AeroliteChunkGenerator) dimensions.dimensions().get(LevelStem.OVERWORLD).generator();
        ShaftLayout layout = generator.layout(level);
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.X, 0).orElseThrow();
        ChunkPos pos = new ChunkPos(0, SectionPos.blockToSectionCoord(shaft.position()));
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        generator.buildShafts(level, chunk);
        helper.assertTrue(chunk.getBlockState(new BlockPos(pos.getMinBlockX(), shaft.centerY(), shaft.position())).is(layout.variation(shaft).interior().getBlock()),
                "The generator wrote no shaft core");
        helper.succeed();
    }

    /** Settings whose Overworld variations differ in every block, without decay and erosion. */
    private static DimensionSettings twoVariations() {
        ShaftVariation deepslate = new ShaftVariation(1, 0.8F, TestSettings.DECAY_SCALE, TestSettings.DECAY_THRESHOLD, TestSettings.EROSION_THRESHOLD,
                Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        return TestSettings.withDecay(TestSettings.OVERWORLD.withVariations(List.of(TestSettings.STONE.withWeight(1), deepslate)),
                TestSettings.DECAY_SCALE, 1, 1);
    }

    @GameTest(template = "empty")
    public static void junctionBoxTakesTheVariationOfItsHorizontalShaft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = new ShaftLayout(mazes, 7, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), twoVariations().withPlacement(TestSettings.PLACEMENT.withColumnChance(1)));
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        int columns = 0;
        int mixed = 0;
        for (int cell = 0; cell < 6; cell++) {
            for (int other = 0; other < 6; other++) {
                ShaftLayout.Column column = layout.column(cell, other).orElse(null);
                if (column == null) {
                    continue;
                }
                columns++;
                ShaftVariation columnVariation = layout.variation(column);
                for (ColumnJunction junction : column.junctions()) {
                    ShaftVariation boxVariation = layout.variation(junction.alongX() ? column.xShaft() : column.zShaft());
                    if (boxVariation != columnVariation) {
                        mixed++;
                    }
                    BlockPos boxEdge = new BlockPos(column.x() + Ring.BOX, junction.center() + Ring.BOX, column.z() + Ring.BOX);
                    helper.assertTrue(paintedAt(chunks, layout, level, boxEdge) == boxVariation.interior(), "A junction box block is not the interior block of its horizontal shaft");
                }
                // An even Y between the boxes holds no ruling, so ring block 1 there is always maze wall.
                int between = (column.junctions().getFirst().center() + column.junctions().getLast().center()) / 2 & ~1;
                helper.assertTrue(paintedAt(chunks, layout, level, new BlockPos(column.x(), between, column.z())) == columnVariation.interior(),
                        "The column core is not the interior block of the column");
                BlockState wall = paintedAt(chunks, layout, level, new BlockPos(column.x() + ring.first(1), between, column.z() + ring.second(1)));
                helper.assertTrue(wall == columnVariation.wall(), "A column wall block is not the maze wall block of the column");
            }
        }
        helper.assertTrue(columns > 0, "The layout has no column");
        helper.assertTrue(mixed > 0, "No junction box differs in variation from its column");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unlistedDimensionGetsNoShafts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<WorldPreset> preset = level.registryAccess().registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(AeroliteWorldgen.PRESET);
        AeroliteChunkGenerator presetGenerator = (AeroliteChunkGenerator) preset.value().createWorldDimensions().dimensions().get(LevelStem.OVERWORLD).generator();
        AeroliteChunkGenerator listed = presetGenerator.withAeroliteSettings(TestSettings.SETTINGS);
        AeroliteSettings netherOnly = TestSettings.SETTINGS.withoutDimension(Level.OVERWORLD).withoutDimension(Level.END);
        helper.assertTrue(netherOnly.dimensions().keySet().equals(Set.of(Level.NETHER)), "The settings hold a dimension other than the Nether");
        AeroliteChunkGenerator generator = listed.withAeroliteSettings(netherOnly);
        ShaftLayout listedLayout = listed.layout(level);
        ShaftLayout.Shaft shaft = listedLayout.shaft(ShaftLayout.Axis.X, 0).orElseThrow();
        ChunkPos pos = new ChunkPos(0, SectionPos.blockToSectionCoord(shaft.position()));
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        generator.buildShafts(level, chunk);
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            helper.assertTrue(chunk.getBlockState(new BlockPos(pos.getMinBlockX(), y, shaft.position())).isAir(), "A dimension without variations got a shaft block");
        }
        helper.assertFalse(generator.layout(level).hasShafts(), "A dimension without variations has shafts");
        helper.succeed();
    }

    private static int clampInterior(int offset) {

        return Math.max(-Ring.OUTER + 1, Math.min(Ring.OUTER - 1, offset));
    }

}
