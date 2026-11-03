package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The groove graph of the 2-core of a band: vertices are junction blocks, edges are the block chains between them.
 *
 * <p>A vertex has 3 or more linked core neighbors. A core cycle without a junction gets its lowest block as vertex.
 * The vertices come sorted. The edges come sorted by {@link GrooveEdge#SORT}.
 *
 * <p>{@link #of} splits every edge whose end vertices lie more than one segment apart.
 * The split puts a vertex on each chain block that enters another segment.
 * So every edge joins two vertices of one segment or of two neighbor segments, as the loop rationing needs.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public record GrooveGraph(LongList vertices, List<GrooveEdge> edges) {

    public GrooveGraph {
        vertices = LongLists.unmodifiable(new LongArrayList(vertices));
        edges = List.copyOf(edges);
    }

    /** The groove graph of the band, with the long edges split at the segment boundaries. */
    public static GrooveGraph of(Band band) {
        return unsplit(band).splitLongEdges();
    }

    /** The groove graph of the band, without the split of long edges. */
    public static GrooveGraph unsplit(Band band) {
        LongSet core = Grooves.core(band);
        Long2ObjectOpenHashMap<LongList> links = Grooves.links(band, core);
        LongSet vertices = new LongOpenHashSet();
        core.forEach(pos -> {
            if (links.get(pos).size() >= 3) {
                vertices.add(pos);
            }
        });
        LongList sortedCore = new LongArrayList(core);
        sortedCore.sort(null);
        LongSet seen = new LongOpenHashSet();
        LongArrayList stack = new LongArrayList();
        for (long start : sortedCore) {
            if (seen.contains(start)) {
                continue;
            }
            long lowest = start;
            boolean hasVertex = false;
            seen.add(start);
            stack.add(start);
            while (!stack.isEmpty()) {
                long pos = stack.popLong();
                hasVertex |= vertices.contains(pos);
                lowest = Math.min(lowest, pos);
                for (long next : links.get(pos)) {
                    if (seen.add(next)) {
                        stack.add(next);
                    }
                }
            }
            if (!hasVertex) {
                vertices.add(lowest);
            }
        }
        TreeSet<GrooveEdge> edges = new TreeSet<>(GrooveEdge.SORT);
        for (long vertex : vertices) {
            for (long first : links.get(vertex)) {
                LongList chain = new LongArrayList();
                long previous = vertex;
                long current = first;
                while (!vertices.contains(current)) {
                    chain.add(current);
                    LongList pair = links.get(current);
                    long next = pair.getLong(0) != previous ? pair.getLong(0) : pair.getLong(1);
                    previous = current;
                    current = next;
                }
                edges.add(GrooveEdge.canonical(vertex, current, chain));
            }
        }
        LongList sortedVertices = new LongArrayList(vertices);
        sortedVertices.sort(null);
        return new GrooveGraph(sortedVertices, new ArrayList<>(edges));
    }

    /**
     * This graph with each edge whose end vertices lie more than one segment apart cut into pieces.
     * A long edge gets a new vertex at each chain block that enters another segment.
     */
    public GrooveGraph splitLongEdges() {
        LongSet vertices = new LongOpenHashSet(this.vertices);
        TreeSet<GrooveEdge> edges = new TreeSet<>(GrooveEdge.SORT);
        for (GrooveEdge edge : this.edges) {
            if (Math.abs(LoopRationing.segmentOf(edge.u()) - LoopRationing.segmentOf(edge.v())) <= 1) {
                edges.add(edge);
                continue;
            }
            LongList seq = new LongArrayList();
            seq.add(edge.u());
            seq.addAll(edge.chain());
            seq.add(edge.v());
            int start = 0;
            for (int i = 1; i < seq.size() - 1; i++) {
                if (LoopRationing.segmentOf(seq.getLong(i)) != LoopRationing.segmentOf(seq.getLong(i - 1))) {
                    vertices.add(seq.getLong(i));
                    edges.add(GrooveEdge.canonical(seq.getLong(start), seq.getLong(i), seq.subList(start + 1, i)));
                    start = i;
                }
            }
            edges.add(GrooveEdge.canonical(seq.getLong(start), seq.getLong(seq.size() - 1), seq.subList(start + 1, seq.size() - 1)));
        }
        LongList sortedVertices = new LongArrayList(vertices);
        sortedVertices.sort(null);
        return new GrooveGraph(sortedVertices, new ArrayList<>(edges));
    }

}
