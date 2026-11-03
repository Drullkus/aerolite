package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

/**
 * A union-find over {@code long} items, created on first use. The representative of a set is its smallest item.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class LongUnionFind {

    private final Long2LongOpenHashMap up = new Long2LongOpenHashMap();

    /** The representative of the item. An unknown item becomes a set of its own. */
    public long find(long item) {
        if (!this.up.containsKey(item)) {
            this.up.put(item, item);
            return item;
        }
        long root = item;
        while (this.up.get(root) != root) {
            root = this.up.get(root);
        }
        while (item != root) {
            long next = this.up.get(item);
            this.up.put(item, root);
            item = next;
        }
        return root;
    }

    /** Joins the sets of the two items. False when they already were one set. */
    public boolean union(long a, long b) {
        long ra = this.find(a);
        long rb = this.find(b);
        if (ra == rb) {
            return false;
        }
        if (rb < ra) {
            this.up.put(ra, rb);
        } else {
            this.up.put(rb, ra);
        }
        return true;
    }

    /** Every item that this union-find knows, sorted. */
    public LongList items() {
        LongList out = new LongArrayList(this.up.keySet());
        out.sort(null);
        return out;
    }

    /** The number of sets among the items. */
    public int count(LongList items) {
        LongList roots = new LongArrayList();
        for (long item : items) {
            roots.add(this.find(item));
        }
        return (int) roots.longStream().distinct().count();
    }

}
