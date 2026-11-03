package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * The loop share rationing of a groove graph, segment by segment, so each segment is a pure function of its index.
 *
 * <p>A segment is one chunk, {@value #SEGMENT_LENGTH} blocks along the axis. A vertex belongs to the segment of its axis position.
 * Even segments are tree segments. Odd segments are connector segments.
 * A tree segment owns the edges between its own vertices. A connector segment also owns the edges to its two neighbor tree segments.
 *
 * <p>A tree segment can hold several components. A connector sees each component of the tree segment below it as one virtual node.
 * It sees two components of the tree segment above it as one virtual node when the full graph of the next
 * {@code look} tree segments above joins them. So no cycle runs down through one connector of a tree segment and back up
 * through the other, while that cycle spans {@code look} tree segments above the connector or fewer.
 * With one component per tree segment, the look has no effect.
 *
 * <p>Each segment samples a spanning forest with Wilson's algorithm. The other edges of the segment are its cycle edges.
 * A shuffle sets their sequence. The segment keeps {@code round(share * count)} of them and removes one block from each other one.
 * An edge with no removable block always stays. The draws do not depend on the share, so the removal of a share
 * holds the removal of each higher share.
 *
 * <p>The Python prototype draws from {@code random.Random}. This class draws from {@link RandomSource} with the same
 * draw structure: one {@code nextInt} per walk step, one per shuffle swap from the last index down, one per removed block pick.
 * The values differ from the prototype. The virtual nodes sort by their lowest vertex position here, so the virtual
 * nodes below come before the own vertices and the virtual nodes above come after them. The prototype puts the own vertices first.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class LoopRationing {

    /** The blocks of one segment along the axis. */
    public static final int SEGMENT_LENGTH = 16;

    /** The tree segments above a connector whose joins the connector sees. */
    public static final int DEFAULT_LOOK = 2;

    private LoopRationing() {
    }

    /**
     * The result of a rationing.
     *
     * @param graph the groove graph that the rationing sampled
     * @param removed the removed blocks, segment by segment
     * @param cycles the cycle edges of all segments
     * @param kept the cycle edges that stay
     * @param unbreakable the cycle edges without a removable block; a valid pattern has none
     */
    public record Result(GrooveGraph graph, LongList removed, int cycles, int kept, int unbreakable) {
        public Result {
            removed = LongLists.unmodifiable(new LongArrayList(removed));
        }
    }

    /**
     * The chunks of margin that a window needs on each side, so the rationing of each chunk of the window is the same for any window.
     * A pattern adds the reach of its own layout and surface paths.
     */
    public static int margin(int look) {
        return 2 * look + 4;
    }

    /** The segment of the position. */
    public static int segmentOf(long pos) {
        return Math.floorDiv(BandPos.axis(pos), SEGMENT_LENGTH);
    }

    /** The segment that owns an edge between two segments: the shared one, else the odd connector. */
    public static int owner(int a, int b) {
        if (a == b) {
            return a;
        }
        if (Math.abs(a - b) != 1) {
            throw new IllegalArgumentException("An edge spans the segments " + a + " and " + b);
        }
        return (a & 1) != 0 ? a : b;
    }

    /**
     * The removable blocks of each edge: the chain blocks that are not fixed and hold no attachment, blocks off the groove nodes first.
     * An attachment block is a 2-core groove that links a groove outside the 2-core. Its removal strands that groove.
     *
     * @param fixed the blocks that never go, for example the pinned end grooves of surface paths and the tail blocks
     */
    public static Function<GrooveEdge, LongList> removable(Band band, LongSet fixed) {
        LongSet never = new LongOpenHashSet(fixed);
        never.addAll(Grooves.attachments(band, Grooves.core(band)));
        LongSet nodes = new LongOpenHashSet(band.grooveNodes());
        return edge -> {
            LongList free = new LongArrayList();
            LongList calm = new LongArrayList();
            for (long pos : edge.chain()) {
                if (!never.contains(pos)) {
                    free.add(pos);
                    if (!nodes.contains(pos)) {
                        calm.add(pos);
                    }
                }
            }
            return calm.isEmpty() ? free : calm;
        };
    }

    /**
     * Builds the groove graph of the band, rations it with {@link #removable} and writes each removed block as wall.
     *
     * @param firstSegment the first segment to sample
     * @param endSegment the first segment after the sampled ones
     * @param randomFor the random of each segment, a pure function of the seed, the pillar and the segment
     * @param share the loop share, from 0 to 1
     */
    public static Result ration(Band band, LongSet fixed, int firstSegment, int endSegment, IntFunction<RandomSource> randomFor, double share) {
        Result result = sample(GrooveGraph.of(band), firstSegment, endSegment, randomFor, share, removable(band, fixed), DEFAULT_LOOK);
        apply(band, result);
        return result;
    }

    /** Writes each removed block inside the band as wall. */
    public static void apply(Band band, Result result) {
        for (long pos : result.removed()) {
            if (band.inside(pos)) {
                band.set(pos, BandKind.WALL);
            }
        }
    }

    /** The rationing with the default look. */
    public static Result sample(GrooveGraph graph, int firstSegment, int endSegment, IntFunction<RandomSource> randomFor, double share,
                                Function<GrooveEdge, LongList> removable) {
        return sample(graph, firstSegment, endSegment, randomFor, share, removable, DEFAULT_LOOK);
    }

    /**
     * Rations the cycles of the groove graph segment by segment.
     *
     * @param graph a groove graph whose edges each span one segment or two neighbor segments
     * @param look the tree segments above a connector whose joins the connector sees; 0 sees only the next tree segment
     */
    public static Result sample(GrooveGraph graph, int firstSegment, int endSegment, IntFunction<RandomSource> randomFor, double share,
                                Function<GrooveEdge, LongList> removable, int look) {
        if (!(share >= 0 && share <= 1)) {
            throw new IllegalArgumentException("The loop share is not between 0 and 1: " + share);
        }
        List<GrooveEdge> edges = graph.edges();
        Int2ObjectOpenHashMap<IntList> own = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            GrooveEdge edge = edges.get(i);
            own.computeIfAbsent(owner(segmentOf(edge.u()), segmentOf(edge.v())), s -> new IntArrayList()).add(i);
        }
        Int2ObjectOpenHashMap<LongList> verticesOf = new Int2ObjectOpenHashMap<>();
        for (long vertex : graph.vertices()) {
            verticesOf.computeIfAbsent(segmentOf(vertex), s -> new LongArrayList()).add(vertex);
        }
        LongList removed = new LongArrayList();
        int cycles = 0;
        int kept = 0;
        int unbreakable = 0;
        for (int s = firstSegment; s < endSegment; s++) {
            RandomSource random = randomFor.apply(s);
            boolean connector = (s & 1) != 0;
            LongUnionFind below = connector ? components(graph, own, verticesOf, s - 1, s - 1) : new LongUnionFind();
            LongUnionFind above = connector ? components(graph, own, verticesOf, s + 1, s + 1 + 2 * look) : new LongUnionFind();
            int segment = s;
            KeyOf key = vertex -> {
                int t = segmentOf(vertex);
                if (t == segment) {
                    return vertex;
                }
                return t < segment ? below.find(vertex) : above.find(vertex);
            };
            LongUnionFind uf = new LongUnionFind();
            for (long vertex : verticesOf.getOrDefault(s, LongLists.EMPTY_LIST)) {
                uf.find(key.of(vertex));
            }
            IntList owned = own.getOrDefault(s, IntList.of());
            List<LongList> options = new ArrayList<>(owned.size());
            IntList free = new IntArrayList();
            for (int j = 0; j < owned.size(); j++) {
                GrooveEdge edge = edges.get(owned.getInt(j));
                long ku = key.of(edge.u());
                long kv = key.of(edge.v());
                uf.find(ku);
                uf.find(kv);
                LongList blocks = removable.apply(edge);
                options.add(blocks);
                if (!blocks.isEmpty()) {
                    free.add(j);
                } else if (!uf.union(ku, kv)) {
                    unbreakable++;
                }
            }
            LongList us = new LongArrayList(free.size());
            LongList vs = new LongArrayList(free.size());
            for (int f = 0; f < free.size(); f++) {
                GrooveEdge edge = edges.get(owned.getInt(free.getInt(f)));
                us.add(uf.find(key.of(edge.u())));
                vs.add(uf.find(key.of(edge.v())));
            }
            LongSet rootSet = new LongOpenHashSet();
            for (long item : uf.items()) {
                rootSet.add(uf.find(item));
            }
            LongList nodes = new LongArrayList(rootSet);
            nodes.sort(null);
            IntSet tree = SpanningForest.sample(nodes, us, vs, random);
            IntList cotree = new IntArrayList();
            for (int f = 0; f < free.size(); f++) {
                if (!tree.contains(f)) {
                    cotree.add(free.getInt(f));
                }
            }
            shuffle(cotree, random);
            LongList picks = new LongArrayList(cotree.size());
            for (int c = 0; c < cotree.size(); c++) {
                LongList blocks = options.get(cotree.getInt(c));
                picks.add(blocks.getLong(random.nextInt(blocks.size())));
            }
            int keep = (int) Math.floor(share * cotree.size() + 0.5);
            cycles += cotree.size();
            kept += keep;
            removed.addAll(picks.subList(keep, picks.size()));
        }
        return new Result(graph, removed, cycles, kept, unbreakable);
    }

    @FunctionalInterface
    private interface KeyOf {
        long of(long vertex);
    }

    /** The components of the vertices of the segments {@code [first, last]}, joined by the edges that those segments own inside the range. */
    private static LongUnionFind components(GrooveGraph graph, Int2ObjectOpenHashMap<IntList> own, Int2ObjectOpenHashMap<LongList> verticesOf,
                                            int first, int last) {
        LongUnionFind uf = new LongUnionFind();
        for (int t = first; t <= last; t++) {
            for (long vertex : verticesOf.getOrDefault(t, LongLists.EMPTY_LIST)) {
                uf.find(vertex);
            }
            for (int i : own.getOrDefault(t, IntList.of())) {
                GrooveEdge edge = graph.edges().get(i);
                int su = segmentOf(edge.u());
                int sv = segmentOf(edge.v());
                if (su >= first && su <= last && sv >= first && sv <= last) {
                    uf.union(edge.u(), edge.v());
                }
            }
        }
        return uf;
    }

    /** The Fisher-Yates shuffle from the last index down, one {@code nextInt} per swap. */
    static void shuffle(IntList list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int a = list.getInt(i);
            list.set(i, list.getInt(j));
            list.set(j, a);
        }
    }

}
