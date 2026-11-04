package us.drullk.aerolite.world.shaft.surface;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.surface.path.Band;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the Pillar ring description with the tables of {@link Ring}, block by block.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class PillarRingTest {

    private final Ring ring = new Ring();
    private final PillarRing pillar = PillarRing.INSTANCE;

    @Test
    void blocksMatchTheRingTables() {
        assertEquals(1, this.pillar.rings());
        assertEquals(Ring.BLOCKS, this.pillar.length(0));
        assertEquals(Ring.NODES, this.pillar.nodeCount());
        for (int block = 0; block < Ring.BLOCKS; block++) {
            assertEquals(this.ring.first(block), this.pillar.first(0, block), "first offset of block " + block);
            assertEquals(this.ring.second(block), this.pillar.second(0, block), "second offset of block " + block);
            assertEquals(this.ring.block(this.ring.first(block), this.ring.second(block)), this.pillar.index(0, this.ring.first(block), this.ring.second(block)));
            boolean corner = block % 2 == 0 && this.ring.isCorner(block / 2);
            assertEquals(corner ? RingKind.CORNER_NODE : RingKind.FACE, this.pillar.kind(0, block), "kind of block " + block);
            int first = Math.max(-Ring.OUTER + 1, Math.min(Ring.OUTER - 1, this.ring.first(block)));
            int second = Math.max(-Ring.OUTER + 1, Math.min(Ring.OUTER - 1, this.ring.second(block)));
            assertEquals(List.of(new SectionOffset(first, second)), this.pillar.floors(0, block), "floor of block " + block);
        }
    }

    @Test
    void nodesMatchTheRingTables() {
        for (int node = 0; node < Ring.NODES; node++) {
            assertEquals(2 * node, this.pillar.indexOf(node));
            assertEquals(node, this.pillar.node(0, 2 * node));
            assertEquals(!this.ring.isCorner(node), this.pillar.hasAxisEdge(node));
            int next = (node + 1) % Ring.NODES;
            assertEquals(next, this.pillar.nextNode(node));
            assertEquals(this.ring.blockBetween(node, next), this.pillar.blockBetween(node, next));
        }
    }

    @Test
    void offsetsOffTheRingHaveNoIndex() {
        for (int first = -Ring.BOX; first <= Ring.BOX; first++) {
            for (int second = -Ring.BOX; second <= Ring.BOX; second++) {
                assertEquals(this.ring.block(first, second), this.pillar.index(0, first, second));
            }
        }
    }

    @Test
    void sectionIsTheFullSquare() {
        List<SectionBlock> section = this.pillar.section();
        assertEquals((2 * Ring.OUTER + 1) * (2 * Ring.OUTER + 1), section.size());
        int k = 0;
        for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
            for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                SectionBlock block = section.get(k++);
                assertEquals(new SectionBlock(first, second, Math.max(Math.abs(first), Math.abs(second)) == Ring.OUTER), block);
            }
        }
        assertEquals(Ring.BLOCKS, section.stream().filter(SectionBlock::outer).count());
    }

    @Test
    void crossingsAreTheThreeFaceNodes() {
        for (SectionAxis normal : SectionAxis.values()) {
            for (int sign : new int[]{-1, 1}) {
                List<CrossingCandidate> candidates = this.pillar.crossings(normal, sign);
                assertEquals(Ring.PATCH_NODES - 2, candidates.size());
                for (int k = 0; k < candidates.size(); k++) {
                    CrossingCandidate candidate = candidates.get(k);
                    int across = k + 1 - Ring.PATCH_INDEX;
                    int expected = normal == SectionAxis.SECOND ? this.ring.block(2 * across, Ring.OUTER * sign) / 2 : this.ring.block(Ring.OUTER * sign, 2 * across) / 2;
                    assertEquals(new CrossingCandidate(expected, across, false), candidate);
                }
            }
        }
        assertTrue(this.pillar.crossings(SectionAxis.SECOND, 1).stream().noneMatch(CrossingCandidate::foreign));
    }

    @Test
    void bandCornersAreTheFourCorners() {
        assertArrayEquals(Band.pillar(0, 1).corners(), this.pillar.bandCorners(0));
    }

}
