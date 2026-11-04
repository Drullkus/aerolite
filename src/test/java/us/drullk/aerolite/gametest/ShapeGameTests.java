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
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.shaft.ShaftFrame;
import us.drullk.aerolite.world.shaft.ShaftFrames;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ColumnJunction;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;
import us.drullk.aerolite.world.shaft.shape.WebPlane;
import us.drullk.aerolite.world.shaft.surface.RingKind;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GameTests of the I-beam and Bridge shapes: the cross sections, the trusses, the hole through a box, the column web plane,
 * erosion and the one crossing of a horizontal Bridge.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@GameTestHolder(Aerolite.MODID)
@PrefixGameTestTemplate(false)
public final class ShapeGameTests {

    // GameTest methods are static. Beanification fills these fields.
    @Autowired
    private static ShaftMazes mazes;
    @Autowired
    private static ShaftPainter painter;
    @Autowired
    private static ShaftFrames frames;

    private static final BlockState INTERIOR = Blocks.STONE.defaultBlockState();
    private static final BlockState JUNCTION = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState PATH = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState SURFACE = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState TRUSS = Blocks.IRON_BLOCK.defaultBlockState();

    private ShapeGameTests() {
    }

    private static ShaftVariation variation(PillarShape shape) {
        return TestSettings.STONE.withShape(shape).withJunction(JUNCTION).withSurface(SURFACE).withTrusses(TRUSS);
    }

    /** One variation of the shape, without decay and erosion. */
    private static DimensionSettings settings(PillarShape shape, float columnChance) {
        DimensionSettings settings = new DimensionSettings(TestSettings.PLACEMENT.withColumnChance(columnChance), List.of(TestSettings.one(variation(shape))));
        return TestSettings.withDecay(settings, TestSettings.DECAY_SCALE, 1, 1);
    }

