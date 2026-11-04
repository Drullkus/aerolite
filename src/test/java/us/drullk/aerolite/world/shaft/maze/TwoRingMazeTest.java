package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.shaft.surface.CrossingCandidate;
import us.drullk.aerolite.world.shaft.surface.RingKind;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;
import us.drullk.aerolite.world.shaft.surface.SectionBlock;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a shape with two rings samples one tree per ring along a plain shaft.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class TwoRingMazeTest {

    private static final int PLATE = 28;

    /** Two plate rings of 28 blocks with corner nodes at 0, 12, 14 and 26. The maze reads no offset. */
    private static final RingShape PLATES = new RingShape() {
        @Override
        public int rings() {
            return 2;
        }

        @Override
        public int length(int ring) {
            return PLATE;
        }

        @Override
        public int first(int ring, int index) {
            return index;
        }

        @Override
        public int second(int ring, int index) {
            return ring;
        }

        @Override
        public int index(int ring, int first, int second) {
            return second == ring && first >= 0 && first < PLATE ? first : -1;
        }

        @Override
        public RingKind kind(int ring, int index) {
            int i = Math.floorMod(index, PLATE);
            return i == 0 || i == 12 || i == 14 || i == 26 ? RingKind.CORNER_NODE : i == 13 || i == 27 ? RingKind.PLATE_SIDE : RingKind.FACE;
        }

        @Override
        public List<SectionOffset> floors(int ring, int index) {
            return List.of();
        }

        @Override
        public List<SectionBlock> section() {
            return List.of();
        }

        @Override
        public List<CrossingCandidate> crossings(SectionAxis normal, int sign) {
            return List.of();
        }
    };

    @Test
    void eachRingIsOneTree() {
        MazeEdges edges = new MazeEdges();
        for (float bias : ShaftMazeTest.BIASES) {
            for (long seed = 0; seed < 4; seed++) {
                ShaftMaze maze = new ShaftMaze(PLATES, edges, (min, max) -> List.of(), new XoroshiroRandomSource(seed).forkPositional(), -100, 100, bias);
                List<List<MazeEdge>> byRing = List.of(new ArrayList<>(), new ArrayList<>());
                for (int chunk = 0; chunk <= 10; chunk++) {
                    for (MazeEdge edge : maze.segmentAt(chunk).edges()) {
                        int ringA = PLATES.ringOf(((MazeNode.Ruling) edge.a()).node());
                        int ringB = PLATES.ringOf(((MazeNode.Ruling) edge.b()).node());
                        assertEquals(ringA, ringB, "An edge joins the two plates: " + edge);
                        byRing.get(ringA).add(edge);
                    }
                }
                for (List<MazeEdge> ring : byRing) {
                    int nodes = TreeCheck.assertOneTree(ring);
                    long rulings = ring.stream().flatMap(edge -> Stream.of(edge.a(), edge.b())).map(node -> ((MazeNode.Ruling) node).along()).distinct().count();
                    assertTrue(rulings >= 11 * ShaftMaze.RULINGS_PER_CHUNK, "A ring misses a ruling at bias " + bias);
                    assertTrue(nodes >= 11 * ShaftMaze.RULINGS_PER_CHUNK * (PLATE / 2 - 4));
                }
            }
        }
    }

}
