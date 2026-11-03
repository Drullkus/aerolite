package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks of the pattern prototypes on the synthetic patterns: every mode, the loop shares 0%, 50% and 100%, seeds 1 to 3.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class PatternSceneTest {

    private static final int CHUNKS = 12;
    private static final int TREE_END = CHUNKS % 2 == 1 ? CHUNKS : CHUNKS - 1;

    static Stream<TestPatterns.Params> params() {
        List<TestPatterns.Params> out = new ArrayList<>();
        for (long seed = 1; seed <= 3; seed++) {
            for (double share : new double[] {0, 0.5, 1}) {
                for (TestPatterns.Mode mode : TestPatterns.Mode.values()) {
                    out.add(new TestPatterns.Params(TestPatterns.Pattern.TILES, mode, seed, 0, share, false));
                }
                out.add(new TestPatterns.Params(TestPatterns.Pattern.TILES, TestPatterns.Mode.CUT, seed, 0, share, true));
                out.add(new TestPatterns.Params(TestPatterns.Pattern.LADDER, TestPatterns.Mode.PLAIN, seed, 0, share, false));
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("params")
    void sceneKeepsTheRules(TestPatterns.Params params) {
        TestPatterns.Scene scene = TestPatterns.scene(params, 0, CHUNKS);
        Band band = scene.band();
        assertEquals(List.of(), BandChecks.twoWide(band), "2-wide path");
        assertEquals(List.of(), BandChecks.crossings(band), "new plus crossing");
        assertEquals(List.of(), BandChecks.cornerRuns(band), "corner run");
        assertEquals(0, scene.result().unbreakable(), "unbreakable cycle edge");
        if (params.mode() == TestPatterns.Mode.CUT && !params.allowOrphans()) {
            assertEquals(List.of(), BandChecks.orphans(band), "orphan wall block");
        }
        for (int chunk = 0; chunk < CHUNKS; chunk++) {
            Band alone = TestPatterns.scene(params, chunk, chunk + 1).band();
            assertTrue(alone.sameKinds(band.crop(chunk * TestPatterns.CHUNK, TestPatterns.CHUNK)), "chunk " + chunk + " differs alone");
        }
        if (params.share() == 0) {
            int[] forest = TestPatterns.vertexForest(scene.result().graph(), scene.result().removed(), 0, TREE_END);
            assertEquals(0, forest[0], "kept vertex graph has a cycle");
            assertEquals(forest[1], forest[2], "kept vertex graph joins other vertices than the full graph");
            assertEquals(1, forest[2], "kept vertex graph is not one tree");
            assertEquals(0, Grooves.cycleRank(band.crop(0, TREE_END * TestPatterns.CHUNK)), "groove blocks have a cycle");
        }
    }

    @ParameterizedTest
    @MethodSource("params")
    void sameSeedGivesSameBand(TestPatterns.Params params) {
        Band first = TestPatterns.scene(params, 2, 6).band();
        Band second = TestPatterns.scene(params, 2, 6).band();
        assertTrue(first.sameKinds(second));
    }

    @ParameterizedTest
    @MethodSource("params")
    void higherShareRemovesASubset(TestPatterns.Params params) {
        LongSet zero = new LongOpenHashSet(TestPatterns.scene(params.withShare(0), 0, CHUNKS).result().removed());
        LongSet removed = new LongOpenHashSet(TestPatterns.scene(params, 0, CHUNKS).result().removed());
        assertTrue(zero.containsAll(removed));
    }

    @ParameterizedTest
    @MethodSource("params")
    void fullShareKeepsEveryGroove(TestPatterns.Params params) {
        assertEquals(0, TestPatterns.scene(params.withShare(1), 0, CHUNKS).result().removed().size());
    }

}