    private static ShaftLayout layout(GameTestHelper helper, DimensionSettings settings, long seed) {
        ServerLevel level = helper.getLevel();
        return new ShaftLayout(mazes, seed, Level.OVERWORLD, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
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

    /** The first chunks along the Z shaft of cell 0 that hold no junction box and no column, nor a neighbor chunk that does. */
    private static int freeChunk(ShaftLayout layout, ShaftLayout.Shaft shaft, int from) {
        int chunkZ = from;
        while (!layout.columnsOn(shaft, 16 * chunkZ - 32, 16 * chunkZ + 48).isEmpty()) {
            chunkZ++;
        }
        return chunkZ;
    }

    private static boolean ringBlock(RingShape ring, int first, int second) {
        for (int r = 0; r < ring.rings(); r++) {
            if (ring.index(r, first, second) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static int ringOf(RingShape ring, int first, int second) {
        for (int r = 0; r < ring.rings(); r++) {
            if (ring.index(r, first, second) >= 0) {
                return r;
            }
        }
        return -1;
    }

    /**
     * Paints two chunks of a Z shaft and checks each block of its cross section: no block outside the section,
     * the interior block or a groove floor inside, and on the ring a maze wall, a flush maze path, a truss anchor, or a groove over its floors.
     * Returns the number of grooves over a concave corner and the number of flush maze paths.
     */
    private static int[] checkSection(GameTestHelper helper, PillarShape shape) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = layout(helper, settings(shape, TestSettings.PLACEMENT.columnChance()), 42);
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
        RingShape ring = layout.ring(shaft);
        ShaftFrame frame = frames.of(shaft);
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        Set<SectionOffset> solid = new HashSet<>();
        ring.section().forEach(block -> solid.add(new SectionOffset(block.first(), block.second())));
        int chunkZ = freeChunk(layout, shaft, 0);
        int concave = 0;
        int flush = 0;
        int grooves = 0;
        for (int along = 16 * chunkZ; along < 16 * chunkZ + 32; along++) {
            List<SectionOffset> trusses = ring.trusses(along);
            for (int first = -Ring.OUTER - 1; first <= Ring.OUTER + 1; first++) {
                for (int second = -Ring.OUTER - 1; second <= Ring.OUTER + 1; second++) {
                    BlockPos pos = frame.at(along, first, second);
                    BlockState state = paintedAt(chunks, layout, level, pos);
                    String at = " at along " + along + ", offsets " + first + ", " + second;
                    SectionOffset offset = new SectionOffset(first, second);
                    if (trusses.contains(offset)) {
                        helper.assertTrue(state == TRUSS, "A truss block is not the trusses block" + at);
                    } else if (!solid.contains(offset)) {
                        helper.assertTrue(state.isAir(), "A block outside the section is not air" + at);
                    } else if (!ringBlock(ring, first, second)) {
                        helper.assertTrue(state == INTERIOR || state == PATH, "A block inside the section is not the interior block" + at);
                    } else {
                        int r = ringOf(ring, first, second);
                        int index = ring.index(r, first, second);
                        if (ring.flush(r, index)) {
                            helper.assertTrue(state == WALL || state == PATH, "A flush face block is not wall or flush path" + at);
                            if (state == PATH) {
                                flush++;
                            }
                        } else if (state.isAir()) {
                            grooves++;
                            for (SectionOffset floor : ring.floors(r, index)) {
                                helper.assertTrue(paintedAt(chunks, layout, level, frame.at(along, floor.first(), floor.second())) == PATH,
                                        "A groove floor is not the maze path block" + at);
                            }
                            if (ring.kind(r, index) == RingKind.CONCAVE_EDGE) {
                                concave++;
                            }
                        } else {
                            helper.assertTrue(state == WALL, "A ring block is not the maze wall block" + at);
                        }
                    }
                }
            }
        }
        helper.assertTrue(grooves > 0, "The shaft has no groove");
        return new int[]{concave, flush};
    }

    @GameTest(template = "empty")
    public static void iBeamPaintsTheHSection(GameTestHelper helper) {
        int[] counts = checkSection(helper, PillarShape.I_BEAM);
        helper.assertTrue(counts[0] > 0, "No groove crosses a concave corner");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bridgePaintsItsPlates(GameTestHelper helper) {
        int[] counts = checkSection(helper, PillarShape.BRIDGE);
        helper.assertTrue(counts[1] > 0, "No inner face holds a flush maze path");
        helper.succeed();
    }

    /** Each truss block of the rule is the trusses block. An anchor replaces the plate block, also on the flush face. */
    @GameTest(template = "empty")
    public static void trussesFollowTheAnchorRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = layout(helper, settings(PillarShape.BRIDGE, TestSettings.PLACEMENT.columnChance()), 43);
        ShaftLayout.Shaft shaft = layout.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
        ShaftFrame frame = frames.of(shaft);
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        int chunkZ = freeChunk(layout, shaft, 0);
        int anchors = 0;
        for (int along = 16 * chunkZ; along < 16 * chunkZ + 16; along++) {
            boolean anchorSlot = Math.floorMod(along, 8) == 3 || Math.floorMod(along, 8) == 4;
            for (int v : new int[]{4, -4}) {
                for (int u : new int[]{4, 5, -4, -5}) {
                    BlockState state = paintedAt(chunks, layout, level, frame.at(along, v, u));
                    if (anchorSlot) {
                        helper.assertTrue(state == TRUSS, "An anchor is not the trusses block at along " + along + ", u " + u + ", v " + v);
                        anchors++;
                    } else {
                        helper.assertTrue(state != TRUSS, "A plate block off the anchors is the trusses block at along " + along);
                    }
                }
            }
            // The truss space at the plate sides stays empty.
            for (int v = -3; v <= 3; v++) {
                helper.assertTrue(paintedAt(chunks, layout, level, frame.at(along, v, Ring.OUTER)).isAir(), "The truss space at the plate side holds a block");
                helper.assertTrue(paintedAt(chunks, layout, level, frame.at(along, v, -Ring.OUTER)).isAir(), "The truss space at the plate side holds a block");
            }
        }
        helper.assertTrue(anchors == 2 * 2 * 4 * 2, "The chunk holds " + anchors + " anchors");
        helper.succeed();
    }

    /** The horizontal Bridge passes each box in a hole. The plate layers and the trusses stay, and the passage is 7 by 7. */
    @GameTest(template = "empty")
    public static void bridgeBoxHasTheHole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = layout(helper, settings(PillarShape.BRIDGE, 1), 7);
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        int boxes = 0;
        for (int cell = 0; cell < 3; cell++) {
            for (int other = 0; other < 3; other++) {
                ShaftLayout.Column column = layout.column(cell, other).orElse(null);
                if (column == null) {
                    continue;
                }
                for (ColumnJunction junction : column.junctions()) {
                    boxes++;
                    ShaftLayout.Shaft shaft = junction.alongX() ? column.xShaft() : column.zShaft();
                    RingShape ring = layout.ring(shaft);
                    ShaftFrame frame = frames.of(shaft);
                    int center = junction.alongX() ? column.x() : column.z();
                    int passage = 0;
                    for (int along = center - Ring.BOX; along <= center + Ring.BOX; along++) {
                        List<SectionOffset> trusses = ring.trusses(along);
                        for (int dy = -Ring.BOX; dy <= Ring.BOX; dy++) {
                            for (int cross = -Ring.BOX + 2; cross <= Ring.BOX - 2; cross++) {
                                BlockState state = paintedAt(chunks, layout, level, frame.at(along, dy, cross));
                                String at = " at along " + (along - center) + ", y " + dy + ", cross " + cross;
                                int ay = Math.abs(dy);
                                int ac = Math.abs(cross);
                                if (ay <= 3 && ac <= 3) {
                                    helper.assertTrue(state.isAir(), "The passage holds a block" + at);
                                    passage++;
                                } else if (ay <= 3) {
                                    helper.assertTrue(state == (trusses.contains(new SectionOffset(dy, cross)) ? TRUSS : Blocks.AIR.defaultBlockState()),
                                            "A truss layer in the hole does not follow the truss rule" + at);
                                } else if (ay == 4) {
                                    helper.assertTrue(state == WALL || state == PATH || state == TRUSS, "The inner plate layer in the hole is gone" + at);
                                } else if (ay == 5) {
                                    helper.assertTrue(state == INTERIOR, "The middle plate layer in the hole is gone" + at);
                                } else {
                                    helper.assertTrue(state == INTERIOR, "A box block outside the hole is not the box block" + at);
                                }
                            }
                        }
                    }
                    helper.assertTrue(passage == (2 * Ring.BOX + 1) * 7 * 7, "The passage is not 7 by 7 through the box");
                }
            }
        }
        helper.assertTrue(boxes > 0, "The layout has no box");
        helper.succeed();
    }

    /** A column I-beam puts its web in its seeded plane. Both planes occur. */
    @GameTest(template = "empty")
    public static void columnWebFollowsItsPlane(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = layout(helper, settings(PillarShape.I_BEAM, 1), 7);
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        Set<WebPlane> planes = EnumSet.noneOf(WebPlane.class);
        for (int cell = 0; cell < 6 && planes.size() < 2; cell++) {
            for (int other = 0; other < 6 && planes.size() < 2; other++) {
                ShaftLayout.Column column = layout.column(cell, other).orElse(null);
                if (column == null || planes.contains(column.webPlane())) {
                    continue;
                }
                planes.add(column.webPlane());
                int y = (column.junctions().getFirst().center() + column.junctions().getLast().center()) / 2;
                boolean planeX = column.webPlane() == WebPlane.X;
                // Plane X puts the flanges at X offsets 3 to 6, so the Z offset 5 off the web is air.
                BlockState offZ = paintedAt(chunks, layout, level, new BlockPos(column.x(), y, column.z() + 5));
                BlockState offX = paintedAt(chunks, layout, level, new BlockPos(column.x() + 5, y, column.z()));
                helper.assertTrue(planeX == offZ.isAir(), "The web plane " + column.webPlane() + " does not match the block at Z offset 5");
                helper.assertTrue(planeX != offX.isAir(), "The web plane " + column.webPlane() + " does not match the block at X offset 5");
                helper.assertTrue(paintedAt(chunks, layout, level, new BlockPos(column.x(), y, column.z())) == INTERIOR, "The web core is not the interior block");
            }
        }
        helper.assertTrue(planes.size() == 2, "The layout holds only the web planes " + planes);
        helper.succeed();
    }

    /** Erosion takes blocks, but never a middle layer of a plate, a flange or the web. */
    @GameTest(template = "empty")
    public static void erosionKeepsTheMiddleLayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (PillarShape shape : List.of(PillarShape.I_BEAM, PillarShape.BRIDGE)) {
            DimensionSettings base = new DimensionSettings(TestSettings.PLACEMENT, List.of(TestSettings.one(variation(shape))));
            ShaftLayout eroded = layout(helper, TestSettings.withDecay(base, 32, 0, ShaftVariation.THRESHOLD_STEP), 42);
            ShaftLayout intact = layout(helper, TestSettings.withDecay(base, 32, 0, 1), 42);
            ShaftLayout.Shaft shaft = eroded.shaft(ShaftLayout.Axis.Z, 0).orElseThrow();
            ShaftFrame frame = frames.of(shaft);
            Map<Long, ProtoChunk> erodedChunks = new HashMap<>();
            Map<Long, ProtoChunk> intactChunks = new HashMap<>();
            int chunkZ = freeChunk(eroded, shaft, 0);
            int removed = 0;
            for (int along = 16 * chunkZ; along < 16 * chunkZ + 32; along++) {
                for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
                    for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                        BlockPos pos = frame.at(along, first, second);
                        BlockState after = paintedAt(erodedChunks, eroded, level, pos);
                        BlockState before = paintedAt(intactChunks, intact, level, pos);
                        if (after == before) {
                            continue;
                        }
                        removed++;
                        int v = Math.abs(first);
                        int u = Math.abs(second);
                        boolean middle = shape == PillarShape.BRIDGE ? v == 5 : v == 4 || v == 5 || v <= 2 && u == 0;
                        helper.assertTrue(!middle, "Erosion removed a middle layer block of the " + shape.getSerializedName() + " at offsets " + first + ", " + second);
                    }
                }
            }
            helper.assertTrue(removed > 0, "Erosion removed no block of the " + shape.getSerializedName());
        }
        helper.succeed();
    }

