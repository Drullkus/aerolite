package us.drullk.aerolite.world.shaft.shape;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.CrossingCandidate;
import us.drullk.aerolite.world.shaft.surface.PatchColumn;
import us.drullk.aerolite.world.shaft.surface.RingKind;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;
import us.drullk.aerolite.world.shaft.surface.SectionBlock;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the two plate rings, the trusses, the resistance and the junction link rules of the Bridge.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class BridgeRingTest {

    private static final List<BridgeRing> RINGS = List.of(BridgeRing.STACK_FIRST, BridgeRing.STACK_SECOND);

    private static SectionOffset offset(BridgeRing ring, int u, int v) {
        return ring.stack() == SectionAxis.FIRST ? new SectionOffset(v, u) : new SectionOffset(u, v);
    }

    /** The block (u, v) of a plate ring: the outer face, one side block, the inner face, the other side block. */
    private static int[] expected(int r, int index) {
        int sign = r == 0 ? 1 : -1;
        if (index <= 12) {
            return new int[]{index - 6, 6 * sign};
        }
        if (index == 13) {
            return new int[]{6, 5 * sign};
        }
        if (index <= 26) {
            return new int[]{6 - (index - 14), 4 * sign};
        }
        return new int[]{-6, 5 * sign};
    }

    @Test
    void platesAreTwoRingsOf28() {
        for (BridgeRing ring : RINGS) {
            assertEquals(2, ring.rings());
            assertEquals(2 * BridgeRing.LENGTH / 2, ring.nodeCount());
            for (int r = 0; r < 2; r++) {
                assertEquals(BridgeRing.LENGTH, ring.length(r));
                for (int index = 0; index < BridgeRing.LENGTH; index++) {
                    int[] uv = expected(r, index);
                    assertEquals(uv[0], ring.ringU(r, index), "u of ring " + r + " block " + index);
                    assertEquals(uv[1], ring.ringV(r, index), "v of ring " + r + " block " + index);
                    SectionOffset at = offset(ring, uv[0], uv[1]);
                    assertEquals(index, ring.index(r, at.first(), at.second()));
                    assertEquals(-1, ring.index(1 - r, at.first(), at.second()));
                    RingKind kind = index == 0 || index == 12 || index == 14 || index == 26 ? RingKind.CORNER_NODE
                            : index == 13 || index == 27 ? RingKind.PLATE_SIDE : RingKind.FACE;
                    assertEquals(kind, ring.kind(r, index), "kind of ring " + r + " block " + index);
                    // Every floor sits in the middle layer of the plate.
                    for (SectionOffset floor : ring.floors(r, index)) {
                        int v = ring.stack() == SectionAxis.FIRST ? floor.first() : floor.second();
                        assertEquals(5 * (r == 0 ? 1 : -1), v, "floor of ring " + r + " block " + index);
                    }
                    assertEquals(1, ring.floors(r, index).size());
                    assertEquals(Math.abs(uv[1]) == 4 && Math.abs(uv[0]) <= 5, ring.flush(r, index), "flush of ring " + r + " block " + index);
                }
            }
        }
    }

    @Test
    void sectionIsThePlates() {
        for (BridgeRing ring : RINGS) {
            assertEquals(2 * 3 * 13, ring.section().size());
            assertEquals(2 * BridgeRing.LENGTH, ring.section().stream().filter(SectionBlock::outer).count());
            for (SectionBlock block : ring.section()) {
                int v = ring.stack() == SectionAxis.FIRST ? block.first() : block.second();
                assertTrue(Math.abs(v) >= 4, "A section block lies in the truss space: " + block);
            }
        }
    }

    @Test
    void resistanceKeepsTheMiddleLayer() {
        BridgeRing ring = BridgeRing.STACK_FIRST;
        for (int u = -6; u <= 6; u++) {
            assertEquals(1, ring.resistance(5, u));
            assertEquals(1, ring.resistance(-5, u));
            for (int v : new int[]{4, 6, -4, -6}) {
                assertEquals(0, ring.resistance(v, u));
            }
        }
    }

    /** Row r is v = 6 - r. Red sits at u = 4 and -5, green at u = 5 and -4, both two blocks wide per row with a period of 8. */
    @Test
    void trussesFollowTheRowRule() {
        for (BridgeRing ring : RINGS) {
            for (int s = -16; s < 16; s++) {
                Set<SectionOffset> trusses = new HashSet<>(ring.trusses(s));
                int m = Math.floorMod(s, 8);
                for (int v = -6; v <= 6; v++) {
                    int row = 6 - v;
                    boolean red = Math.abs(v) <= 4 && (m == Math.floorMod(5 - row, 8) || m == Math.floorMod(6 - row, 8));
                    boolean green = Math.abs(v) <= 4 && (m == Math.floorMod(row + 1, 8) || m == Math.floorMod(row + 2, 8));
                    for (int u = -6; u <= 6; u++) {
                        boolean expected = (u == 4 || u == -5) && red || (u == 5 || u == -4) && green;
                        assertEquals(expected, trusses.contains(offset(ring, u, v)), "truss at s " + s + ", u " + u + ", v " + v);
                    }
                }
                // Both diagonals anchor in the inner plate layers at s mod 8 = 3 and 4.
                boolean anchors = m == 3 || m == 4;
                for (int v : new int[]{4, -4}) {
                    for (int u : new int[]{4, 5, -4, -5}) {
                        assertEquals(anchors, trusses.contains(offset(ring, u, v)), "anchor at s " + s + ", u " + u + ", v " + v);
                    }
                }
                // The middle row holds both diagonals at s mod 8 = 7 and 0.
                boolean middle = m == 7 || m == 0;
                assertEquals(middle, trusses.contains(offset(ring, 4, 0)));
                assertEquals(middle, trusses.contains(offset(ring, 5, 0)));
                for (SectionOffset truss : trusses) {
                    assertTrue(ring.isTruss(s, truss.first(), truss.second()));
                }
            }
        }
    }

    @Test
    void horizontalBridgeTakesOneCrossing() {
        BridgeRing ring = BridgeRing.STACK_FIRST;
        assertFalse(ring.boxCutsRings(new ShaftMaze.Box(0, 1)));
        assertTrue(ring.holeThroughBox());
        Set<Integer> plates = new HashSet<>();
        for (int sign : new int[]{-1, 1}) {
            for (long seed = 0; seed < 32; seed++) {
                List<CrossingCandidate> candidates = ring.crossings(SectionAxis.SECOND, sign, new ShaftMaze.Box(0, sign), new XoroshiroRandomSource(seed));
                assertEquals(1, candidates.size());
                CrossingCandidate candidate = candidates.getFirst();
                int r = ring.ringOf(candidate.node());
                int index = ring.indexOf(candidate.node());
                plates.add(r);
                assertEquals(6 * sign, ring.ringU(r, index));
                assertEquals(r == 0 ? 4 : -4, ring.ringV(r, index));
                assertEquals(RingKind.CORNER_NODE, ring.kind(r, index));
                assertEquals(r == 0 ? Ring.PATCH_INDEX : -Ring.PATCH_INDEX, candidate.across());
                assertFalse(candidate.foreign());
            }
        }
        assertEquals(Set.of(0, 1), plates, "The random never picks one of the plates");
    }

    @Test
    void columnBridgeJoinsBothPatches() {
        for (BridgeRing ring : RINGS) {
            ShaftMaze.Box column = new ShaftMaze.Box(0, 1, ShaftMaze.Patching.COLUMN_ALONG_X);
            assertTrue(ring.boxCutsRings(column));
            for (int sign : new int[]{-1, 1}) {
                List<CrossingCandidate> matched = ring.crossings(ring.cross(), sign, column, new XoroshiroRandomSource(1));
                assertEquals(2, matched.size());
                assertEquals(Set.of(0, 1), Set.of(ring.ringOf(matched.get(0).node()), ring.ringOf(matched.get(1).node())));
                for (CrossingCandidate candidate : matched) {
                    int r = ring.ringOf(candidate.node());
                    assertEquals(6 * sign, ring.ringU(r, ring.indexOf(candidate.node())));
                    assertFalse(candidate.foreign());
                }
                List<CrossingCandidate> mismatched = ring.crossings(ring.stack(), sign, column, new XoroshiroRandomSource(1));
                assertEquals(6, mismatched.size());
                for (CrossingCandidate candidate : mismatched) {
                    int r = ring.ringOf(candidate.node());
                    int index = ring.indexOf(candidate.node());
                    // The near plate faces the patch. The far plate faces the patch of the horizontal pillar.
                    int near = sign > 0 ? 0 : 1;
                    assertEquals(candidate.foreign(), r != near);
                    assertEquals((r == 0 ? 6 : -6), ring.ringV(r, index));
                    assertEquals(2 * candidate.across(), ring.ringU(r, index));
                    assertTrue(Math.abs(candidate.across()) < Ring.PATCH_INDEX);
                }
            }
        }
    }

    @Test
    void patchColumnsSkipTheTrussSpace() {
        BridgeRing ring = BridgeRing.STACK_FIRST;
        List<PatchColumn> side = ring.patchColumns(SectionAxis.SECOND, 1);
        for (int cross = -4; cross <= 4; cross++) {
            PatchColumn column = side.get(cross + 4);
            if (Math.abs(cross) == 4) {
                assertEquals(cross > 0 ? 0 : 1, column.ring());
                assertEquals(6, ring.ringU(column.ring(), column.index()));
                assertEquals(cross, ring.ringV(column.ring(), column.index()));
            } else {
                assertFalse(column.present(), "The truss space holds a patch column at " + cross);
            }
        }
        List<PatchColumn> face = ring.patchColumns(SectionAxis.FIRST, -1);
        for (int cross = -4; cross <= 4; cross++) {
            assertEquals(new PatchColumn(1, ring.index(1, -6, cross)), face.get(cross + 4));
        }
    }

}
