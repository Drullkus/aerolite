package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every surface path of one region.
 *
 * <p>A path leaves a groove through an exit of the {@link Lattice}, passes face nodes, and ends on a groove or on a face node.
 * It has at most two corners: an I has none, an L has one, an S or a U has two.
 * The candidates hold every U. {@link PathFit} rejects a U whose legs touch, because a leg block then touches a path block that is not its neighbor in the path.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class SurfaceCandidates {

    /** The most corners of a surface path. */
    public static final int MAX_TURNS = 2;

    private SurfaceCandidates() {
    }

    /** Every surface path of the region with up to {@link #MAX_TURNS} corners, sorted by {@link SurfacePath#SORT}, without duplicates. */
    public static List<SurfacePath> of(Band band, LongSet region, LongSet nodes) {
        return of(band, region, nodes, MAX_TURNS);
    }

    /**
     * Every surface path of the region with up to {@code maxTurns} corners, sorted by {@link SurfacePath#SORT}.
     * Two paths with the same block set and the same end set count as one. The first one found stays.
     */
    public static List<SurfacePath> of(Band band, LongSet region, LongSet nodes, int maxTurns) {
        Long2ObjectOpenHashMap<List<Lattice.Link>> links = Lattice.of(band, region, nodes);
        Map<Key, SurfacePath> found = new HashMap<>();
        Walker walker = new Walker(links, maxTurns, found);
        LongList sortedNodes = new LongArrayList(nodes);
        sortedNodes.sort(null);
        for (long node : sortedNodes) {
            for (Lattice.Link link : links.get(node)) {
                if (link.exit()) {
                    LongList blocks = new LongArrayList();
                    for (int i = link.run().size() - 1; i >= 0; i--) {
                        blocks.add(link.run().getLong(i));
                    }
                    blocks.add(node);
                    LongSet visited = new LongOpenHashSet();
                    visited.add(node);
                    walker.walk(node, link.direction().opposite(), blocks, visited, 0, link.target());
                }
            }
        }
        List<SurfacePath> out = new ArrayList<>(found.values());
        out.sort(SurfacePath.SORT);
        return out;
    }

    private record Key(LongList blocks, LongList ends) {
        static Key of(LongList blocks, LongList ends) {
            LongList b = new LongArrayList(blocks);
            b.sort(null);
            LongList e = new LongArrayList(ends);
            e.sort(null);
            return new Key(b, e);
        }
    }

    private record Walker(Long2ObjectOpenHashMap<List<Lattice.Link>> links, int maxTurns, Map<Key, SurfacePath> found) {

        void record(LongList blocks, LongList ends, int turns) {
            this.found.computeIfAbsent(Key.of(blocks, ends), k -> new SurfacePath(blocks, ends, turns));
        }

        void walk(long node, BandDirection in, LongList blocks, LongSet visited, int turns, long start) {
            this.record(blocks, LongList.of(start), turns);
            for (Lattice.Link link : this.links.get(node)) {
                if (link.direction() == in.opposite()) {
                    continue;
                }
                int nextTurns = turns + (link.direction() != in ? 1 : 0);
                if (nextTurns > this.maxTurns) {
                    continue;
                }
                LongList next = new LongArrayList(blocks);
                next.addAll(link.run());
                if (link.exit()) {
                    this.record(next, LongList.of(start, link.target()), nextTurns);
                } else if (!visited.contains(link.target())) {
                    next.add(link.target());
                    LongSet nextVisited = new LongOpenHashSet(visited);
                    nextVisited.add(link.target());
                    this.walk(link.target(), link.direction(), next, nextVisited, nextTurns, start);
                }
            }
        }
    }

}
