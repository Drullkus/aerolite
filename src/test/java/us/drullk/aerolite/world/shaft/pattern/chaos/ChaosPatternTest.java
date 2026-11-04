package us.drullk.aerolite.world.shaft.pattern.chaos;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.binding.chaos.ChaosPatternBinding;
import us.drullk.aerolite.world.shaft.maze.MazeEdges;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.PatchSurface;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.binding.PatternBindings;
import us.drullk.aerolite.world.shaft.binding.maze.MazePatternBinding;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;
import us.drullk.aerolite.world.shaft.shape.ShapeRings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the chaos pattern through the surface model: the segments, the junction patches and the binding.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class ChaosPatternTest {

    private static final ChaosSettings SETTINGS = ChaosSettings.DEFAULT.withSpacing(8);

    private static ShaftMaze.BoxLookup lookup(List<ShaftMaze.Box> boxes) {
        return (min, max) -> boxes.stream().filter(box -> box.center() >= min && box.center() <= max).toList();
    }

    private static SurfacePattern pattern(ChaosSettings settings, List<ShaftMaze.Box> boxes, long seed, int minChunk, int maxChunk) {
        PatternContext context = new PatternContext(settings, PillarRing.INSTANCE, lookup(boxes), new XoroshiroRandomSource(seed).forkPositional(), 0.5F,
                minChunk, maxChunk);
        return new ChaosPatternBinding(new ChaosScenes()).create(context);
    }

    @Test
    void bindingServesTheChaosSettings() {
        ChaosPatternBinding binding = new ChaosPatternBinding(new ChaosScenes());
        assertSame(ChaosSettings.class, binding.settings());
        ShaftMazes mazes = new ShaftMazes(new MazeEdges(), PatternBindings.of(List.of(new MazePatternBinding(new MazeEdges()), binding)), new ShapeRings());
        SurfacePattern pattern = mazes.pattern(TestSettings.STONE.withPattern(SETTINGS), lookup(List.of()), new XoroshiroRandomSource(1).forkPositional(), -10, 10);
        assertInstanceOf(ChaosPattern.class, pattern);
        assertSame(PillarRing.INSTANCE, pattern.ring());
    }

    /** Each segment covers its chunk and the first block of the next chunk. Both segments agree on that block row. */
    @Test
    void neighborSegmentsAgreeOnTheSharedRow() {
        SurfacePattern pattern = pattern(SETTINGS, List.of(), 5, Integer.MIN_VALUE / ShaftMaze.CHUNK, Integer.MAX_VALUE / ShaftMaze.CHUNK - 1);
        int grooves = 0;
        for (int chunk = -3; chunk < 9; chunk++) {
            SegmentSurface surface = pattern.segmentAt(chunk);
            SegmentSurface next = pattern.segmentAt(chunk + 1);
            assertEquals(chunk, surface.firstChunk());
            assertEquals(chunk, surface.lastChunk());
            Band band = surface.band(0);
            assertEquals(ShaftMaze.CHUNK * chunk, band.axis0());
            assertEquals(ShaftMaze.CHUNK * (chunk + 1) + 1, band.axisEnd());
            int shared = ShaftMaze.CHUNK * (chunk + 1);
            for (int index = 0; index < band.ringLength(); index++) {
                assertEquals(next.band(0).get(index, shared), band.get(index, shared), "chunk " + chunk + " ring " + index);
            }
            grooves += band.blocksOf(BandKind.GROOVE).size();
        }
        assertTrue(grooves > 0, "the pattern carved no groove");
    }

    /** A new store and another query sequence give the same segments. */
    @Test
    void segmentsDoNotDependOnTheQuerySequence() {
        int min = Integer.MIN_VALUE / ShaftMaze.CHUNK;
        int max = Integer.MAX_VALUE / ShaftMaze.CHUNK - 1;
        ChaosSettings settings = SETTINGS.withPaths(SETTINGS.paths().withSurfacePaths(true).withLoopShare(0));
        SurfacePattern forward = pattern(settings, List.of(), 9, min, max);
        SurfacePattern backward = pattern(settings, List.of(), 9, min, max);
        Band[] first = new Band[10];
        for (int chunk = 0; chunk < 10; chunk++) {
            first[chunk] = forward.segmentAt(chunk).band(0);
        }
        for (int chunk = 9; chunk >= 0; chunk--) {
            assertTrue(first[chunk].sameKinds(backward.segmentAt(chunk).band(0)), "chunk " + chunk);
        }
    }

    /** The box rows are box cells. The patch takes the band in its 9x9 area. The bezel stays wall. */
    @Test
    void junctionPatchContinuesTheBand() {
        int patches = 0;
        for (long seed = 1; seed <= 4; seed++) {
            for (ShaftMaze.Box box : List.of(new ShaftMaze.Box(40, 1), new ShaftMaze.Box(40, -1),
                    new ShaftMaze.Box(40, 1, ShaftMaze.Patching.COLUMN_ALONG_X), new ShaftMaze.Box(40, -1, ShaftMaze.Patching.COLUMN_ALONG_Z))) {
                SurfacePattern pattern = pattern(SETTINGS, List.of(box), seed, -100, 100);
                for (int chunk = 1; chunk <= 3; chunk++) {
                    SegmentSurface surface = pattern.segmentAt(chunk);
                    if (!surface.boxes().contains(box)) {
                        continue;
                    }
                    Band band = surface.band(0);
                    for (int along = box.center() - Ring.BOX; along <= box.center() + Ring.BOX; along++) {
                        if (band.insideAxis(along)) {
                            for (int index = 0; index < band.ringLength(); index++) {
                                assertEquals(BandKind.BOX, band.get(index, along));
                            }
                        }
                    }
                    PatchSurface patch = surface.patch(box.center());
                    int paths = 0;
                    for (int a = -Ring.BOX; a <= Ring.BOX; a++) {
                        for (int f = -Ring.BOX; f <= Ring.BOX; f++) {
                            if (PatchSurface.isBezel(a, f)) {
                                assertEquals(BandKind.WALL, patch.get(a, f), "a bezel cell is not wall");
                            } else if (patch.get(a, f).isPath()) {
                                paths++;
                            }
                        }
                    }
                    if (paths > 0) {
                        patches++;
                    }
                }
            }
        }
        assertTrue(patches > 0, "no patch holds a groove");
    }

    /** Two segments that hold one box give it the same patch. */
    @Test
    void segmentsShareThePatchOfABox() {
        ShaftMaze.Box box = new ShaftMaze.Box(ShaftMaze.CHUNK * 3, 1);
        SurfacePattern pattern = pattern(SETTINGS, List.of(box), 3, -100, 100);
        PatchSurface low = pattern.segmentAt(2).patch(box.center());
        PatchSurface high = pattern.segmentAt(3).patch(box.center());
        for (int a = -Ring.BOX; a <= Ring.BOX; a++) {
            for (int f = -Ring.BOX; f <= Ring.BOX; f++) {
                assertEquals(low.get(a, f), high.get(a, f));
            }
        }
    }

    /** A column computes all its chunk sections at once, and each section ends at the column range. */
    @Test
    void boundedColumnPaintsEverySection() {
        List<ShaftMaze.Box> boxes = List.of(new ShaftMaze.Box(40, 1, ShaftMaze.Patching.COLUMN_ALONG_X), new ShaftMaze.Box(150, -1, ShaftMaze.Patching.COLUMN_ALONG_Z));
        SurfacePattern pattern = pattern(SETTINGS, boxes, 11, -4, 19);
        int grooves = 0;
        for (int chunk = pattern.minChunk(); chunk <= pattern.maxChunk(); chunk++) {
            SegmentSurface surface = pattern.segmentAt(chunk);
            grooves += surface.band(0).blocksOf(BandKind.GROOVE).size();
        }
        assertTrue(grooves > 0, "the column carved no groove");
        Band top = pattern.segmentAt(pattern.maxChunk()).band(0);
        int above = ShaftMaze.CHUNK * (pattern.maxChunk() + 1);
        for (int index = 0; index < top.ringLength(); index++) {
            assertEquals(BandKind.WALL, top.get(index, above), "the row above the column holds a groove");
        }
    }

}
