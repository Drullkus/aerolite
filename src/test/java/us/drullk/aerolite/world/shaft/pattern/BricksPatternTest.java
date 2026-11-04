package us.drullk.aerolite.world.shaft.pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.shaft.pattern.bricks.BricksPattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks of {@code bricks.py --check} on the bricks pattern: every size, mode, loop share and box option for the seeds 1 to 3.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class BricksPatternTest {

    static Stream<Arguments> params() {
        List<Arguments> out = new ArrayList<>();
        for (long seed = 1; seed <= 3; seed++) {
            for (BricksSettings.Size size : BricksSettings.Size.values()) {
                for (PatternChecks.Mode mode : PatternChecks.Mode.values()) {
                    for (double share : new double[] {0, 0.5, 1}) {
                        for (boolean box : new boolean[] {false, true}) {
                            out.add(Arguments.of(seed, size, mode, share, box));
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    private static BricksPattern pattern(long seed, BricksSettings.Size size, PatternChecks.Mode mode, double share, boolean box) {
        BricksSettings settings = new BricksSettings(size, mode.paths(share));
        return new BricksPattern(PatternChecks.context(settings, seed, box), settings);
    }

    @ParameterizedTest
    @MethodSource("params")
    void sceneKeepsTheRules(long seed, BricksSettings.Size size, PatternChecks.Mode mode, double share, boolean box) {
        BricksPattern pattern = pattern(seed, size, mode, share, box);
        PatternChecks.check(pattern, size + " " + mode + " " + share + " box " + box + " seed " + seed, box, true, false, false, false);
    }

    /** A large brick course has its head joints at face column 2 or 10 on every face, and its bed joints on rows 7 and 15. */
    @Test
    void largeBricksHaveTheirJointsOnEveryFace() {
        Band band = pattern(1, BricksSettings.Size.LARGE, PatternChecks.Mode.PLAIN, 1, false).scene(0, 0, 1).band();
        for (int face = 0; face < 4; face++) {
            for (int row = 0; row < 7; row++) {
                assertEquals(BandKind.GROOVE, band.get(12 * face + 2, row), "course 0 joint");
                assertEquals(BandKind.GROOVE, band.get(12 * face + 10, row + 8), "course 1 joint");
            }
        }
        for (int ring = 0; ring < 48; ring++) {
            assertEquals(BandKind.GROOVE, band.get(ring, 7));
            assertEquals(BandKind.GROOVE, band.get(ring, 15));
        }
    }

    /** A small face on phase 0 has one head joint at column 6. The neighbor faces have two at columns 3 and 9. */
    @Test
    void smallBricksSwapThePhaseOfNeighborFaces() {
        for (long seed = 1; seed <= 4; seed++) {
            Band band = pattern(seed, BricksSettings.Size.SMALL, PatternChecks.Mode.PLAIN, 1, false).scene(0, 0, 0).band();
            for (int course = 0; course < 4; course++) {
                int row = 4 * course + 1;
                for (int face = 0; face < 4; face++) {
                    boolean six = band.get(12 * face + 6, row) == BandKind.GROOVE;
                    boolean three = band.get(12 * face + 3, row) == BandKind.GROOVE && band.get(12 * face + 9, row) == BandKind.GROOVE;
                    assertTrue(six != three, "face " + face + " course " + course);
                    boolean nextSix = band.get(12 * ((face + 1) % 4) + 6, row) == BandKind.GROOVE;
                    assertTrue(six != nextSix, "neighbor faces share the phase");
                }
            }
        }
    }

    /** Jumbled courses are 4, 4 and 5 rows high, and no head joint shares a ring position with one in the course above or below. */
    @Test
    void jumbledJointsAvoidTheNeighborCourses() {
        Band band = pattern(5, BricksSettings.Size.JUMBLED, PatternChecks.Mode.PLAIN, 1, false).scene(0, 0, PatternChecks.CHUNKS - 1).band();
        for (int chunk = 0; chunk < PatternChecks.CHUNKS; chunk++) {
            int beds = 0;
            for (int row = 16 * chunk; row < 16 * chunk + 16; row++) {
                if (band.get(1, row) == BandKind.GROOVE) {
                    beds++;
                }
            }
            assertEquals(3, beds, "chunk " + chunk);
            assertEquals(BandKind.GROOVE, band.get(1, 16 * chunk + 15), "the last bed joint of chunk " + chunk);
        }
        assertEquals(List.of(), us.drullk.aerolite.world.shaft.surface.path.BandChecks.crossings(band));
    }

}
