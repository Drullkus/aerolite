package us.drullk.aerolite.world.shaft.pattern.chaos;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import us.drullk.aerolite.world.pattern.ChaosSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * The stone labels and the grooves of one window of a ring.
 *
 * <p>Each block takes the label of the nearest grid point, with the distance wrapped around the ring. A box block has no label.
 * The corner radius chamfers the stones at each T junction. The labels then settle: every block becomes thick and every face
 * keeps a 2x2 wall square. A block is a groove when its 2x2 window holds two labels. Voronoi then repairs each plus crossing.
 * Delaunay replaces the grooves with the dual of the stones.
 *
 * <p>A label is a cell code that sorts like the prototype code: by the cell along the axis, then by the cell around the ring.
 * Only the sequence and the equality of labels count, so the code is local to the window.
 * The label -1 marks a box block. The label -2 marks a block outside the rows.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ChaosStones {

    private static final double EPS = 1e-9;
    private static final int OUTSIDE = -2;
    private static final int BOX = -1;
    private static final int SETTLE_ROUNDS = 40;
    private static final int REPAIR_ROUNDS = 8;
    /** The flat pair and the corner pair of a T junction, as window slots: 0 at (0, 0), 1 at (1, 0), 2 at (0, 1), 3 at (1, 1). */
    private static final int[][] T_PAIRS = {{0, 1, 2, 3}, {2, 3, 0, 1}, {0, 2, 1, 3}, {1, 3, 0, 2}};
    private static final int[][] NB8 = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

    private final ChaosParams params;
    private final ChaosSeeds seeds;
    private final int ring;
    private final int rows;
    private final int axis0;
    private final boolean[] box;
    private int[] labels;

    /**
     * @param axis0 the first axis position of the window
     * @param rows  the blocks of the window along the axis
     * @param box   the box blocks of the window
     */
    public ChaosStones(ChaosParams params, ChaosSeeds seeds, int axis0, int rows, boolean[] box) {
        this.params = params;
        this.seeds = seeds;
        this.ring = params.ringLength();
        this.rows = rows;
        this.axis0 = axis0;
        this.box = box;
    }

    /** The settled labels after {@link #grooves}. */
    public int[] labels() {
        return this.labels;
    }

    /** Computes the labels and returns the groove mask of the window. */
    public boolean[] grooves() {
        int[] lab = this.nearest();
        for (int i = 0; i < lab.length; i++) {
            if (this.box[i]) {
                lab[i] = BOX;
            }
        }
        if (this.params.radius() > 0) {
            lab = this.roundCorners(lab);
        }
        this.labels = lab;
        GrooveGrid grid = null;
        for (int round = 0; round < REPAIR_ROUNDS; round++) {
            grid = new GrooveGrid(this.ring, this.rows, this.axis0, this.settle(lab), this.box);
            if (this.params.cells() != ChaosSettings.Cells.VORONOI) {
                break;
            }
            IntList left = grid.jogPluses(this.seeds);
            if (left.isEmpty()) {
                break;
            }
            boolean changed = false;
            for (int p : left) {
                changed |= this.mergeCell(lab, p);
            }
            if (!changed) {
                break;
            }
        }
        if (this.params.cells() == ChaosSettings.Cells.DELAUNAY) {
            grid = new ChaosDual(this.params, this.seeds, this, this.box).build(lab);
        }
        return grid.groove;
    }

    int idx(int r, int y) {
        return Math.floorMod(r, this.ring) * this.rows + y;
    }

    boolean inRows(int y) {
        return y >= 0 && y < this.rows;
    }

    int ring() {
        return this.ring;
    }

    int rows() {
        return this.rows;
    }

    int axis0() {
        return this.axis0;
    }

    private int at(int[] lab, int r, int y) {
        return this.inRows(y) ? lab[this.idx(r, y)] : OUTSIDE;
    }

    private static double floorMod(double x, double m) {
        return x - m * Math.floor(x / m);
    }

    /** The label of the nearest point to each block center. A tie takes the lower label. */
    private int[] nearest() {
        int count = this.params.cellsAround();
        double width = this.params.cellWidth();
        int s = this.params.spacing();
        double jitter = this.params.jitter();
        int jLo = Math.floorDiv(this.axis0, s) - 2;
        int jHi = Math.floorDiv(this.axis0 + this.rows, s) + 2;
        int span = jHi - jLo + 1;
        double[] pr = new double[count * span];
        double[] pa = new double[count * span];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < span; j++) {
                int cj = jLo + j;
                pr[i * span + j] = (i + 0.5 + jitter * (this.seeds.unit(i, cj, 0) - 0.5)) * width;
                pa[i * span + j] = (cj + 0.5 + jitter * (this.seeds.unit(i, cj, 1) - 0.5)) * s;
            }
        }
        int[] out = new int[this.ring * this.rows];
        for (int r = 0; r < this.ring; r++) {
            double br = r + 0.5;
            int bi = (int) Math.floor(br / width);
            for (int y = 0; y < this.rows; y++) {
                double ba = this.axis0 + y + 0.5;
                int bj = (int) Math.floor(ba / s);
                double bestD = Double.POSITIVE_INFINITY;
                int best = 0;
                for (int di = -2; di <= 2; di++) {
                    int ci = Math.floorMod(bi + di, count);
                    for (int dj = -2; dj <= 2; dj++) {
                        int j = bj + dj - jLo;
                        double dr = floorMod(br - pr[ci * span + j] + this.ring / 2.0, this.ring) - this.ring / 2.0;
                        double da = ba - pa[ci * span + j];
                        double d = dr * dr + da * da;
                        int code = j * count + ci;
                        if (d < bestD - EPS || (Math.abs(d - bestD) <= EPS && code < best)) {
                            bestD = d;
                            best = code;
                        }
                    }
                }
                out[this.idx(r, y)] = best;
            }
        }
        return out;
    }

    /**
     * The corner radius: at each T junction, the stone with the flat side takes the corner triangle of each of the two other stones.
     * A block that two triangles claim takes the nearer junction, then the lower label.
     */
    private int[] roundCorners(int[] lab) {
        int radius = this.params.radius();
        int[] claimDistance = new int[lab.length];
        int[] claimLabel = new int[lab.length];
        java.util.Arrays.fill(claimDistance, Integer.MAX_VALUE);
        int[] q = new int[4];
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                q[0] = this.at(lab, r, y);
                q[1] = this.at(lab, r + 1, y);
                q[2] = this.at(lab, r, y + 1);
                q[3] = this.at(lab, r + 1, y + 1);
                if (q[0] < 0 || q[1] < 0 || q[2] < 0 || q[3] < 0 || distinct(q) != 3) {
                    continue;
                }
                int flat = Integer.MIN_VALUE;
                int c0 = 0;
                int c1 = 0;
                for (int[] pair : T_PAIRS) {
                    if (q[pair[0]] == q[pair[1]]) {
                        flat = q[pair[0]];
                        c0 = pair[2];
                        c1 = pair[3];
                    }
                }
                if (flat == Integer.MIN_VALUE) {
                    continue;
                }
                for (int k : new int[] {c0, c1}) {
                    int dx = k & 1;
                    int dy = k >> 1;
                    int sx = dx == 0 ? -1 : 1;
                    int sy = dy == 0 ? -1 : 1;
                    for (int i = 0; i < radius; i++) {
                        for (int j = 0; j < radius - i; j++) {
                            int py = y + dy + sy * j;
                            if (!this.inRows(py)) {
                                continue;
                            }
                            int p = this.idx(r + dx + sx * i, py);
                            if (lab[p] == q[k] && (i + j < claimDistance[p] || (i + j == claimDistance[p] && flat < claimLabel[p]))) {
                                claimDistance[p] = i + j;
                                claimLabel[p] = flat;
                            }
                        }
                    }
                }
            }
        }
        int[] out = lab.clone();
        for (int p = 0; p < out.length; p++) {
            if (claimDistance[p] != Integer.MAX_VALUE) {
                out[p] = claimLabel[p];
            }
        }
        return out;
    }

    private static int distinct(int[] q) {
        int n = 0;
        for (int i = 0; i < q.length; i++) {
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                seen |= q[j] == q[i];
            }
            if (!seen) {
                n++;
            }
        }
        return n;
    }

    /** True where a block lies in a 2x2 square of one label. */
    private boolean[] thick(int[] lab) {
        boolean[] u = new boolean[lab.length];
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                int l = lab[this.idx(r, y)];
                u[this.idx(r, y)] = l >= 0 && l == this.at(lab, r + 1, y) && l == this.at(lab, r, y + 1) && l == this.at(lab, r + 1, y + 1);
            }
        }
        boolean[] out = new boolean[lab.length];
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                out[this.idx(r, y)] = u[this.idx(r, y)] || u[this.idx(r - 1, y)]
                        || (y > 0 && (u[this.idx(r, y - 1)] || u[this.idx(r - 1, y - 1)]));
            }
        }
        return out;
    }

    /** For each block, the most frequent other label among its 8 neighbors, the lowest on a tie, or -1. */
    private int[] majorityOther(int[] lab) {
        int[] out = new int[lab.length];
        int[] c = new int[NB8.length];
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                for (int k = 0; k < NB8.length; k++) {
                    c[k] = this.at(lab, r + NB8[k][0], y + NB8[k][1]);
                }
                int own = lab[this.idx(r, y)];
                int best = -1;
                int bestN = 0;
                for (int k = 0; k < NB8.length; k++) {
                    if (c[k] < 0 || c[k] == own) {
                        continue;
                    }
                    int n = 0;
                    for (int m = 0; m < NB8.length; m++) {
                        if (c[m] == c[k]) {
                            n++;
                        }
                    }
                    if (n > bestN || (n == bestN && c[k] < best)) {
                        best = c[k];
                        bestN = n;
                    }
                }
                out[this.idx(r, y)] = best;
            }
        }
        return out;
    }

    /** The one-sided groove rule: a labeled block is a groove when its 2x2 window along the ring and the axis holds two labels. */
    private boolean[] grooveMask(int[] lab) {
        boolean[] out = new boolean[lab.length];
        int[] w = new int[4];
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                w[0] = lab[this.idx(r, y)];
                if (w[0] < 0) {
                    continue;
                }
                w[1] = this.at(lab, r + 1, y);
                w[2] = this.at(lab, r, y + 1);
                w[3] = this.at(lab, r + 1, y + 1);
                boolean g = false;
                for (int i = 0; i < 4 && !g; i++) {
                    for (int j = i + 1; j < 4 && !g; j++) {
                        g = w[i] >= 0 && w[j] >= 0 && w[i] != w[j];
                    }
                }
                out[this.idx(r, y)] = g;
            }
        }
        return out;
    }

    /**
     * Relabels every block outside a one-label 2x2 square to its majority neighbor label, until none is left.
     * A block that stays thin also gives its new label to the rest of the 2x2 square around it that holds the most of that label.
     */
    private void thinPass(int[] lab, boolean[] frozen) {
        for (int round = 0; round < SETTLE_ROUNDS; round++) {
            boolean[] thick = this.thick(lab);
            boolean any = false;
            boolean[] thin = new boolean[lab.length];
            for (int i = 0; i < lab.length; i++) {
                thin[i] = !thick[i] && lab[i] >= 0 && !frozen[i];
                any |= thin[i];
            }
            if (!any) {
                return;
            }
            int[] target = this.majorityOther(lab);
            boolean[] update = new boolean[lab.length];
            any = false;
            for (int i = 0; i < lab.length; i++) {
                update[i] = thin[i] && target[i] >= 0;
                any |= update[i];
            }
            if (!any) {
                return;
            }
            for (int i = 0; i < lab.length; i++) {
                if (update[i]) {
                    lab[i] = target[i];
                }
            }
            boolean[] after = this.thick(lab);
            IntList still = new IntArrayList();
            for (int y = 0; y < this.rows; y++) {
                for (int r = 0; r < this.ring; r++) {
                    int i = this.idx(r, y);
                    if (update[i] && !after[i]) {
                        still.add(i);
                    }
                }
            }
            for (int p : still) {
                this.fillSquare(lab, frozen, p);
            }
        }
    }

    /** Gives the label of a block to the rest of the 2x2 square around it that holds the most of that label. */
    private void fillSquare(int[] lab, boolean[] frozen, int p) {
        int r = p / this.rows;
        int y = p % this.rows;
        int t = lab[p];
        int bestN = -1;
        int[] best = null;
        for (int dr = -1; dr <= 0; dr++) {
            for (int da = -1; da <= 0; da++) {
                int[] square = new int[4];
                boolean ok = true;
                int k = 0;
                for (int i = 0; i <= 1 && ok; i++) {
                    for (int j = 0; j <= 1 && ok; j++) {
                        int qy = y + da + j;
                        if (!this.inRows(qy)) {
                            ok = false;
                            break;
                        }
                        int q = this.idx(r + dr + i, qy);
                        ok = lab[q] >= 0 && !frozen[q];
                        square[k++] = q;
                    }
                }
                if (!ok) {
                    continue;
                }
                int n = 0;
                for (int q : square) {
                    if (lab[q] == t) {
                        n++;
                    }
                }
                if (n > bestN) {
                    bestN = n;
                    best = square;
                }
            }
        }
        if (best != null) {
            for (int q : best) {
                lab[q] = t;
            }
        }
    }

    /** The component of each mask cell, 4-connected with the ring wrapped, as its lowest cell index, or -1 off the mask. With labels, only equal labels join. */
    int[] components(boolean[] mask, int[] lab) {
        int n = mask.length;
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                int i = this.idx(r, y);
                if (!mask[i]) {
                    continue;
                }
                int right = this.idx(r + 1, y);
                if (mask[right] && (lab == null || lab[i] == lab[right])) {
                    union(parent, i, right);
                }
                if (y + 1 < this.rows) {
                    int down = i + 1;
                    if (mask[down] && (lab == null || lab[i] == lab[down])) {
                        union(parent, i, down);
                    }
                }
            }
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = mask[i] ? find(parent, i) : -1;
        }
        return out;
    }

    private static int find(int[] parent, int i) {
        int root = i;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[i] != root) {
            int next = parent[i];
            parent[i] = root;
            i = next;
        }
        return root;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra < rb) {
            parent[rb] = ra;
        } else if (rb < ra) {
            parent[ra] = rb;
        }
    }

    /** The cells of each component, in cell index sequence. The components come in the sequence of their ids. */
    static Int2ObjectLinkedOpenHashMap<IntList> groups(int[] comp) {
        Int2ObjectLinkedOpenHashMap<IntList> out = new Int2ObjectLinkedOpenHashMap<>();
        for (int i = 0; i < comp.length; i++) {
            if (comp[i] >= 0 && comp[i] == i) {
                out.put(i, new IntArrayList());
            }
        }
        for (int i = 0; i < comp.length; i++) {
            if (comp[i] >= 0) {
                out.get(comp[i]).add(i);
            }
        }
        return out;
    }

    /** True at the low corner of each k x k square of mask cells. */
    private boolean[] squares(boolean[] mask, int k) {
        boolean[] out = new boolean[mask.length];
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y + k <= this.rows; y++) {
                boolean all = true;
                for (int dr = 0; dr < k && all; dr++) {
                    for (int da = 0; da < k && all; da++) {
                        all = mask[this.idx(r + dr, y + da)];
                    }
                }
                out[this.idx(r, y)] = all;
            }
        }
        return out;
    }

    /**
     * Relabels each face without a square of {@code width} x {@code width} wall blocks, with the grooves of its own label around it.
     * The face takes the most frequent label next to it that has no small face, else the most frequent higher label.
     * A face whose first block is in the history comes back after a merge, so its whole stone takes the new label.
     * A face that touches the frozen rows stays.
     *
     * @return true when a label changed
     */
    private boolean mergeTiny(int[] lab, boolean[] grooves, boolean[] frozen, IntSet history, int width) {
        boolean[] wall = new boolean[lab.length];
        for (int i = 0; i < lab.length; i++) {
            wall[i] = lab[i] >= 0 && !grooves[i];
        }
        int[] comp = this.components(wall, null);
        boolean[] square = this.squares(wall, width);
        IntSet good = new IntOpenHashSet();
        IntSet skip = new IntOpenHashSet();
        for (int i = 0; i < lab.length; i++) {
            if (square[i]) {
                good.add(comp[i]);
            }
            if (frozen[i]) {
                skip.add(comp[i]);
            }
        }
        Int2ObjectLinkedOpenHashMap<IntList> found = groups(comp);
        List<IntList> tiny = new ArrayList<>();
        IntSet tinyLabels = new IntOpenHashSet();
        for (Int2ObjectLinkedOpenHashMap.Entry<IntList> entry : found.int2ObjectEntrySet()) {
            if (!good.contains(entry.getIntKey()) && !skip.contains(entry.getIntKey())) {
                tiny.add(entry.getValue());
                tinyLabels.add(lab[entry.getIntKey()]);
            }
        }
        int[] stones = null;
        List<IntSet> blocks = new ArrayList<>();
        IntList targets = new IntArrayList();
        for (IntList face : tiny) {
            int label = lab[face.getInt(0)];
            IntOpenHashSet t = new IntOpenHashSet(face);
            int first = face.getInt(0);
            for (int p : face) {
                if (p % this.rows < first % this.rows || (p % this.rows == first % this.rows && p / this.rows < first / this.rows)) {
                    first = p;
                }
            }
            if (history.contains(first)) {
                if (stones == null) {
                    stones = this.components(nonNegative(lab), lab);
                }
                int id = stones[first];
                int minY = Integer.MAX_VALUE;
                int maxY = Integer.MIN_VALUE;
                IntList stone = new IntArrayList();
                for (int i = 0; i < stones.length; i++) {
                    if (stones[i] == id) {
                        stone.add(i);
                        minY = Math.min(minY, i % this.rows);
                        maxY = Math.max(maxY, i % this.rows);
                    }
                }
                if (minY >= 2 && maxY < this.rows - 2) {
                    t.addAll(stone);
                }
            }
            history.add(first);
            for (int p : new IntArrayList(t)) {
                int r = p / this.rows;
                int y = p % this.rows;
                for (int[] d : NB8) {
                    if (this.inRows(y + d[1])) {
                        int q = this.idx(r + d[0], y + d[1]);
                        if (grooves[q] && lab[q] == label) {
                            t.add(q);
                        }
                    }
                }
            }
            Int2IntLinkedOpenHashMap count = new Int2IntLinkedOpenHashMap();
            for (int p : t) {
                int r = p / this.rows;
                int y = p % this.rows;
                for (int[] d : GrooveGrid.DIRS4) {
                    if (this.inRows(y + d[1])) {
                        int q = this.idx(r + d[0], y + d[1]);
                        if (!t.contains(q) && lab[q] >= 0 && lab[q] != label) {
                            count.addTo(lab[q], 1);
                        }
                    }
                }
            }
            int target = pick(count, k -> !tinyLabels.contains(k));
            if (target < 0) {
                target = pick(count, k -> k > label);
            }
            if (target >= 0) {
                blocks.add(t);
                targets.add(target);
            }
        }
        for (int u = 0; u < blocks.size(); u++) {
            int target = targets.getInt(u);
            for (int p : blocks.get(u)) {
                lab[p] = target;
            }
        }
        return !blocks.isEmpty();
    }

    /** The most frequent label that passes the filter, the lowest on a tie, or -1. */
    private static int pick(Int2IntMap count, java.util.function.IntPredicate filter) {
        int best = -1;
        int bestN = 0;
        for (Int2IntMap.Entry entry : count.int2IntEntrySet()) {
            int k = entry.getIntKey();
            int n = entry.getIntValue();
            if (filter.test(k) && (n > bestN || (n == bestN && k < best))) {
                best = k;
                bestN = n;
            }
        }
        return best;
    }

    private static boolean[] nonNegative(int[] lab) {
        boolean[] out = new boolean[lab.length];
        for (int i = 0; i < lab.length; i++) {
            out[i] = lab[i] >= 0;
        }
        return out;
    }

    /** Makes every block thick and every face at least the merge width across. Returns the groove mask. */
    private boolean[] settle(int[] lab) {
        boolean[] frozen = new boolean[lab.length];
        for (int r = 0; r < this.ring; r++) {
            for (int y = 0; y < this.rows; y++) {
                frozen[this.idx(r, y)] = y < 2 || y >= this.rows - 2;
            }
        }
        IntSet history = new IntOpenHashSet();
        for (int round = 0; round < SETTLE_ROUNDS; round++) {
            this.thinPass(lab, frozen);
            boolean[] grooves = this.grooveMask(lab);
            if (!this.mergeTiny(lab, grooves, frozen, history, ChaosParams.MERGE_WIDTH)) {
                return grooves;
            }
        }
        this.thinPass(lab, frozen);
        return this.grooveMask(lab);
    }

    /**
     * Gives the smallest stone at the 2x2 window of a cell the most frequent label along its border. A stone that touches the window ends stays.
     *
     * @return true when a label changed
     */
    private boolean mergeCell(int[] lab, int p) {
        int[] comp = this.components(nonNegative(lab), lab);
        int r = p / this.rows;
        int y = p % this.rows;
        IntSet ids = new IntOpenHashSet();
        for (int i = 0; i <= 1; i++) {
            for (int j = 0; j <= 1; j++) {
                if (this.inRows(y + j) && comp[this.idx(r + i, y + j)] >= 0) {
                    ids.add(comp[this.idx(r + i, y + j)]);
                }
            }
        }
        IntList sortedIds = new IntArrayList(ids);
        sortedIds.sort(null);
        IntList bestStone = null;
        int bestSize = 0;
        int bestLabel = 0;
        for (int id : sortedIds) {
            IntList stone = new IntArrayList();
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int i = 0; i < comp.length; i++) {
                if (comp[i] == id) {
                    stone.add(i);
                    minY = Math.min(minY, i % this.rows);
                    maxY = Math.max(maxY, i % this.rows);
                }
            }
            if (minY < 2 || maxY >= this.rows - 2) {
                continue;
            }
            int label = lab[id];
            if (bestStone == null || stone.size() < bestSize || (stone.size() == bestSize && label < bestLabel)) {
                bestStone = stone;
                bestSize = stone.size();
                bestLabel = label;
            }
        }
        if (bestStone == null) {
            return false;
        }
        Int2IntLinkedOpenHashMap count = new Int2IntLinkedOpenHashMap();
        for (int q0 : bestStone) {
            int qr = q0 / this.rows;
            int qy = q0 % this.rows;
            for (int[] d : GrooveGrid.DIRS4) {
                if (this.inRows(qy + d[1])) {
                    int q = this.idx(qr + d[0], qy + d[1]);
                    if (lab[q] >= 0 && lab[q] != bestLabel) {
                        count.addTo(lab[q], 1);
                    }
                }
            }
        }
        int target = pick(count, k -> true);
        if (target < 0) {
            return false;
        }
        for (int q : bestStone) {
            lab[q] = target;
        }
        return true;
    }

}
