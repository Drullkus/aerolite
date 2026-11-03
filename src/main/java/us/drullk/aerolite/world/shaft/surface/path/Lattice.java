package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.List;

/**
 * The straight links of each face node inside one region.
 *
 * <p>For each node and direction, the run of region blocks ends on another node (a link) or on a groove block outside the region (an exit).
 * A corner node has no axis link.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class Lattice {

    private Lattice() {
    }

    /**
     * One straight link of a node.
     *
     * @param direction the direction from the node
     * @param run the region blocks between the node and the target, from the node outward
     * @param target the next node, or the groove of an exit
     * @param exit true when the target is a groove outside the region
     */
    public record Link(BandDirection direction, LongList run, long target, boolean exit) {
        public Link {
            run = LongLists.unmodifiable(new LongArrayList(run));
        }
    }

    /** Each node mapped to its links, in the sequence of {@link BandDirection}. */
    public static Long2ObjectOpenHashMap<List<Link>> of(Band band, LongSet region, LongSet nodes) {
        Long2ObjectOpenHashMap<List<Link>> out = new Long2ObjectOpenHashMap<>(nodes.size());
        nodes.forEach(node -> {
            List<Link> links = new ArrayList<>(4);
            for (BandDirection direction : BandDirection.values()) {
                if (direction.isAxis() && band.isCorner(node)) {
                    continue;
                }
                LongList run = new LongArrayList();
                long q = band.step(node, direction);
                // A run around a ring without a node or an exit stops after one turn of the ring.
                while (q != BandPos.NONE && run.size() < band.ringLength()) {
                    if (nodes.contains(q)) {
                        links.add(new Link(direction, run, q, false));
                        break;
                    }
                    if (!region.contains(q)) {
                        if (band.get(q) == BandKind.GROOVE) {
                            links.add(new Link(direction, run, q, true));
                        }
                        break;
                    }
                    run.add(q);
                    q = band.step(q, direction);
                }
            }
            out.put(node, links);
        });
        return out;
    }

}
