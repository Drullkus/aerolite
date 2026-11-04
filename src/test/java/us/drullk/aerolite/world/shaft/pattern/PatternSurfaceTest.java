package us.drullk.aerolite.world.shaft.pattern;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.pattern.bricks.BricksPattern;
import us.drullk.aerolite.world.shaft.pattern.scales.ScalesPattern;
import us.drullk.aerolite.world.shaft.pattern.tiles.TilesPattern;
import us.drullk.aerolite.world.shaft.surface.PatchSurface;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the segment surfaces of the band patterns: the segments, the box rows, the patch and the column range.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class PatternSurfaceTest {

    private static List<BandPattern> patterns(boolean box) {
        BricksSettings bricks = new BricksSettings(BricksSettings.Size.SMALL, PatternChecks.Mode.SURFACE.paths(0.5));
        TilesSettings tiles = new TilesSettings(TilesSettings.Size.LARGE, TilesSettings.Stagger.Y, false, PatternChecks.Mode.CUT.paths(0.5));
        ScalesSettings scales = new ScalesSettings(PatternChecks.Mode.CUT.paths(1), true);
        return List.of(
            new BricksPattern(PatternChecks.context(bricks, 4, box), bricks),
            new TilesPattern(PatternChecks.context(tiles, 4, box), tiles),
            new ScalesPattern(PatternChecks.context(scales, 4, box), scales));
    }

    /** Each segment band equals the scene of its chunks. The extra row after the segment stays wall. */
    @Test
    void segmentsCopyTheScene() {
        for (BandPattern pattern : patterns(false)) {
            Band scene = pattern.scene(0, 0, 7).band();
            for (int chunk = 0; chunk < 8; chunk++) {
                SegmentSurface surface = pattern.segmentAt(chunk);
                assertEquals(chunk, surface.firstChunk());
                assertEquals(chunk, surface.lastChunk());
                Band band = surface.band(0);
                assertTrue(band.crop(16 * chunk, 16).sameKinds(scene.crop(16 * chunk, 16)), pattern.getClass().getSimpleName() + " chunk " + chunk);
                for (int ring = 0; ring < band.ringLength(); ring++) {
                    assertEquals(BandKind.WALL, band.get(ring, 16 * chunk + 16));
                }
            }
        }
    }

    /** The box at 89 spans the chunks 5 and 6. Both give one segment that holds the box, and its patch continues the pattern. */
    @Test
    void boxSegmentHoldsThePatch() {
        for (BandPattern pattern : patterns(true)) {
            String name = pattern.getClass().getSimpleName();
            SegmentSurface surface = pattern.segmentAt(5);
            assertEquals(5, surface.firstChunk(), name);
            assertEquals(6, surface.lastChunk(), name);
            assertEquals(6, pattern.segmentAt(6).lastChunk(), name);
            assertEquals(List.of(PatternChecks.BOX), surface.boxes(), name);
            Band scene = pattern.scene(0, 5, 6).band();
            PatchSurface patch = surface.patch(PatternChecks.BOX.center());
            int paths = 0;
            for (int a = -Ring.BOX; a <= Ring.BOX; a++) {
                for (int f = -Ring.BOX; f <= Ring.BOX; f++) {
                    BandKind kind = patch.get(a, f);
                    if (PatchSurface.isBezel(a, f)) {
                        assertEquals(BandKind.WALL, kind, name + " bezel");
                        continue;
                    }
                    // The patch face of a box with sign 1 is face 0 of the pillar ring, where the first offset runs from -6 to 6.
                    int index = PillarRing.INSTANCE.index(0, f, Ring.OUTER);
                    BandKind expected = scene.get(index, PatternChecks.BOX.center() + a);
                    assertEquals(expected == BandKind.BOX ? BandKind.WALL : expected, kind, name + " patch (" + a + ", " + f + ")");
                    paths += kind.isPath() ? 1 : 0;
                }
            }
            assertTrue(paths > 0, name + " has no groove on the patch");
            for (int along = PatternChecks.BOX.center() - Ring.BOX; along <= PatternChecks.BOX.center() + Ring.BOX; along++) {
                for (int ring = 0; ring < 48; ring++) {
                    assertEquals(BandKind.BOX, surface.band(0).get(ring, along), name + " box row");
                }
            }
        }
    }

    /** A column pattern spans the chunk sections of the build height. Each segment of it equals the scene of the whole column. */
    @Test
    void columnSegmentsFollowOneScene() {
        BricksSettings bricks = new BricksSettings(BricksSettings.Size.JUMBLED, PatternChecks.Mode.CUT.paths(0));
        ShaftMaze.Box box = new ShaftMaze.Box(40, -1, ShaftMaze.Patching.COLUMN_ALONG_X);
        PatternContext base = PatternChecks.context(bricks, 9, false);
        PatternContext context = new PatternContext(bricks, base.ring(), (min, max) -> box.center() >= min && box.center() <= max ? List.of(box) : List.of(),
            base.random(), base.bias(), -4, 19);
        BricksPattern pattern = new BricksPattern(context, bricks);
        Band scene = pattern.scene(0, -4, 19).band();
        int chunk = -4;
        while (chunk <= 19) {
            SegmentSurface surface = pattern.segmentAt(chunk);
            for (int c = surface.firstChunk(); c <= surface.lastChunk(); c++) {
                Band band = surface.band(0).crop(16 * c, 16);
                Band expected = scene.crop(16 * c, 16);
                for (int along = 16 * c; along < 16 * c + 16; along++) {
                    for (int ring = 0; ring < 48; ring++) {
                        BandKind kind = expected.get(ring, along);
                        if (!box.covers(along)) {
                            assertEquals(kind, band.get(ring, along), "(" + ring + ", " + along + ")");
                        } else if (pattern.isPatchCell(0, ring, along, box)) {
                            int cross = PillarRing.INSTANCE.first(0, ring);
                            assertEquals(kind, surface.patch(box.center()).get(cross, along - box.center()), "patch (" + ring + ", " + along + ")");
                        }
                    }
                }
            }
            chunk = surface.lastChunk() + 1;
        }
    }

}
