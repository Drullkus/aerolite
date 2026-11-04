package us.drullk.aerolite.world.shaft.pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.shaft.pattern.scales.ScalesPattern;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks of {@code scales.py --check} on the scales pattern: both tail directions, paths over tails on and off,
 * every mode, loop share and box option for the seeds 1 and 2.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class ScalesPatternTest {

    static Stream<Arguments> params() {
        List<Arguments> out = new ArrayList<>();
        for (long seed = 1; seed <= 2; seed++) {
            for (int tail : new int[] {ScalesPattern.TAIL_UP, ScalesPattern.TAIL_DOWN}) {
                for (boolean over : new boolean[] {false, true}) {
                    for (PatternChecks.Mode mode : PatternChecks.Mode.values()) {
                        if (over && mode == PatternChecks.Mode.PLAIN) {
                            continue;
                        }
                        for (double share : new double[] {0, 0.5, 1}) {
                            for (boolean box : new boolean[] {false, true}) {
                                out.add(Arguments.of(seed, tail, over, mode, share, box));
                            }
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    private static ScalesPattern pattern(long seed, int tail, boolean over, PatternChecks.Mode mode, double share, boolean box) {
        ScalesSettings settings = new ScalesSettings(mode.paths(share), over);
        return new ScalesPattern(PatternChecks.context(settings, seed, box), settings, tail, new PatternCache());
    }

    @ParameterizedTest
    @MethodSource("params")
    void sceneKeepsTheRules(long seed, int tail, boolean over, PatternChecks.Mode mode, double share, boolean box) {
        ScalesPattern pattern = pattern(seed, tail, over, mode, share, box);
        String name = "tail " + tail + (over ? " over tails " : " ") + mode + " " + share + " box " + box + " seed " + seed;
        PatternChecks.check(pattern, name, box, false, false, true, over);
    }

    /** Each tail caps the axis groove of the next course. Without cut paths over tails, every tail stays wall. */
    @Test
    void tailsCapTheNextCourse() {
        for (int tail : new int[] {ScalesPattern.TAIL_UP, ScalesPattern.TAIL_DOWN}) {
            PatternScene scene = pattern(1, tail, false, PatternChecks.Mode.CUT, 1, false).scene(0, 0, 3);
            assertEquals(12 * 16, scene.tails().size(), "one tail per tile");
            scene.tails().forEach(pos -> {
                assertEquals(BandKind.WALL, scene.band().get(pos));
                long beyond = scene.band().pos(us.drullk.aerolite.world.shaft.surface.path.BandPos.ring(pos),
                    us.drullk.aerolite.world.shaft.surface.path.BandPos.axis(pos) + tail);
                if (scene.band().inside(beyond)) {
                    assertEquals(BandKind.GROOVE, scene.base().get(beyond), "the tail does not cap an axis groove");
                }
            });
        }
    }

    /** Cut paths over tails carve some tails. */
    @Test
    void cutPathsOverTailsCarveTails() {
        PatternScene scene = pattern(1, ScalesPattern.TAIL_DOWN, true, PatternChecks.Mode.CUT, 1, false).scene(0, 0, PatternChecks.CHUNKS - 1);
        assertTrue(scene.tails().longStream().anyMatch(pos -> scene.band().get(pos) == BandKind.GROOVE));
    }

}
