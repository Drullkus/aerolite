package us.drullk.aerolite.world.shaft.shape;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.surface.CrossingCandidate;
import us.drullk.aerolite.world.shaft.surface.PatchColumn;
import us.drullk.aerolite.world.shaft.surface.RingKind;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;
import us.drullk.aerolite.world.shaft.surface.SectionBlock;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the I-beam ring with the index table and the corner table of the shapes prototype, block by block.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class IBeamRingTest {

    /** The blocks (u, v) of the ring in index sequence, from the index table. */
    static List<int[]> table() {
        List<int[]> blocks = new ArrayList<>();
        for (int u = -6; u <= 6; u++) {
            blocks.add(new int[]{u, 6});
        }
        for (int v = 5; v >= 3; v--) {
            blocks.add(new int[]{6, v});
        }
        for (int u = 5; u >= 3; u--) {
            blocks.add(new int[]{u, 3});
        }
        blocks.add(new int[]{2, 3});
        for (int v = 2; v >= -2; v--) {
            blocks.add(new int[]{2, v});
        }
        blocks.add(new int[]{2, -3});
        for (int u = 3; u <= 5; u++) {
            blocks.add(new int[]{u, -3});
        }
        for (int v = -3; v >= -5; v--) {
            blocks.add(new int[]{6, v});
        }
        for (int u = 6; u >= -6; u--) {
            blocks.add(new int[]{u, -6});
        }
        // The mirror of 13 to 31 on the -u side, walked from (-6, -5) to (-6, 5).
        for (int v = -5; v <= -3; v++) {
            blocks.add(new int[]{-6, v});
        }
        for (int u = -5; u <= -3; u++) {
            blocks.add(new int[]{u, -3});
        }
        blocks.add(new int[]{-2, -3});
        for (int v = -2; v <= 2; v++) {
            blocks.add(new int[]{-2, v});
        }
        blocks.add(new int[]{-2, 3});
        for (int u = -3; u >= -5; u--) {
            blocks.add(new int[]{u, 3});
        }
        for (int v = 3; v <= 5; v++) {
            blocks.add(new int[]{-6, v});
        }
        return blocks;
    }

    /** The corner table: index, kind and floors (u, v). */
    private static final Map<Integer, RingKind> CORNER_KINDS = Map.ofEntries(
            Map.entry(0, RingKind.CORNER_NODE), Map.entry(12, RingKind.CORNER_NODE), Map.entry(15, RingKind.CONVEX_EDGE),
            Map.entry(19, RingKind.CONCAVE_EDGE), Map.entry(25, RingKind.CONCAVE_EDGE), Map.entry(29, RingKind.CONVEX_EDGE),
            Map.entry(32, RingKind.CORNER_NODE), Map.entry(44, RingKind.CORNER_NODE), Map.entry(47, RingKind.CONVEX_EDGE),
            Map.entry(51, RingKind.CONCAVE_EDGE), Map.entry(57, RingKind.CONCAVE_EDGE), Map.entry(61, RingKind.CONVEX_EDGE));
    private static final Map<Integer, int[][]> CORNER_FLOORS = Map.ofEntries(
            Map.entry(0, new int[][]{{-5, 5}}), Map.entry(12, new int[][]{{5, 5}}), Map.entry(15, new int[][]{{5, 4}}),
            Map.entry(19, new int[][]{{1, 3}, {2, 4}}), Map.entry(25, new int[][]{{2, -4}, {1, -3}}), Map.entry(29, new int[][]{{5, -4}}),
            Map.entry(32, new int[][]{{5, -5}}), Map.entry(44, new int[][]{{-5, -5}}), Map.entry(47, new int[][]{{-5, -4}}),
            Map.entry(51, new int[][]{{-1, -3}, {-2, -4}}), Map.entry(57, new int[][]{{-1, 3}, {-2, 4}}), Map.entry(61, new int[][]{{-5, 4}}));

    private static SectionOffset offset(IBeamRing ring, int u, int v) {
        return ring.stack() == SectionAxis.FIRST ? new SectionOffset(v, u) : new SectionOffset(u, v);
    }

    @Test
    void blocksMatchTheIndexTable() {
        List<int[]> table = table();
        assertEquals(IBeamRing.LENGTH, table.size());
        for (IBeamRing ring : List.of(IBeamRing.STACK_FIRST, IBeamRing.STACK_SECOND)) {
            assertEquals(1, ring.rings());
            assertEquals(IBeamRing.LENGTH, ring.length(0));
            assertEquals(IBeamRing.LENGTH / 2, ring.nodeCount());
            for (int index = 0; index < IBeamRing.LENGTH; index++) {
                int u = table.get(index)[0];
                int v = table.get(index)[1];
                assertEquals(u, ring.ringU(0, index), "u of block " + index);
                assertEquals(v, ring.ringV(0, index), "v of block " + index);
                SectionOffset at = offset(ring, u, v);
                assertEquals(at.first(), ring.first(0, index));
                assertEquals(at.second(), ring.second(0, index));
                assertEquals(index, ring.index(0, at.first(), at.second()));
            }
        }
    }

    @Test
    void cornersMatchTheCornerTable() {
        for (IBeamRing ring : List.of(IBeamRing.STACK_FIRST, IBeamRing.STACK_SECOND)) {
            for (int index = 0; index < IBeamRing.LENGTH; index++) {
                RingKind expected = CORNER_KINDS.getOrDefault(index, RingKind.FACE);
                assertEquals(expected, ring.kind(0, index), "kind of block " + index);
                if (CORNER_FLOORS.containsKey(index)) {
                    List<SectionOffset> floors = new ArrayList<>();
                    for (int[] floor : CORNER_FLOORS.get(index)) {
                        floors.add(offset(ring, floor[0], floor[1]));
                    }
                    assertEquals(new HashSet<>(floors), new HashSet<>(ring.floors(0, index)), "floors of block " + index);
                } else {
                    assertEquals(1, ring.floors(0, index).size(), "a face block has one floor: " + index);
                }
            }
            assertEquals(List.of(0, 12, 15, 29, 32, 44, 47, 61), Arrays.stream(ring.bandCorners(0)).boxed().toList());
        }
    }

    @Test
    void webNodesSitAtEvenV() {
        IBeamRing ring = IBeamRing.STACK_FIRST;
        for (int v : new int[]{2, 0, -2}) {
            assertEquals(0, ring.index(0, v, 2) % 2, "The web node at v = " + v + " is not on an even index");
            assertEquals(0, ring.index(0, v, -2) % 2);
        }
        for (int v : new int[]{4, 6, -4, -6}) {
            assertEquals(0, ring.index(0, v, 6) % 2, "The flange edge node at v = " + v + " is not on an even index");
        }
    }

    @Test
    void sectionIsTheH() {
        for (IBeamRing ring : List.of(IBeamRing.STACK_FIRST, IBeamRing.STACK_SECOND)) {
            Set<SectionOffset> solid = new HashSet<>();
            for (SectionBlock block : ring.section()) {
                solid.add(new SectionOffset(block.first(), block.second()));
                SectionOffset uv = ring.stack() == SectionAxis.FIRST ? new SectionOffset(block.second(), block.first()) : new SectionOffset(block.first(), block.second());
                assertEquals(ring.index(0, block.first(), block.second()) >= 0, block.outer(), "outer flag of " + block);
                assertTrue(Math.abs(uv.second()) >= 3 || Math.abs(uv.first()) <= 2, "A section block lies outside the H: " + block);
            }
            assertEquals(2 * 13 * 4 + 5 * 5, solid.size());
            assertEquals(IBeamRing.LENGTH, ring.section().stream().filter(SectionBlock::outer).count());
        }
    }

    @Test
    void resistanceKeepsTheMiddleLayers() {
        IBeamRing ring = IBeamRing.STACK_FIRST;
        for (int u = -6; u <= 6; u++) {
            for (int v : new int[]{4, 5, -4, -5}) {
                assertEquals(1, ring.resistance(v, u), "flange middle layer at " + u + ", " + v);
            }
            if (Math.abs(u) > 2) {
                for (int v : new int[]{3, 6, -3, -6}) {
                    assertEquals(0, ring.resistance(v, u), "flange surface layer at " + u + ", " + v);
                }
            }
        }
        double[] web = {0, 0.5, 1, 0.5, 0};
        for (int v = -2; v <= 2; v++) {
            for (int u = -2; u <= 2; u++) {
                assertEquals(web[u + 2], ring.resistance(v, u), "web layer at " + u + ", " + v);
            }
        }
        assertEquals(0, ring.resistance(0, 4), "Air has resistance");
    }

    @Test
    void flangeEdgesCrossOntoThePatchCorners() {
        for (IBeamRing ring : List.of(IBeamRing.STACK_FIRST, IBeamRing.STACK_SECOND)) {
            for (int sign : new int[]{-1, 1}) {
                List<CrossingCandidate> edge = ring.crossings(ring.cross(), sign);
                assertEquals(2, edge.size());
                for (CrossingCandidate candidate : edge) {
                    int index = ring.indexOf(candidate.node());
                    assertEquals(6 * sign, ring.ringU(0, index));
                    assertEquals(2 * candidate.across(), ring.ringV(0, index));
                    assertEquals(Ring.PATCH_INDEX, Math.abs(candidate.across()));
                    assertTrue(ring.hasAxisEdge(candidate.node()));
                }
                List<CrossingCandidate> face = ring.crossings(ring.stack(), sign);
                assertEquals(3, face.size());
                for (CrossingCandidate candidate : face) {
                    int index = ring.indexOf(candidate.node());
                    assertEquals(6 * sign, ring.ringV(0, index));
                    assertEquals(2 * candidate.across(), ring.ringU(0, index));
                }
            }
        }
    }

    @Test
    void patchColumnsReachTheWeb() {
        IBeamRing ring = IBeamRing.STACK_FIRST;
        List<PatchColumn> columns = ring.patchColumns(SectionAxis.SECOND, 1);
        for (int cross = -4; cross <= 4; cross++) {
            PatchColumn column = columns.get(cross + 4);
            assertEquals(0, column.ring());
            assertEquals(cross, ring.ringV(0, column.index()));
            assertEquals(Math.abs(cross) >= 3 ? 6 : 2, ring.ringU(0, column.index()), "patch column " + cross);
        }
        List<PatchColumn> face = ring.patchColumns(SectionAxis.FIRST, -1);
        for (int cross = -4; cross <= 4; cross++) {
            assertEquals(ring.index(0, -6, cross), face.get(cross + 4).index());
        }
    }

}