    /**
     * The patch of a horizontal Bridge joins one plate. Its notches through the side bezel sit on the row of one plate side corner node,
     * at Y offset 4 or -4, through the face offsets 7, 6 and 5, over the maze path block.
     */
    @GameTest(template = "empty")
    public static void bridgeCrossingNotchesBesideTheHole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ShaftLayout layout = layout(helper, settings(PillarShape.BRIDGE, 1), 11);
        Map<Long, ProtoChunk> chunks = new HashMap<>();
        int notches = 0;
        for (int cell = 0; cell < 4; cell++) {
            for (int other = 0; other < 4; other++) {
                ShaftLayout.Column column = layout.column(cell, other).orElse(null);
                if (column == null) {
                    continue;
                }
                for (ColumnJunction junction : column.junctions()) {
                    ShaftFrame frame = junction.alongX()
                            ? new ShaftFrame(ShaftFrame.Kind.ALONG_X, junction.center(), column.z())
                            : new ShaftFrame(ShaftFrame.Kind.ALONG_Z, junction.center(), column.x());
                    int center = junction.alongX() ? column.x() : column.z();
                    // The shaft patch is on the face opposite the column patch.
                    int sign = -junction.sign();
                    Set<Integer> rows = new HashSet<>();
                    for (int side : new int[]{-1, 1}) {
                        for (int f = -Ring.PATCH_EDGE; f <= Ring.PATCH_EDGE; f++) {
                            int air = 0;
                            for (int step = 0; step < Ring.BEZEL; step++) {
                                int a = side * (Ring.BOX - step);
                                BlockState state = paintedAt(chunks, layout, level, frame.at(center + a, f, Ring.BOX * sign));
                                if (state.isAir()) {
                                    air++;
                                    rows.add(f);
                                    helper.assertTrue(paintedAt(chunks, layout, level, frame.at(center + a, f, (Ring.BOX - 1) * sign)) == PATH,
                                            "A notch block has no maze path block one in");
                                } else {
                                    helper.assertTrue(state == JUNCTION, "A side bezel block is not the junction block");
                                }
                            }
                            helper.assertTrue(air == 0 || air == Ring.BEZEL, "A notch does not run through the whole bezel");
                            if (air == Ring.BEZEL) {
                                notches++;
                            }
                        }
                    }
                    helper.assertTrue(rows.size() == 1, "The patch of a horizontal Bridge has notches on rows " + rows);
                    int row = rows.iterator().next();
                    helper.assertTrue(Math.abs(row) == Ring.PATCH_EDGE, "The Bridge notch is not on a plate side corner row: " + row);
                }
            }
        }
        helper.assertTrue(notches > 0, "No Bridge crossing notches through the bezel");
        helper.succeed();
    }

}
