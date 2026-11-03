package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * The groove blocks of a band as a graph of linked neighbor blocks.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class Grooves {

    private Grooves() {
    }

    /** The set of groove block positions. */
    public static LongSet blocks(Band band) {
        return new LongOpenHashSet(band.blocksOf(BandKind.GROOVE));
    }

    /** Each block of the set mapped to its linked neighbors inside the set, in the sequence of {@link BandDirection}. */
    public static Long2ObjectOpenHashMap<LongList> links(Band band, LongSet blocks) {
        Long2ObjectOpenHashMap<LongList> out = new Long2ObjectOpenHashMap<>(blocks.size());
        blocks.forEach(pos -> {
            LongList linked = new LongArrayList(4);
            for (long next : band.neighbors(pos)) {
                if (blocks.contains(next) && band.linked(pos, next)) {
                    linked.add(next);
                }
            }
            out.put(pos, linked);
        });
        return out;
    }

    /**
     * The 2-core of the groove blocks: the blocks left after dead ends are pruned again and again.
     * Only the 2-core holds cycles. The pruned blocks hang as trees and stay.
     */
    public static LongSet core(Band band) {
        LongSet blocks = blocks(band);
        Long2ObjectOpenHashMap<LongList> links = links(band, blocks);
        Long2IntOpenHashMap degree = new Long2IntOpenHashMap(blocks.size());
        LongArrayList stack = new LongArrayList();
        blocks.forEach(pos -> {
            int d = links.get(pos).size();
            degree.put(pos, d);
            if (d <= 1) {
                stack.add(pos);
            }
        });
        LongSet core = new LongOpenHashSet(blocks);
        while (!stack.isEmpty()) {
            long pos = stack.popLong();
            if (!core.remove(pos)) {
                continue;
            }
            for (long next : links.get(pos)) {
                if (core.contains(next) && degree.addTo(next, -1) == 2) {
                    stack.add(next);
                }
            }
        }
        return core;
    }

    /** The core blocks that link a groove outside the core. A removal there strands that groove. */
    public static LongSet attachments(Band band, LongSet core) {
        LongSet out = new LongOpenHashSet();
        core.forEach(pos -> {
            for (long next : band.neighbors(pos)) {
                if (!core.contains(next) && band.get(next) == BandKind.GROOVE && band.linked(pos, next)) {
                    out.add(pos);
                    return;
                }
            }
        });
        return out;
    }

    /** The number of independent cycles of the groove blocks: links minus blocks plus components. 0 means a forest. */
    public static int cycleRank(Band band) {
        LongSet blocks = blocks(band);
        Long2ObjectOpenHashMap<LongList> links = links(band, blocks);
        int linkEnds = 0;
        for (LongList linked : links.values()) {
            linkEnds += linked.size();
        }
        int components = 0;
        LongSet seen = new LongOpenHashSet();
        LongArrayList stack = new LongArrayList();
        for (long start : blocks) {
            if (!seen.add(start)) {
                continue;
            }
            components++;
            stack.add(start);
            while (!stack.isEmpty()) {
                for (long next : links.get(stack.popLong())) {
                    if (seen.add(next)) {
                        stack.add(next);
                    }
                }
            }
        }
        return linkEnds / 2 - blocks.size() + components;
    }

}
