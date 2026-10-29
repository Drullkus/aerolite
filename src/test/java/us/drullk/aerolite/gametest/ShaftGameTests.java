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
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.status.AeroliteChunkStatuses;

import java.util.List;

/**
 * GameTests of the shafts chunk step and the blocks it writes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class ShaftGameTests {

    private ShaftGameTests() {
    }

    @GameTest(template = "empty")
    public static void shaftsStepSitsBetweenCarversAndFeatures(GameTestHelper helper) {
        ChunkStatus shafts = AeroliteChunkStatuses.shafts();
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
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withDecayThreshold(1);
        ShaftLayout layout = new ShaftLayout(42, "test", level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
        int chunkZ = 0;
        while (!layout.columnsOn(shaft, 16 * chunkZ - 16, 16 * chunkZ + 32).isEmpty()) {
            chunkZ++;
        }
        ChunkPos pos = new ChunkPos(SectionPos.blockToSectionCoord(shaft.position()), chunkZ);
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        ShaftPainter.paint(layout, chunk);

        int x = shaft.position();
        int y = shaft.centerY();
        int paths = 0;
        for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
            helper.assertTrue(chunk.getBlockState(new BlockPos(x, y, z)) == settings.interiorBlock(), "The shaft core is not the interior block");
            for (int block = 0; block < Ring.BLOCKS; block++) {
                int dy = Ring.first(block);
                int dx = Ring.second(block);
                if (SectionPos.blockToSectionCoord(x + dx) != pos.x) {
                    continue;
                }
                BlockState outer = chunk.getBlockState(new BlockPos(x + dx, y + dy, z));
                if (outer.isAir()) {
                    paths++;
                    BlockPos beneath = new BlockPos(x + clampInterior(dx), y + clampInterior(dy), z);
                    helper.assertTrue(chunk.getBlockState(beneath) == settings.pathBlock(), "A maze path has no maze path block under it");
                } else {
                    helper.assertTrue(outer == settings.wallBlock(), "An outer layer block is not the maze wall block");
                }
            }
            helper.assertTrue(chunk.getBlockState(new BlockPos(x, y + Ring.OUTER + 1, z)).isAir(), "The block above the shaft is not air");
        }
        helper.assertTrue(paths > 0, "The shaft has no maze path");
        helper.assertTrue(chunk.getBlockState(new BlockPos(x, level.getMinBuildHeight(), pos.getMinBlockZ())).is(Blocks.AIR), "The void holds a block");
        helper.succeed();
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
        helper.assertTrue(chunk.getBlockState(new BlockPos(pos.getMinBlockX(), shaft.centerY(), shaft.position())).is(generator.aeroliteSettings().interiorBlock().getBlock()),
                "The generator wrote no shaft core");
        helper.succeed();
    }

    private static int clampInterior(int offset) {
        return Math.max(-Ring.OUTER + 1, Math.min(Ring.OUTER - 1, offset));
    }

}
