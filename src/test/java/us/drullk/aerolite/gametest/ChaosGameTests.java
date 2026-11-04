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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import tamaized.beanification.Autowired;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.shaft.ShaftFrame;
import us.drullk.aerolite.world.shaft.ShaftFrames;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.binding.PatternBindings;
import us.drullk.aerolite.world.shaft.binding.chaos.ChaosPatternBinding;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;
import us.drullk.aerolite.world.shaft.pattern.chaos.ChaosPattern;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.List;

/**
 * GameTests of the chaos pattern in the bean context: the binding and the blocks that the painter writes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class ChaosGameTests {

    // GameTest methods are static. Beanification fills these fields.
    @Autowired
    private static PatternBindings bindings;
    @Autowired
    private static ShaftMazes mazes;
    @Autowired
    private static ShaftPainter painter;
    @Autowired
    private static ShaftFrames frames;

    private ChaosGameTests() {
    }

    @GameTest(template = "empty")
    public static void collectorFindsTheChaosBinding(GameTestHelper helper) {
        helper.assertTrue(bindings.binding(ChaosSettings.class) instanceof ChaosPatternBinding, "The chaos settings do not pick the chaos binding");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void painterCarvesTheChaosGrooves(GameTestHelper helper) {
        ChaosSettings settings = ChaosSettings.DEFAULT.withSpacing(8);
        Counts counts = paintAndCheck(helper, settings);
        helper.assertTrue(counts.grooves() > 0, "The chaos pillar has no groove");
        helper.assertTrue(counts.walls() > 0, "The chaos pillar has no wall");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void painterWritesTheChaosSurfacePaths(GameTestHelper helper) {
        ChaosSettings settings = ChaosSettings.DEFAULT.withSpacing(8);
        Counts counts = paintAndCheck(helper, settings.withPaths(settings.paths().withSurfacePaths(true).withPathChance(1)));
        helper.assertTrue(counts.surfaces() > 0, "The chaos pillar has no surface path block");
        helper.succeed();
    }

    private record Counts(int grooves, int walls, int surfaces) {
    }

    /**
     * Paints one chunk of a chaos shaft without decay and erosion, away from the junctions.
     * Each groove cell of the band is air over the maze path block. Each wall cell is the maze wall block. Each surface cell is the surface path block.
     */
    private static Counts paintAndCheck(GameTestHelper helper, ChaosSettings chaos) {
        ServerLevel level = helper.getLevel();
        ShaftVariation variation = TestSettings.STONE.withPattern(chaos).withSurface(Blocks.GOLD_BLOCK.defaultBlockState());
        DimensionSettings settings = TestSettings.withDecay(new DimensionSettings(TestSettings.PLACEMENT, List.of(TestSettings.one(variation))),
                TestSettings.DECAY_SCALE, 1, 1);
        ShaftLayout layout = new ShaftLayout(mazes, 42, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
        ShaftVariation painted = layout.variation(shaft);
        int chunkZ = 0;
        while (!layout.columnsOn(shaft, 16 * chunkZ - 16, 16 * chunkZ + 32).isEmpty()) {
            chunkZ++;
        }
        ChunkPos pos = new ChunkPos(SectionPos.blockToSectionCoord(shaft.position()), chunkZ);
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        painter.paint(layout, chunk);

        SurfacePattern pattern = layout.shaftMaze(shaft);
        helper.assertTrue(pattern instanceof ChaosPattern, "The chaos variation did not make a chaos pattern");
        SegmentSurface segment = pattern.segmentAt(chunkZ);
        RingShape ring = pattern.ring();
        Band band = segment.band(0);
        ShaftFrame frame = frames.of(shaft);
        BlockState wall = TestSettings.state(painted.wall());
        BlockState path = TestSettings.state(painted.path());
        BlockState surface = TestSettings.state(painted.surface());
        int grooves = 0;
        int walls = 0;
        int surfaces = 0;
        for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
            for (int index = 0; index < ring.length(0); index++) {
                BlockPos at = frame.at(z, ring.first(0, index), ring.second(0, index));
                if (SectionPos.blockToSectionCoord(at.getX()) != pos.x) {
                    continue;
                }
                BlockState state = chunk.getBlockState(at);
                BandKind kind = band.get(index, z);
                if (kind == BandKind.GROOVE) {
                    grooves++;
                    helper.assertTrue(state.isAir(), "A groove cell is not air at " + at);
                    for (SectionOffset floor : ring.floors(0, index)) {
                        BlockPos beneath = frame.at(z, floor.first(), floor.second());
                        if (SectionPos.blockToSectionCoord(beneath.getX()) == pos.x) {
                            helper.assertTrue(chunk.getBlockState(beneath) == path, "A groove cell has no maze path block under it at " + beneath);
                        }
                    }
                } else if (kind == BandKind.SURFACE) {
                    surfaces++;
                    helper.assertTrue(state == surface, "A surface cell is not the surface path block at " + at);
                } else {
                    walls++;
                    helper.assertTrue(state == wall, "A wall cell is not the maze wall block at " + at);
                }
            }
        }
        return new Counts(grooves, walls, surfaces);
    }

}
