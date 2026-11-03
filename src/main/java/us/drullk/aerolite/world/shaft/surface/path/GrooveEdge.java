package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;

import java.util.Comparator;

/**
 * One edge of a groove graph: two vertices and the chain of groove blocks between them, from {@code u} to {@code v}.
 * The chain can be empty. In a canonical edge, {@code u} sorts at or before {@code v}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public record GrooveEdge(long u, long v, LongList chain) {

    /** Sorts by the end vertices, then by the chain. */
    public static final Comparator<GrooveEdge> SORT = Comparator.comparingLong(GrooveEdge::u)
        .thenComparingLong(GrooveEdge::v)
        .thenComparing(GrooveEdge::chain, GrooveEdge::compareLists);

    public GrooveEdge {
        chain = LongLists.unmodifiable(new LongArrayList(chain));
    }

    /**
     * The canonical edge of the two vertices and the chain from {@code u} to {@code v}.
     * The vertex that sorts first becomes {@code u}. A loop on one vertex takes the chain direction that sorts first.
     */
    public static GrooveEdge canonical(long u, long v, LongList chain) {
        if (u > v) {
            return new GrooveEdge(v, u, reversed(chain));
        }
        if (u == v) {
            LongList back = reversed(chain);
            if (compareLists(back, chain) < 0) {
                return new GrooveEdge(u, v, back);
            }
        }
        return new GrooveEdge(u, v, chain);
    }

    private static LongList reversed(LongList list) {
        LongList out = new LongArrayList(list.size());
        for (int i = list.size() - 1; i >= 0; i--) {
            out.add(list.getLong(i));
        }
        return out;
    }

    /** The lexicographic comparison of two position lists. A prefix sorts first. */
    static int compareLists(LongList a, LongList b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int c = Long.compare(a.getLong(i), b.getLong(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

}
