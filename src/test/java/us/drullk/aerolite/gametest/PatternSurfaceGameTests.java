package us.drullk.aerolite.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tamaized.beanification.Autowired;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;
import us.drullk.aerolite.world.shaft.ShaftFrame;
import us.drullk.aerolite.world.shaft.ShaftFrames;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;
import us.drullk.aerolite.world.shaft.pattern.bricks.BricksPattern;
import us.drullk.aerolite.world.shaft.pattern.scales.ScalesPattern;
import us.drullk.aerolite.world.shaft.pattern.tiles.TilesPattern;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.List;

/**
 * GameTests of the bricks, tiles and scales patterns: the bindings, and the blocks that the paint writes for the band of a segment.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class PatternSurfaceGameTests {

    // GameTest methods are static. Beanification fills these fields.
    @Autowired
    private static ShaftMazes mazes;
    @Autowired
    private static ShaftPainter painter;
    @Autowired
    private static ShaftFrames frames;

    private PatternSurfaceGameTests() {
    }

    /** No decay and no erosion, so each band cell gives one block kind. */
    private static DimensionSettings settings(PatternSettings pattern) {
        ShaftVariation variation = TestSettings.STONE.withSurface(Blocks.GOLD_BLOCK.defaultBlockState()).withPattern(pattern);
        return TestSettings.withDecay(new DimensionSettings(TestSettings.PLACEMENT, List.of(TestSettings.one(variation))), TestSettings.DECAY_SCALE, 1, 1);
    }

    private static SurfacePathSettings paths(boolean cut) {
        return new SurfacePathSettings(true, 0.5F, true, cut, 0.75F);
    }

    @GameTest(template = "empty")
    public static void bindingsMakeTheBandPatterns(GameTestHelper helper) {
        ShaftVariation stone = TestSettings.STONE;
        helper.assertTrue(pattern(stone.withPattern(BricksSettings.DEFAULT)) instanceof BricksPattern, "The bricks settings do not make a bricks pattern");
        helper.assertTrue(pattern(stone.withPattern(TilesSettings.DEFAULT)) instanceof TilesPattern, "The tiles settings do not make a tiles pattern");
        helper.assertTrue(pattern(stone.withPattern(ScalesSettings.DEFAULT)) instanceof ScalesPattern, "The scales settings do not make a scales pattern");
        helper.succeed();
    }

    private static SurfacePattern pattern(ShaftVariation variation) {
        return mazes.pattern(variation, (min, max) -> List.of(), new XoroshiroRandomSource(1).forkPositional(), -4, 4);
    }

    @GameTest(template = "empty")
    public static void bricksPaintTheirSegmentBand(GameTestHelper helper) {
        paintMatchesSegment(helper, new BricksSettings(BricksSettings.Size.SMALL, paths(false)), true);
    }

    @GameTest(template = "empty")
    public static void tilesPaintTheirSegmentBand(GameTestHelper helper) {
        paintMatchesSegment(helper, new TilesSettings(TilesSettings.Size.SMALL, TilesSettings.Stagger.Y, false, paths(true)), false);
    }

    @GameTest(template = "empty")
    public static void scalesPaintTheirSegmentBand(GameTestHelper helper) {
        paintMatchesSegment(helper, new ScalesSettings(paths(true), true), false);
    }

    /**
     * Paints one chunk of a Z shaft without a column nearby and compares each outer block with the segment band.
     * A groove is air over the maze path block. A surface path is the surface path block. A wall is the maze wall block.
     */
    private static void paintMatchesSegment(GameTestHelper helper, PatternSettings pattern, boolean surfacePaths) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = new ShaftLayout(mazes, 42, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings(pattern));
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
        ShaftVariation variation = layout.variation(shaft);
        int chunkZ = 0;
        while (!layout.columnsOn(shaft, 16 * chunkZ - 16, 16 * chunkZ + 32).isEmpty()) {
            chunkZ++;
        }
        ChunkPos pos = new ChunkPos(SectionPos.blockToSectionCoord(shaft.position()), chunkZ);
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        painter.paint(layout, chunk);

        SurfacePattern surface = layout.shaftMaze(shaft);
        SegmentSurface segment = surface.segmentAt(chunkZ);
        Band band = segment.band(0);
        RingShape ring = surface.ring();
        ShaftFrame frame = frames.of(shaft);
        BlockState wall = TestSettings.state(variation.wall());
        BlockState path = TestSettings.state(variation.path());
        BlockState surfaceBlock = TestSettings.state(variation.surface());
        int grooves = 0;
        int surfaces = 0;
        for (int along = pos.getMinBlockZ(); along <= pos.getMaxBlockZ(); along++) {
            for (int index = 0; index < ring.length(0); index++) {
                BlockPos at = frame.at(along, ring.first(0, index), ring.second(0, index));
                if (!new ChunkPos(at).equals(pos)) {
                    continue;
                }
                BlockState state = chunk.getBlockState(at);
                BandKind kind = band.get(index, along);
                String cell = " at ring " + index + ", axis " + along;
                switch (kind) {
                    case GROOVE -> {
                        grooves++;
                        helper.assertTrue(state.isAir(), "A groove cell is not air" + cell);
                        for (SectionOffset offset : ring.floors(0, index)) {
                            BlockPos floor = frame.at(along, offset.first(), offset.second());
                            if (new ChunkPos(floor).equals(pos)) {
                                helper.assertTrue(chunk.getBlockState(floor) == path, "A groove cell has no maze path block under it" + cell);
                            }
                        }
                    }
                    case SURFACE -> {
                        surfaces++;
                        helper.assertTrue(state == surfaceBlock, "A surface path cell is not the surface path block" + cell);
                    }
                    default -> helper.assertTrue(state == wall, "A wall cell is not the maze wall block" + cell);
                }
            }
        }
        helper.assertTrue(grooves > 0, "The segment band has no groove in the chunk");
        helper.assertTrue(!surfacePaths || surfaces > 0, "The segment band has no surface path in the chunk");
        helper.succeed();
    }

}
