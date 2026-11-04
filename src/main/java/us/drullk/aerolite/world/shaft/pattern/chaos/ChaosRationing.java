package us.drullk.aerolite.world.shaft.pattern.chaos;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.GrooveEdge;
import us.drullk.aerolite.world.shaft.surface.path.GrooveGraph;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The loop share rationing of chaos.
 *
 * <p>Each edge of the groove graph splits where its chain enters another chunk. The split block becomes a vertex, so each edge spans 2 chunks at most.
 * A removable block is a chain block that no surface path pins. The blocks away from a junction and from the box go first.
 * The library samples the segments, with several components per tree segment.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ChaosRationing {

    private final Band band;
    private final LongSet pins;

    ChaosRationing(Band band, LongSet pins) {
        this.band = band;
        this.pins = pins;
    }

    /** Rations the segments {@code [first, end)} and writes each removed block as wall. */
    LoopRationing.Result ration(int first, int end, ChaosSeeds seeds, double share) {
        GrooveGraph graph = splitAtChunks(GrooveGraph.unsplit(this.band));
        LoopRationing.Result result = LoopRationing.sample(graph, first, end, seeds::segment, share, this.removable(), LoopRationing.DEFAULT_LOOK);
        LoopRationing.apply(this.band, result);
        return result;
    }

    /** The graph with each edge cut at each chain block that enters another chunk than the block before it. */
    static GrooveGraph splitAtChunks(GrooveGraph graph) {
        LongSet vertices = new LongOpenHashSet(graph.vertices());
        TreeSet<GrooveEdge> edges = new TreeSet<>(GrooveEdge.SORT);
        for (GrooveEdge edge : graph.edges()) {
            long u = edge.u();
            LongList chain = new LongArrayList();
            for (long block : edge.chain()) {
                if (LoopRationing.segmentOf(block) != LoopRationing.segmentOf(u)) {
                    vertices.add(block);
                    edges.add(GrooveEdge.canonical(u, block, chain));
                    u = block;
                    chain = new LongArrayList();
                } else {
                    chain.add(block);
                }
            }
            edges.add(GrooveEdge.canonical(u, edge.v(), chain));
        }
        LongList sorted = new LongArrayList(vertices);
        sorted.sort(null);
        return new GrooveGraph(sorted, new ArrayList<>(edges));
    }

    /** The groove blocks with 3 or more groove neighbors. */
    private LongSet junctions() {
        LongSet out = new LongOpenHashSet();
        for (long pos : this.band.blocksOf(BandKind.GROOVE)) {
            int degree = 0;
            for (long q : this.band.neighbors(pos)) {
                if (this.band.get(q) == BandKind.GROOVE) {
                    degree++;
                }
            }
            if (degree >= 3) {
                out.add(pos);
            }
        }
        return out;
    }

    /** The chain blocks that no path pins, the blocks without a junction or box neighbor first. */
    private Function<GrooveEdge, LongList> removable() {
        LongSet joints = this.junctions();
        return edge -> {
            LongList free = new LongArrayList();
            LongList calm = new LongArrayList();
            for (long pos : edge.chain()) {
                if (this.pins.contains(pos)) {
                    continue;
                }
                free.add(pos);
                boolean nearJoint = false;
                for (long q : this.band.neighbors(pos)) {
                    nearJoint |= joints.contains(q) || this.band.get(q) == BandKind.BOX;
                }
                if (!nearJoint) {
                    calm.add(pos);
                }
            }
            return calm.isEmpty() ? free : calm;
        };
    }

}
