package us.drullk.aerolite.world.shaft.pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;
import us.drullk.aerolite.world.shaft.pattern.tiles.TilesPattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks of {@code tiles.py --check} on the tiles pattern: every allowed layout, mode, loop share and box option for the seeds 1 and 2.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class TilesPatternTest {

    static Stream<Arguments> params() {
        List<Arguments> out = new ArrayList<>();
        for (long seed = 1; seed <= 2; seed++) {
            for (TilesSettings.Size size : TilesSettings.Size.values()) {
                for (TilesSettings.Stagger stagger : TilesSettings.Stagger.values()) {
                    for (boolean encased : new boolean[] {false, true}) {
                        if (stagger == TilesSettings.Stagger.X && (size == TilesSettings.Size.LARGE || encased)) {
                            continue;
                        }
                        for (PatternChecks.Mode mode : PatternChecks.Mode.values()) {
                            for (double share : new double[] {0, 0.5, 1}) {
                                for (boolean box : new boolean[] {false, true}) {
                                    out.add(Arguments.of(seed, size, stagger, encased, mode, share, box));
                                }
                            }
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    private static TilesPattern pattern(long seed, TilesSettings.Size size, TilesSettings.Stagger stagger, boolean encased, SurfacePathSettings paths, boolean box) {
        TilesSettings settings = new TilesSettings(size, stagger, encased, paths);
        return new TilesPattern(PatternChecks.context(settings, seed, box), settings);
    }

    @ParameterizedTest
    @MethodSource("params")
    void sceneKeepsTheRules(long seed, TilesSettings.Size size, TilesSettings.Stagger stagger, boolean encased, PatternChecks.Mode mode, double share, boolean box) {
        TilesPattern pattern = pattern(seed, size, stagger, encased, mode.paths(share), box);
        String name = size + " " + stagger + (encased ? " encased " : " ") + mode + " " + share + " box " + box + " seed " + seed;
        PatternChecks.check(pattern, name, box, !encased, true, size == TilesSettings.Size.SMALL, false);
    }

    /** Small tiles have axis grooves at face columns 2, 6 and 10 and ring grooves on every fourth row. */
    @Test
    void smallTilesFollowTheGrid() {
        Band band = pattern(1, TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, false, PatternChecks.Mode.PLAIN.paths(1), false).scene(0, 0, 0).band();
        for (int ring = 0; ring < 48; ring++) {
            boolean axis = ring % 12 == 2 || ring % 12 == 6 || ring % 12 == 10;
            for (int row = 0; row < 16; row++) {
                boolean groove = axis || row % 4 == 3;
                assertEquals(groove ? BandKind.GROOVE : BandKind.WALL, band.get(ring, row), "(" + ring + ", " + row + ")");
            }
        }
    }

    /** Encased large tiles keep the corner strip solid, and the ring grooves of the other tile columns stay. */
    @Test
    void encasedKeepsTheCornerStrip() {
        Band band = pattern(1, TilesSettings.Size.LARGE, TilesSettings.Stagger.NONE, true, PatternChecks.Mode.PLAIN.paths(1), false).scene(0, 0, 1).band();
        for (int row = 0; row < 32; row++) {
            for (int ring : new int[] {47, 0, 1, 11, 12, 13}) {
                assertEquals(BandKind.WALL, band.get(ring, row));
            }
        }
        assertEquals(BandKind.GROOVE, band.get(6, 7));
    }

    /** Loops off gives the result of a loop share of 0. */
    @Test
    void loopsOffEqualsZeroShare() {
        SurfacePathSettings zero = PatternChecks.Mode.CUT.paths(0);
        SurfacePathSettings off = PatternChecks.Mode.CUT.paths(0.5).withAllowLoops(false);
        Band a = pattern(3, TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, false, zero, false).scene(0, 0, 3).band();
        Band b = pattern(3, TilesSettings.Size.SMALL, TilesSettings.Stagger.NONE, false, off, false).scene(0, 0, 3).band();
        assertTrue(a.sameKinds(b));
    }

}
