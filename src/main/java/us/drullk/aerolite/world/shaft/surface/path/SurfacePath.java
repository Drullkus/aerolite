package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;

import java.util.Comparator;

/**
 * One surface path of a region. It leaves a groove, passes face nodes, and ends on a groove or on a face node.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param blocks the path blocks, from the start groove onward
 * @param ends the start groove, then the end groove when the path ends on a groove
 * @param turns the corners of the path, 0 to 2
 */
public record SurfacePath(LongList blocks, LongList ends, int turns) {

    /** Sorts by the blocks, then by the ends, as one position list. */
    public static final Comparator<SurfacePath> SORT = (a, b) -> GrooveEdge.compareLists(a.sequenceKey(), b.sequenceKey());

    public SurfacePath {
        blocks = LongLists.unmodifiable(new LongArrayList(blocks));
        ends = LongLists.unmodifiable(new LongArrayList(ends));
        if (blocks.isEmpty() || ends.isEmpty() || ends.size() > 2) {
            throw new IllegalArgumentException("A surface path needs blocks and one or two end grooves");
        }
    }

    /** True when the path ends on a groove at both ends. */
    public boolean isThrough() {
        return this.ends.size() == 2;
    }

    /** The start groove, the blocks, then the end groove when one exists. */
    public LongList sequence() {
        LongList out = new LongArrayList(this.blocks.size() + 2);
        out.add(this.ends.getLong(0));
        out.addAll(this.blocks);
        if (this.isThrough()) {
            out.add(this.ends.getLong(1));
        }
        return out;
    }

    private LongList sequenceKey() {
        LongList out = new LongArrayList(this.blocks);
        out.addAll(this.ends);
        return out;
    }

    /**
     * The shape of the path: I without a corner, L with one corner, S with two corners and parallel end legs,
     * U with two corners and opposite end legs.
     */
    public Shape shape(Band band) {
        if (this.turns == 0) {
            return Shape.I;
        }
        if (this.turns == 1) {
            return Shape.L;
        }
        LongList seq = this.sequence();
        BandDirection first = band.direction(seq.getLong(0), seq.getLong(1));
        BandDirection last = band.direction(seq.getLong(seq.size() - 2), seq.getLong(seq.size() - 1));
        return first == last ? Shape.S : Shape.U;
    }

    /** The shapes of a surface path. */
    public enum Shape {
        I, L, S, U
    }

}
