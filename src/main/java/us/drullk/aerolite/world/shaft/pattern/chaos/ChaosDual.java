package us.drullk.aerolite.world.shaft.pattern.chaos;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Delaunay grooves of one window: a center ring in each stone, and one straight groove between the centers of two neighbor stones.
 *
 * <p>Two stones share a border when 2 or more neighbor block pairs join them. A ring goes in where its square and the blocks around it
 * hold no groove and no box, and the local rules hold. A line runs from center to center and loses its blocks inside the two ring squares.
 * The lines go from the shortest to the longest. Each end of a line stops where the line first touches the grooves.
 * A line stays only when the local rules hold after it. The plus crossing repair splits a plain center after all lines.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ChaosDual {

    private final ChaosParams params;
    private final ChaosSeeds seeds;
    private final ChaosStones stones;
    private final boolean[] box;
    private final int ring;
    private final int rows;
    private final int axis0;

    ChaosDual(ChaosParams params, ChaosSeeds seeds, ChaosStones stones, boolean[] box) {
        this.params = params;
        this.seeds = seeds;
        this.stones = stones;
        this.box = box;
        this.ring = stones.ring();
        this.rows = stones.rows();
        this.axis0 = stones.axis0();
    }

    private record Line(int length, long key, IntList blocks, IntSet plain) {
    }

    /** The grooves of the dual of the settled labels. */
    GrooveGrid build(int[] lab) {
        boolean[] stoneMask = new boolean[lab.length];
        for (int i = 0; i < lab.length; i++) {
            stoneMask[i] = lab[i] >= 0;
        }
        int[] comp = this.stones.components(stoneMask, lab);
        IntSet skip = new IntOpenHashSet();
        for (int r = 0; r < this.ring; r++) {
            skip.add(comp[this.stones.idx(r, 0)]);
            skip.add(comp[this.stones.idx(r, this.rows - 1)]);
        }
        Long2IntLinkedOpenHashMap pairs = new Long2IntLinkedOpenHashMap();
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                int a = comp[this.stones.idx(r, y)];
                if (a < 0) {
                    continue;
                }
                int right = comp[this.stones.idx(r + 1, y)];
                if (right >= 0 && right != a) {
                    pairs.addTo(pairKey(a, right), 1);
                }
                if (y + 1 < this.rows) {
                    int down = comp[this.stones.idx(r, y + 1)];
                    if (down >= 0 && down != a) {
                        pairs.addTo(pairKey(a, down), 1);
                    }
                }
            }
        }
        IntSet idSet = new IntOpenHashSet();
        for (Long2IntLinkedOpenHashMap.Entry entry : pairs.long2IntEntrySet()) {
            if (entry.getIntValue() >= 2) {
                idSet.add((int) (entry.getLongKey() >>> 32));
                idSet.add((int) entry.getLongKey());
            }
        }
        idSet.removeAll(skip);
        IntList ids = new IntArrayList(idSet);
        ids.sort(null);
        Int2ObjectOpenHashMap<int[]> hub = this.centroids(comp, ids);

        GrooveGrid grid = new GrooveGrid(this.ring, this.rows, this.axis0, new boolean[lab.length], this.box);
        boolean[] g = grid.groove;
        int side = this.params.ringSide();
        Int2ObjectOpenHashMap<IntSet> squaresOf = new Int2ObjectOpenHashMap<>();
        List<int[]> ringSequence = new ArrayList<>();
        for (int id : ids) {
            int[] h = hub.get(id);
            ringSequence.add(new int[] {id, h[0], h[1]});
        }
        ringSequence.sort(Comparator.<int[]>comparingLong(t -> this.seeds.ringKey(t[1], t[2] + this.axis0)).thenComparingInt(t -> t[0]));
        for (int[] t : ringSequence) {
            int[] h = hub.get(t[0]);
            int x0 = h[0] - (side - 1) / 2;
            int y0 = h[1] - (side - 1) / 2;
            IntSet square = new IntOpenHashSet();
            IntList edge = new IntArrayList();
            boolean ok = true;
            for (int i = 0; i < side && ok; i++) {
                for (int j = 0; j < side && ok; j++) {
                    int y = y0 + j;
                    if (y < 1 || y >= this.rows - 1) {
                        ok = false;
                        break;
                    }
                    int p = this.stones.idx(x0 + i, y);
                    ok = !g[p] && !this.box[p];
                    square.add(p);
                    if (i == 0 || i == side - 1 || j == 0 || j == side - 1) {
                        edge.add(p);
                    }
                }
            }
            if (!ok) {
                continue;
            }
            for (int p : square) {
                int r = p / this.rows;
                int y = p % this.rows;
                for (int[] d : GrooveGrid.DIRS4) {
                    ok &= !grid.g(r + d[0], y + d[1]);
                }
            }
            if (!ok) {
                continue;
            }
            for (int p : edge) {
                g[p] = true;
            }
            if (grid.localOk(edge, GrooveGrid.noHubs())) {
                squaresOf.put(t[0], square);
            } else {
                for (int p : edge) {
                    g[p] = false;
                }
            }
        }

        List<Line> lines = new ArrayList<>();
        for (Long2IntLinkedOpenHashMap.Entry entry : pairs.long2IntEntrySet()) {
            int a = (int) (entry.getLongKey() >>> 32);
            int b = (int) entry.getLongKey();
            if (entry.getIntValue() < 2 || skip.contains(a) || skip.contains(b)) {
                continue;
            }
            int[] hubA = hub.get(a);
            int[] hubB = hub.get(b);
            int ca = a;
            int cb = b;
            if (hubB[1] < hubA[1] || (hubB[1] == hubA[1] && hubB[0] < hubA[0])) {
                int[] swap = hubA;
                hubA = hubB;
                hubB = swap;
                ca = b;
                cb = a;
            }
            int ha = this.stones.idx(hubA[0], hubA[1]);
            int hb = this.stones.idx(hubB[0], hubB[1]);
            if (ha == hb || this.box[ha] || this.box[hb]) {
                continue;
            }
            int dr = Math.floorMod(hubB[0] - hubA[0] + this.ring / 2, this.ring) - this.ring / 2;
            int da = hubB[1] - hubA[1];
            IntList blocks = new IntArrayList();
            blocks.add(ha);
            int r = hubA[0];
            int y = hubA[1];
            for (int[] step : lineSteps(dr, da)) {
                r += step[0];
                y += step[1];
                blocks.add(this.stones.idx(r, y));
            }
            IntSet sa = squaresOf.getOrDefault(ca, IntSets.EMPTY_SET);
            IntSet sb = squaresOf.getOrDefault(cb, IntSets.EMPTY_SET);
            int lo = 0;
            int hi = blocks.size();
            while (lo < hi && sa.contains(blocks.getInt(lo))) {
                lo++;
            }
            while (hi > lo && sb.contains(blocks.getInt(hi - 1))) {
                hi--;
            }
            if (hi - lo < 1) {
                continue;
            }
            IntList part = new IntArrayList(blocks.subList(lo, hi));
            boolean inside = false;
            for (int p : part) {
                inside |= sa.contains(p) || sb.contains(p);
            }
            if (inside) {
                continue;
            }
            IntSet plain = new IntOpenHashSet();
            if (sa.isEmpty()) {
                plain.add(ha);
            }
            if (sb.isEmpty()) {
                plain.add(hb);
            }
            long key = this.seeds.lineKey(hubA[0], hubA[1] + this.axis0, hubB[0], hubB[1] + this.axis0);
            lines.add(new Line(dr * dr + da * da, key, part, plain));
        }
        lines.sort(Comparator.comparingInt(Line::length).thenComparingLong(Line::key).thenComparingInt(line -> line.blocks().getInt(0)));
        IntSet hubs = new IntOpenHashSet();
        for (Line line : lines) {
            IntList trimmed = this.trim(grid, line.blocks());
            IntLinkedOpenHashSet fresh = new IntLinkedOpenHashSet();
            for (int p : trimmed) {
                if (!g[p] && !this.box[p]) {
                    fresh.add(p);
                }
            }
            if (fresh.isEmpty()) {
                continue;
            }
            IntList added = new IntArrayList(fresh);
            for (int p : added) {
                g[p] = true;
            }
            IntSet allowed = new IntOpenHashSet(line.plain());
            allowed.addAll(hubs);
            if (grid.localOk(added, allowed)) {
                hubs.addAll(line.plain());
            } else {
                for (int p : added) {
                    g[p] = false;
                }
            }
        }
        for (int p : grid.jogPluses(this.seeds)) {
            int r = p / this.rows;
            int y = p % this.rows;
            for (int[] d : GrooveGrid.DIRS4) {
                int a1 = this.stones.idx(r + d[0], y + d[1]);
                g[a1] = false;
                if (grid.localOk(IntList.of(a1), GrooveGrid.noHubs())) {
                    break;
                }
                g[a1] = true;
            }
        }
        return grid;
    }

    private static long pairKey(int a, int b) {
        return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFFFFFFL);
    }

    /**
     * The rounded surface center (ring, row) of each stone. The ring position is a circular mean.
     * The row is the mean of the axis positions, rounded half up in whole numbers, so every window gives the same center.
     */
    private Int2ObjectOpenHashMap<int[]> centroids(int[] comp, IntList ids) {
        Int2ObjectLinkedOpenHashMap<IntList> found = ChaosStones.groups(comp);
        Int2ObjectOpenHashMap<int[]> out = new Int2ObjectOpenHashMap<>();
        double turn = 2 * Math.PI / this.ring;
        for (int id : ids) {
            IntList cells = found.get(id);
            double sin = 0;
            double cos = 0;
            for (int p : cells) {
                double angle = (p / this.rows) * turn;
                sin += StrictMath.sin(angle);
                cos += StrictMath.cos(angle);
            }
            double mid = StrictMath.atan2(sin, cos) / turn;
            double unwrapped = 0;
            long axisSum = 0;
            for (int p : cells) {
                double x = p / this.rows - mid + this.ring / 2.0;
                unwrapped += mid + (x - this.ring * Math.floor(x / this.ring)) - this.ring / 2.0;
                axisSum += p % this.rows + this.axis0;
            }
            int n = cells.size();
            int hubRing = Math.floorMod((int) Math.floor(unwrapped / n + 0.5), this.ring);
            int hubAxis = (int) Math.floorDiv(2 * axisSum + n, 2L * n);
            out.put(id, new int[] {hubRing, hubAxis - this.axis0});
        }
        return out;
    }

    /** The 4-connected steps of the straight line from (0, 0) to (dr, da). */
    static List<int[]> lineSteps(int dr, int da) {
        int nr = Math.abs(dr);
        int na = Math.abs(da);
        int sr = Integer.signum(dr);
        int sa = Integer.signum(da);
        List<int[]> out = new ArrayList<>(nr + na);
        int i = 0;
        int j = 0;
        while (i < nr || j < na) {
            if (j >= na || (i < nr && (long) (2 * i + 1) * na < (long) (2 * j + 1) * nr)) {
                out.add(new int[] {sr, 0});
                i++;
            } else {
                out.add(new int[] {0, sa});
                j++;
            }
        }
        return out;
    }

    /** Cuts each end of a line back to the last block that touches the grooves, so the line joins them in a T. */
    private IntList trim(GrooveGrid grid, IntList blocks) {
        boolean[] flags = new boolean[blocks.size()];
        for (int k = 0; k < flags.length; k++) {
            int p = blocks.getInt(k);
            int r = p / this.rows;
            int y = p % this.rows;
            boolean touches = grid.groove[p];
            for (int[] d : GrooveGrid.DIRS4) {
                touches |= grid.g(r + d[0], y + d[1]);
            }
            flags[k] = touches;
        }
        int s = 0;
        while (s + 1 < flags.length && flags[s] && flags[s + 1]) {
            s++;
        }
        int e = flags.length - 1;
        while (e - 1 > s && flags[e] && flags[e - 1]) {
            e--;
        }
        return new IntArrayList(blocks.subList(s, e + 1));
    }

}
