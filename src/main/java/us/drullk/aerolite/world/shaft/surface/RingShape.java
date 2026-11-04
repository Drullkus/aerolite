package us.drullk.aerolite.world.shaft.surface;

import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.path.Band;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The outer surface of one cross section of a pillar shape.
 *
 * <p>The contract of a ring description:
 * <ul>
 * <li>The shape has one or more rings. A Pillar and an I-beam have one ring. A Bridge has one ring per plate.</li>
 * <li>A ring has an even length. A ring index runs from 0 to the length, and the ring wraps.</li>
 * <li>Each ring index names one outer block by its first and second offset in the frame of the pillar.</li>
 * <li>A node sits on each even ring index. The node index counts the nodes of ring 0 first, then the nodes of ring 1.</li>
 * <li>A node of the kind {@link RingKind#CORNER_NODE} has no axis edge. Every other node has one.</li>
 * <li>The floor blocks of a ring index become the maze path block when the index carves.</li>
 * <li>The section lists every solid block of the cross section, first offset major.</li>
 * </ul>
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public interface RingShape {

    /** The number of rings. */
    int rings();

    /** The blocks around the ring. */
    int length(int ring);

    /** The first offset of the outer block at the ring index. */
    int first(int ring, int index);

    /** The second offset of the outer block at the ring index. */
    int second(int ring, int index);

    /** The ring index of the outer block at the offsets, or -1 when the ring does not hold it. */
    int index(int ring, int first, int second);

    RingKind kind(int ring, int index);

    /** The blocks one step inward from the outer block. They become the maze path block when the index carves. */
    List<SectionOffset> floors(int ring, int index);

    /** Every solid block of the cross section, first offset major. */
    List<SectionBlock> section();

    /**
     * The ring nodes that face a junction patch, in a fixed sequence.
     * The patch is on the face of the box where the offset {@code normal} has the sign {@code sign}.
     * A foreign candidate faces the opposite face of the box.
     */
    List<CrossingCandidate> crossings(SectionAxis normal, int sign);

    /**
     * The crossing candidates of one junction box. The maze calls this form.
     * The random is a pure function of the seed, the pillar and the box center, so a shape can pick candidates per junction.
     */
    default List<CrossingCandidate> crossings(SectionAxis normal, int sign, ShaftMaze.Box box, RandomSource random) {
        return this.crossings(normal, sign);
    }

    /** True when the junction segment drops the rulings that the box covers. */
    default boolean boxCutsRings(ShaftMaze.Box box) {
        return true;
    }

    /**
     * The ring block that faces a junction patch, per cross offset from {@code -PATCH_EDGE} to {@code PATCH_EDGE}.
     * The patch is on the face of the box where the offset {@code normal} has the sign {@code sign}.
     * Entry {@code k} holds the cross offset {@code k - PATCH_EDGE}. The default is the outer face of the 13x13 square.
     */
    default List<PatchColumn> patchColumns(SectionAxis normal, int sign) {
        List<PatchColumn> columns = new ArrayList<>(2 * Ring.PATCH_EDGE + 1);
        for (int cross = -Ring.PATCH_EDGE; cross <= Ring.PATCH_EDGE; cross++) {
            int index = normal == SectionAxis.SECOND ? this.index(0, cross, Ring.OUTER * sign) : this.index(0, Ring.OUTER * sign, cross);
            columns.add(index < 0 ? PatchColumn.NONE : new PatchColumn(0, index));
        }
        return List.copyOf(columns);
    }

    /** True when a groove at the ring index paints a flush maze path block instead of a carve. */
    default boolean flush(int ring, int index) {
        return false;
    }

    /** The truss blocks of the cross section at an axis position. Each one replaces the block of the section there. */
    default List<SectionOffset> trusses(int along) {
        return List.of();
    }

    /** True when the offsets hold a truss block at the axis position. */
    default boolean isTruss(int along, int first, int second) {
        List<SectionOffset> trusses = this.trusses(along);
        return !trusses.isEmpty() && trusses.contains(new SectionOffset(first, second));
    }

    /** The erosion resistance of a solid block of the section, from 0 at a surface to 1 at a core. The default is the Pillar. */
    default double resistance(int first, int second) {
        int offset = Math.max(Math.abs(first), Math.abs(second));
        return offset > Ring.OUTER ? 0 : Math.min(1, (double) (Ring.OUTER - offset) / (Ring.OUTER - 1));
    }

    /** True when a horizontal pillar of the shape passes through its junction boxes in an 11x11 hole. */
    default boolean holeThroughBox() {
        return false;
    }

    /** The ring indices where an axis step never links. */
    default int[] bandCorners(int ring) {
        return IntStream.range(0, this.length(ring)).filter(index -> this.kind(ring, index).isConvexCorner()).toArray();
    }

    /** An all-wall band of the ring over the axis range. */
    default Band band(int ring, int axis0, int length) {
        return new Band(this.length(ring), axis0, length, this.bandCorners(ring));
    }

    /** The number of nodes of all rings. */
    default int nodeCount() {
        return this.firstNode(this.rings());
    }

    /** The node index of the first node of the ring. */
    default int firstNode(int ring) {
        int node = 0;
        for (int r = 0; r < ring; r++) {
            node += this.length(r) / 2;
        }
        return node;
    }

    /** The ring that holds the node. */
    default int ringOf(int node) {
        int ring = 0;
        while (node >= this.length(ring) / 2) {
            node -= this.length(ring) / 2;
            ring++;
        }
        return ring;
    }

    /** The ring index of the node. */
    default int indexOf(int node) {
        return 2 * (node - this.firstNode(this.ringOf(node)));
    }

    /** The node at an even ring index. */
    default int node(int ring, int index) {
        return this.firstNode(ring) + Math.floorMod(index, this.length(ring)) / 2;
    }

    /** True when the node has axis edges. */
    default boolean hasAxisEdge(int node) {
        return this.kind(this.ringOf(node), this.indexOf(node)) != RingKind.CORNER_NODE;
    }

    /** The next node around the ring of the node. */
    default int nextNode(int node) {
        int ring = this.ringOf(node);
        return this.node(ring, this.indexOf(node) + 2);
    }

    /** The ring index of the block between two neighbor nodes of one ring. */
    default int blockBetween(int a, int b) {
        int ring = this.ringOf(a);
        int length = this.length(ring);
        int ia = this.indexOf(a);
        int ib = this.indexOf(b);
        return Math.abs(ia - ib) == 2 ? Math.min(ia, ib) + 1 : length - 1;
    }

}
