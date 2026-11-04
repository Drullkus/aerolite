package us.drullk.aerolite.world.shaft.pattern.chaos;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandDirection;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.BandPos;
import us.drullk.aerolite.world.shaft.surface.path.Lattice;
import us.drullk.aerolite.world.shaft.surface.path.PathFit;
import us.drullk.aerolite.world.shaft.surface.path.SurfaceCandidates;
import us.drullk.aerolite.world.shaft.surface.path.SurfacePath;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The surface paths of chaos: at most one on each face, each from the random of its face, drawn from the band before any surface path.
 *
 * <p>A path enters and leaves through a groove, orthogonally. Its end groove sits in a straight run of 3 grooves across the path,
 * and no block of that run is a junction. The end groove belongs to a chain whose two ends lie more along the run than across it.
 * The parity of the end groove picks the one side that a path can come from. A two-turn path runs its middle leg perpendicular to a third edge
 * of the face with 3 or more blocks.
 *
 * <p>The strip rule: no wall strip under 2 blocks runs beside the path, and each part of the face beside the path keeps a 2x2 wall square.
 * The narrow-stone fallback: a face without a candidate under those rules can take a straight path with a strip of 1 block on each side.
 * A cut path never takes the fallback.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ChaosPaths {

    /** The grooves on each side of an end groove in its straight run. */
    private static final int END_RUN = 1;

    private enum ChainClass {
        RING,
        AXIS
    }

    /** One groove chain: its two ends, its block count and the direction of the larger extent between the ends. */
    private record Chain(long first, long last, int blocks, @Nullable ChainClass direction) {
    }

    private final Band band;
    private final ChaosParams params;
    private final ChaosSeeds seeds;
    private final PathFit.Rules rules;
    private final Long2ObjectOpenHashMap<Chain> chains = new Long2ObjectOpenHashMap<>();
    private final LongSet noChain = new LongOpenHashSet();

    ChaosPaths(Band band, ChaosParams params, ChaosSeeds seeds) {
        this.band = band;
        this.params = params;
        this.seeds = seeds;
        this.rules = new PathFit.Rules(params.cut(), false, false, LongSets.EMPTY_SET);
    }

    /** One surface path per face with the path chance, for the faces with a block in the axis range {@code [lo, hi)}. */
    List<SurfacePath> choose(int lo, int hi) {
        List<SurfacePath> out = new ArrayList<>();
        for (LongSet region : this.faces(lo, hi)) {
            long anchor = Long.MAX_VALUE;
            for (long pos : region) {
                anchor = Math.min(anchor, pos);
            }
            RandomSource random = this.seeds.path(BandPos.ring(anchor), BandPos.axis(anchor));
            if (random.nextDouble() >= this.params.chance()) {
                continue;
            }
            SurfacePath pick = this.pick(region, random);
            if (pick != null) {
                out.add(pick);
            }
        }
        return out;
    }

    /** The wall faces that do not touch the band ends and hold a block in {@code [lo, hi)}, sorted by their first block. */
    private List<LongSet> faces(int lo, int hi) {
        List<LongSet> faces = new ArrayList<>();
        LongSet seen = new LongOpenHashSet();
        for (long start : this.band.blocksOf(BandKind.WALL)) {
            if (!seen.add(start)) {
                continue;
            }
            LongSet region = new LongOpenHashSet();
            region.add(start);
            LongArrayList stack = LongArrayList.of(start);
            int minAxis = BandPos.axis(start);
            int maxAxis = minAxis;
            while (!stack.isEmpty()) {
                long pos = stack.popLong();
                for (long next : this.band.neighbors(pos)) {
                    if (this.band.get(next) == BandKind.WALL && seen.add(next)) {
                        region.add(next);
                        stack.add(next);
                        minAxis = Math.min(minAxis, BandPos.axis(next));
                        maxAxis = Math.max(maxAxis, BandPos.axis(next));
                    }
                }
            }
            boolean touchesEnd = minAxis == this.band.axis0() || maxAxis == this.band.axisEnd() - 1;
            if (!touchesEnd && maxAxis >= lo && minAxis < hi) {
                faces.add(region);
            }
        }
        return faces;
    }

    @Nullable
    private SurfacePath pick(LongSet region, RandomSource random) {
        Set<Chain> edges = new HashSet<>();
        for (long pos : region) {
            for (long q : this.band.neighbors(pos)) {
                if (this.band.get(q) == BandKind.GROOVE) {
                    Chain chain = this.chain(q);
                    if (chain != null) {
                        edges.add(chain);
                    }
                }
            }
        }
        List<List<SurfacePath>> classes = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Set<List<LongList>> seen = new HashSet<>();
        for (int parity = 0; parity < 4; parity++) {
            int pr = parity & 1;
            int pa = parity >> 1;
            LongSet nodes = new LongOpenHashSet();
            for (long pos : region) {
                if ((BandPos.ring(pos) & 1) == pr && Math.floorMod(BandPos.axis(pos), 2) == pa) {
                    nodes.add(pos);
                }
            }
            for (SurfacePath candidate : this.candidates(region, nodes)) {
                List<LongList> key = List.of(candidate.blocks(), candidate.ends());
                if (!seen.contains(key) && this.pathOk(candidate, edges)) {
                    seen.add(key);
                    classes.get(candidate.turns()).add(candidate);
                }
            }
        }
        boolean[] modes = this.params.cut() ? new boolean[] {false} : new boolean[] {false, true};
        for (boolean narrow : modes) {
            List<Integer> turns = new ArrayList<>();
            for (int k = 0; k <= SurfaceCandidates.MAX_TURNS; k++) {
                if (!classes.get(k).isEmpty() && (k == 0 || !narrow)) {
                    turns.add(k);
                }
            }
            shuffle(turns, random);
            for (int k : turns) {
                List<SurfacePath> pool = new ArrayList<>(classes.get(k));
                pool.sort(SurfacePath.SORT);
                shuffle(pool, random);
                for (SurfacePath candidate : pool) {
                    if (PathFit.fits(this.band, candidate, this.rules) && this.geometryOk(candidate, region, narrow)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The candidates of {@link SurfaceCandidates#of} that end on a groove at both ends, where both ends keep the end rule.
     * The walk follows the library walk, so the first path found for each block set and end set is the same path.
     */
    private List<SurfacePath> candidates(LongSet region, LongSet nodes) {
        Long2ObjectOpenHashMap<List<Lattice.Link>> links = Lattice.of(this.band, region, nodes);
        Map<List<LongList>, SurfacePath> found = new HashMap<>();
        LongList sorted = new LongArrayList(nodes);
        sorted.sort(null);
        for (long node : sorted) {
            for (Lattice.Link link : links.get(node)) {
                if (link.exit() && this.endOk(link.target(), link.direction())) {
                    LongList blocks = new LongArrayList();
                    for (int i = link.run().size() - 1; i >= 0; i--) {
                        blocks.add(link.run().getLong(i));
                    }
                    blocks.add(node);
                    LongSet visited = new LongOpenHashSet();
                    visited.add(node);
                    this.walk(links, found, node, link.direction().opposite(), blocks, visited, 0, link.target());
                }
            }
        }
        List<SurfacePath> out = new ArrayList<>(found.values());
        out.sort(SurfacePath.SORT);
        return out;
    }

    private void walk(Long2ObjectOpenHashMap<List<Lattice.Link>> links, Map<List<LongList>, SurfacePath> found, long node, BandDirection in,
                      LongList blocks, LongSet visited, int turns, long start) {
        for (Lattice.Link link : links.get(node)) {
            if (link.direction() == in.opposite()) {
                continue;
            }
            int nextTurns = turns + (link.direction() != in ? 1 : 0);
            if (nextTurns > SurfaceCandidates.MAX_TURNS) {
                continue;
            }
            LongList next = new LongArrayList(blocks);
            next.addAll(link.run());
            if (link.exit()) {
                if (this.endOk(link.target(), link.direction())) {
                    LongList ends = LongList.of(start, link.target());
                    LongList sortedBlocks = new LongArrayList(next);
                    sortedBlocks.sort(null);
                    LongList sortedEnds = new LongArrayList(ends);
                    sortedEnds.sort(null);
                    found.computeIfAbsent(List.of(sortedBlocks, sortedEnds), k -> new SurfacePath(next, ends, nextTurns));
                }
            } else if (!visited.contains(link.target())) {
                next.add(link.target());
                LongSet nextVisited = new LongOpenHashSet(visited);
                nextVisited.add(link.target());
                this.walk(links, found, link.target(), link.direction(), next, nextVisited, nextTurns, start);
            }
        }
    }

    /** The Fisher-Yates shuffle from the last index down, one {@code nextInt} per swap. */
    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, random.nextInt(i + 1));
        }
    }

    private boolean groove(long pos) {
        return this.band.inside(pos) && this.band.get(pos) == BandKind.GROOVE;
    }

    /** The groove neighbors of a block. The chaos band has no corner, so every neighbor links. */
    private LongList links(long pos) {
        LongList out = new LongArrayList(4);
        for (long q : this.band.neighbors(pos)) {
            if (this.band.get(q) == BandKind.GROOVE && this.band.linked(pos, q)) {
                out.add(q);
            }
        }
        return out;
    }

    /** The chain through a groove with two groove neighbors, or null for any other groove. */
    @Nullable
    private Chain chain(long start) {
        Chain known = this.chains.get(start);
        if (known != null || this.noChain.contains(start)) {
            return known;
        }
        LongList first = this.links(start);
        if (first.size() != 2) {
            this.noChain.add(start);
            return null;
        }
        LongList blocks = new LongArrayList();
        blocks.add(start);
        long[] ends = new long[2];
        for (int side = 0; side < 2; side++) {
            long previous = start;
            long current = first.getLong(side);
            while (current != start) {
                LongList next = this.links(current);
                if (next.size() != 2) {
                    break;
                }
                blocks.add(current);
                long step = next.getLong(1) == previous ? next.getLong(0) : next.getLong(1);
                previous = current;
                current = step;
            }
            ends[side] = current;
        }
        long a = Math.min(ends[0], ends[1]);
        long b = Math.max(ends[0], ends[1]);
        int ring = this.band.ringLength();
        int dr = Math.abs(Math.floorMod(BandPos.ring(b) - BandPos.ring(a) + ring / 2, ring) - ring / 2);
        int da = Math.abs(BandPos.axis(b) - BandPos.axis(a));
        ChainClass direction = dr > da ? ChainClass.RING : da > dr ? ChainClass.AXIS : null;
        Chain chain = new Chain(a, b, blocks.size(), direction);
        for (long pos : blocks) {
            this.chains.put(pos, chain);
        }
        return chain;
    }

    /** True when a path can end at the groove, arriving with the step. */
    private boolean endOk(long end, BandDirection arrival) {
        boolean across = arrival.isAxis();
        int tr = across ? 1 : 0;
        int ta = across ? 0 : 1;
        boolean positive = arrival == BandDirection.RING_PLUS || arrival == BandDirection.AXIS_PLUS;
        if (positive != (Math.floorMod(BandPos.ring(end) + BandPos.axis(end), 2) == 0)) {
            return false;
        }
        long[] run = new long[2 * END_RUN + 1];
        for (int k = -END_RUN; k <= END_RUN; k++) {
            run[k + END_RUN] = this.band.pos(BandPos.ring(end) + k * tr, BandPos.axis(end) + k * ta);
            if (!this.groove(run[k + END_RUN])) {
                return false;
            }
        }
        for (int k = 1; k < run.length - 1; k++) {
            if (this.links(run[k]).size() != 2) {
                return false;
            }
        }
        if (this.links(run[0]).size() > 2 || this.links(run[run.length - 1]).size() > 2) {
            return false;
        }
        Chain chain = this.chain(end);
        return chain != null && chain.direction() == (across ? ChainClass.RING : ChainClass.AXIS);
    }

    /** The chaos rules of one candidate, before the fit check of the library. */
    private boolean pathOk(SurfacePath path, Set<Chain> edges) {
        if (!path.isThrough()) {
            return false;
        }
        long g1 = path.ends().getLong(0);
        long g2 = path.ends().getLong(1);
        LongList blocks = path.blocks();
        BandDirection in = this.band.direction(blocks.getLong(0), g1);
        BandDirection out = this.band.direction(blocks.getLong(blocks.size() - 1), g2);
        if (in == null || out == null || !this.endOk(g1, in) || !this.endOk(g2, out)) {
            return false;
        }
        if (path.turns() == 2) {
            BandDirection firstLeg = this.band.direction(g1, blocks.getLong(0));
            ChainClass want = firstLeg != null && firstLeg.isAxis() ? ChainClass.AXIS : ChainClass.RING;
            Chain c1 = this.chain(g1);
            Chain c2 = this.chain(g2);
            for (Chain edge : edges) {
                if (!edge.equals(c1) && !edge.equals(c2) && edge.direction() == want && edge.blocks() >= 3) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * True when no wall strip under 2 blocks runs beside the path, and each part of the face beside the path keeps a 2x2 square.
     * With {@code narrow}, a straight path can leave a strip of 1 block on each side and parts without a square.
     */
    private boolean geometryOk(SurfacePath path, LongSet region, boolean narrow) {
        LongSet own = new LongOpenHashSet(path.blocks());
        LongSet sequence = new LongOpenHashSet(own);
        sequence.addAll(path.ends());
        for (long pos : path.blocks()) {
            for (BandDirection d : BandDirection.values()) {
                long q = this.band.pos(BandPos.ring(pos) + d.ring(), BandPos.axis(pos) + d.axis());
                if (sequence.contains(q)) {
                    continue;
                }
                if (!this.band.inside(q) || this.band.get(q) != BandKind.WALL) {
                    return false;
                }
                long q2 = this.band.pos(BandPos.ring(pos) + 2 * d.ring(), BandPos.axis(pos) + 2 * d.axis());
                if (own.contains(q2) || (this.band.inside(q2) && this.band.get(q2) != BandKind.WALL && !narrow)) {
                    return false;
                }
            }
        }
        if (narrow) {
            return true;
        }
        LongSet rest = new LongOpenHashSet(region);
        rest.removeAll(own);
        LongSet done = new LongOpenHashSet();
        for (long start : rest) {
            if (done.contains(start)) {
                continue;
            }
            LongSet part = new LongOpenHashSet();
            part.add(start);
            LongArrayList stack = LongArrayList.of(start);
            while (!stack.isEmpty()) {
                long pos = stack.popLong();
                for (BandDirection d : BandDirection.values()) {
                    long q = this.band.pos(BandPos.ring(pos) + d.ring(), BandPos.axis(pos) + d.axis());
                    if (rest.contains(q) && part.add(q)) {
                        stack.add(q);
                    }
                }
            }
            done.addAll(part);
            boolean square = false;
            for (long pos : part) {
                int r = BandPos.ring(pos);
                int a = BandPos.axis(pos);
                if (part.contains(this.band.pos(r + 1, a)) && part.contains(this.band.pos(r, a + 1)) && part.contains(this.band.pos(r + 1, a + 1))) {
                    square = true;
                    break;
                }
            }
            if (!square) {
                return false;
            }
        }
        return true;
    }

}
