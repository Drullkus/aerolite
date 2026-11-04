package us.drullk.aerolite.world.shaft.pattern;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.shaft.MixedRandomFactory;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandChecks;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.BandPos;
import us.drullk.aerolite.world.shaft.surface.path.GrooveEdge;
import us.drullk.aerolite.world.shaft.surface.path.GrooveGraph;
import us.drullk.aerolite.world.shaft.surface.path.Grooves;
import us.drullk.aerolite.world.shaft.surface.path.LongUnionFind;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks of the {@code --check} modes of the pattern prototypes, on the scenes of a real pattern.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class PatternChecks {

    static final int CHUNKS = 12;
    static final int TREE_END = CHUNKS % 2 == 1 ? CHUNKS : CHUNKS - 1;
    /** The box of the prototypes, at the axis position 89. */
    static final ShaftMaze.Box BOX = new ShaftMaze.Box(89, 1);
    /** The chunks that the purity check renders alone: tree and connector chunks, and the two chunks of the box. */
    static final int[] ALONE = {0, 1, 5, 6, 11};

    private PatternChecks() {
    }

    /** The modes of the prototypes. */
    enum Mode {
        PLAIN, SURFACE, CUT;

        SurfacePathSettings paths(double share) {
            return new SurfacePathSettings(true, (float) share, this != PLAIN, this == CUT, 0.75F);
        }
    }

    /** The context of one pillar, with the box of the prototypes or none. */
    static PatternContext context(PatternSettings settings, long seed, boolean box) {
        ShaftMaze.BoxLookup lookup = box
            ? (min, max) -> BOX.center() >= min && BOX.center() <= max ? List.of(BOX) : List.of()
            : (min, max) -> List.of();
        return new PatternContext(settings, PillarRing.INSTANCE, lookup, new MixedRandomFactory(seed).fork("pattern"), 0.5F,
            Integer.MIN_VALUE / ShaftMaze.CHUNK, Integer.MAX_VALUE / ShaftMaze.CHUNK - 1);
    }

    /**
     * Asserts the rules of the prototype checks on the chunks 0 to 11.
     *
     * @param oneTree   true asks for one vertex tree at a loop share of 0 without a box
     * @param connected true asks that no groove component lies enclosed in the band without a box
     * @param orphans   true allows the orphan wall blocks of cut turns in small tiles
     */
    static void check(BandPattern pattern, String name, boolean box, boolean oneTree, boolean connected, boolean orphans, boolean overTails) {
        PatternScene scene = pattern.scene(0, 0, CHUNKS - 1);
        Band band = scene.band();
        assertEquals(List.of(), BandChecks.twoWide(band), name + ": 2-wide path");
        LongSet basePluses = new LongOpenHashSet(BandChecks.crossings(scene.base()));
        LongList pluses = new LongArrayList();
        for (long pos : BandChecks.crossings(band)) {
            if (!basePluses.contains(pos) && !(overTails && scene.tails().contains(pos))) {
                pluses.add(pos);
            }
        }
        assertEquals(List.of(), pluses, name + ": new plus crossing");
        assertEquals(List.of(), BandChecks.cornerRuns(band), name + ": corner run");
        assertEquals(0, scene.result().unbreakable(), name + ": unbreakable cycle edge");
        if (pattern.cuts()) {
            for (long orphan : BandChecks.orphans(band)) {
                assertTrue(orphans && besideCutTileCenter(band, orphan), name + ": orphan wall block at " + BandPos.toString(orphan));
            }
        }
        if (!(pattern.cuts() && overTails)) {
            scene.tails().forEach(tail -> assertNotEquals(BandKind.GROOVE, band.get(tail), name + ": tail carved at " + BandPos.toString(tail)));
        }
        for (int chunk : ALONE) {
            Band alone = pattern.scene(0, chunk, chunk).band();
            assertTrue(alone.sameKinds(band.crop(chunk * BandPattern.CHUNK, BandPattern.CHUNK)), name + ": chunk " + chunk + " differs alone");
        }
        if (pattern.share() == 0) {
            int[] forest = vertexForest(scene.result(), 0, TREE_END);
            assertEquals(0, forest[0], name + ": kept vertex graph has a cycle");
            assertEquals(forest[1], forest[2], name + ": kept vertex graph joins other vertices than the full graph");
            assertEquals(0, Grooves.cycleRank(band.crop(0, TREE_END * BandPattern.CHUNK)), name + ": groove blocks have a cycle");
            if (oneTree && !box) {
                assertEquals(1, forest[2], name + ": kept vertex graph is not one tree");
            }
        }
        if (connected && !box) {
            assertEquals(0, enclosedComponents(band), name + ": enclosed groove component");
        }
    }

    /** True when a diagonal neighbor of the wall block is a face node, the center of a small tile. The rationing can remove that center. */
    private static boolean besideCutTileCenter(Band band, long pos) {
        for (int dr = -1; dr <= 1; dr += 2) {
            for (int da = -1; da <= 1; da += 2) {
                long diagonal = band.pos(BandPos.ring(pos) + dr, BandPos.axis(pos) + da);
                if (band.inside(diagonal) && band.faceNodes().contains(diagonal)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The cycles of the kept edges inside the chunks {@code [lo, hi)}, and the component counts of the vertices there.
     * The counts join vertices through the whole graph, before and after the rationing.
     *
     * @return the cycles, the full count and the kept count
     */
    static int[] vertexForest(LoopRationing.Result result, int lo, int hi) {
        GrooveGraph graph = result.graph();
        LongSet cut = new LongOpenHashSet(result.removed());
        LongList inside = new LongArrayList();
        for (long vertex : graph.vertices()) {
            if (inChunks(vertex, lo, hi)) {
                inside.add(vertex);
            }
        }
        LongUnionFind full = new LongUnionFind();
        LongUnionFind kept = new LongUnionFind();
        LongUnionFind local = new LongUnionFind();
        int cycles = 0;
        for (GrooveEdge edge : graph.edges()) {
            full.union(edge.u(), edge.v());
            if (edge.chain().longStream().noneMatch(cut::contains)) {
                kept.union(edge.u(), edge.v());
                if (inChunks(edge.u(), lo, hi) && inChunks(edge.v(), lo, hi) && !local.union(edge.u(), edge.v())) {
                    cycles++;
                }
            }
        }
        return new int[] {cycles, full.count(inside), kept.count(inside)};
    }

    private static boolean inChunks(long pos, int lo, int hi) {
        int chunk = LoopRationing.segmentOf(pos);
        return chunk >= lo && chunk < hi;
    }

    /** The groove components that touch neither the first nor the last row of the band. */
    static int enclosedComponents(Band band) {
        LongSet blocks = Grooves.blocks(band);
        Long2ObjectOpenHashMap<LongList> links = Grooves.links(band, blocks);
        LongSet seen = new LongOpenHashSet();
        int enclosed = 0;
        for (long start : blocks) {
            if (!seen.add(start)) {
                continue;
            }
            boolean touches = false;
            LongArrayList stack = LongArrayList.of(start);
            while (!stack.isEmpty()) {
                long pos = stack.popLong();
                int axis = BandPos.axis(pos);
                touches |= axis == band.axis0() || axis == band.axisEnd() - 1;
                for (long next : links.get(pos)) {
                    if (seen.add(next)) {
                        stack.add(next);
                    }
                }
            }
            enclosed += touches ? 0 : 1;
        }
        return enclosed;
    }

    /** The count of blocks of the kind in the band. */
    static int count(Band band, BandKind kind) {
        return band.blocksOf(kind).size();
    }

}
