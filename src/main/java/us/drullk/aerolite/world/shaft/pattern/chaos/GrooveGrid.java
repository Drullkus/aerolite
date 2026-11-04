package us.drullk.aerolite.world.shaft.pattern.chaos;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The grooves of one window of a ring, with the local rules and the plus crossing repair.
 * A cell index is {@code ring * rows + row}. The ring wraps. The rows do not.
 *
 * <p>The local rules around a change: no 2x2 groove square, no plus crossing, no orphan wall block,
 * and each face next to the change keeps a 2x2 wall square.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class GrooveGrid {

    /** The four steps as (ring, row), in the sequence of the prototype. */
    static final int[][] DIRS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** A face search that grows past this many blocks counts as a wide face. */
    private static final int FACE_LIMIT = 600;
    /** The first plus crossing repair: the lower arm runs around on the side of the higher ring position. */
    private static final int[] FIRST_JOG = {0, 1, 1, 0};

    final int ring;
    final int rows;
    final int axis0;
    final boolean[] groove;
    final boolean[] box;

    GrooveGrid(int ring, int rows, int axis0, boolean[] groove, boolean[] box) {
        this.ring = ring;
        this.rows = rows;
        this.axis0 = axis0;
        this.groove = groove;
        this.box = box;
    }

    int idx(int r, int y) {
        return Math.floorMod(r, this.ring) * this.rows + y;
    }

    boolean inRows(int y) {
        return y >= 0 && y < this.rows;
    }

    /** True for a groove inside the rows. */
    boolean g(int r, int y) {
        return this.inRows(y) && this.groove[this.idx(r, y)];
    }

    /** True for a wall block. A block outside the rows counts as wall. */
    private boolean wallOrOutside(int r, int y) {
        if (!this.inRows(y)) {
            return true;
        }
        int i = this.idx(r, y);
        return !this.groove[i] && !this.box[i];
    }

    /** True for a wall block inside the rows. */
    private boolean wallInside(int r, int y) {
        if (!this.inRows(y)) {
            return false;
        }
        int i = this.idx(r, y);
        return !this.groove[i] && !this.box[i];
    }

    /** True when the blocks near the changed cells keep the local rules. A cell in {@code hubs} can be a plus crossing. */
    boolean localOk(IntList changed, IntSet hubs) {
        IntOpenHashSet area = new IntOpenHashSet();
        for (int c : changed) {
            int r = c / this.rows;
            int y = c % this.rows;
            for (int dr = -2; dr <= 2; dr++) {
                for (int da = -2; da <= 2; da++) {
                    if (this.inRows(y + da)) {
                        area.add(this.idx(r + dr, y + da));
                    }
                }
            }
        }
        for (int p : area) {
            int r = p / this.rows;
            int y = p % this.rows;
            if (this.groove[p]) {
                if (y + 1 < this.rows && this.g(r + 1, y) && this.g(r, y + 1) && this.g(r + 1, y + 1)) {
                    return false;
                }
                if (y > 0 && y < this.rows - 1 && this.g(r + 1, y) && this.g(r - 1, y) && this.g(r, y - 1) && this.g(r, y + 1) && !hubs.contains(p)) {
                    return false;
                }
            } else if (!this.box[p]) {
                boolean wall = false;
                for (int[] d : DIRS4) {
                    wall |= this.wallOrOutside(r + d[0], y + d[1]);
                }
                if (!wall) {
                    return false;
                }
            }
        }
        IntOpenHashSet done = new IntOpenHashSet();
        for (int c : changed) {
            int r = c / this.rows;
            int y = c % this.rows;
            for (int[] d : DIRS4) {
                if (!this.inRows(y + d[1])) {
                    continue;
                }
                int q = this.idx(r + d[0], y + d[1]);
                if (!done.contains(q) && this.wallInside(r + d[0], y + d[1])) {
                    IntOpenHashSet seen = new IntOpenHashSet();
                    if (!this.faceHasSquare(q, seen)) {
                        return false;
                    }
                    done.addAll(seen);
                }
            }
        }
        return true;
    }

    /** Searches the face of a wall block for a 2x2 wall square. A face that reaches the window ends counts as good. */
    private boolean faceHasSquare(int start, IntOpenHashSet seen) {
        seen.add(start);
        IntArrayList stack = IntArrayList.of(start);
        while (!stack.isEmpty()) {
            int p = stack.popInt();
            int r = p / this.rows;
            int y = p % this.rows;
            if (y == 0 || y == this.rows - 1 || seen.size() > FACE_LIMIT) {
                return true;
            }
            if (this.wallInside(r + 1, y) && this.wallInside(r, y + 1) && this.wallInside(r + 1, y + 1)) {
                return true;
            }
            for (int[] d : DIRS4) {
                if (this.wallInside(r + d[0], y + d[1])) {
                    int q = this.idx(r + d[0], y + d[1]);
                    if (seen.add(q)) {
                        stack.add(q);
                    }
                }
            }
        }
        return false;
    }

    /** Every groove with grooves on all four sides, in cell index sequence. */
    IntList plusBlocks() {
        IntList out = new IntArrayList();
        for (int p = 0; p < this.groove.length; p++) {
            if (this.groove[p] && this.isPlus(p / this.rows, p % this.rows)) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean isPlus(int r, int y) {
        return this.g(r, y) && this.g(r + 1, y) && this.g(r - 1, y) && this.g(r, y + 1) && this.g(r, y - 1);
    }

    /**
     * Splits each plus crossing into two T junctions. One arm leaves its first block and runs around it through the two blocks next to a side arm.
     * The crossings go in the sequence of their position hash. The first repair variant goes first. The random of the crossing sets the other seven.
     * The first variant that keeps the local rules stays. The crossings that wait for their own repair do not count against a variant.
     *
     * @return the crossings that no variant fits
     */
    IntList jogPluses(ChaosSeeds seeds) {
        IntList found = this.plusBlocks();
        IntOpenHashSet pending = new IntOpenHashSet(found);
        List<long[]> sorted = new ArrayList<>(found.size());
        for (int p : found) {
            int r = p / this.rows;
            int y = p % this.rows;
            sorted.add(new long[] {seeds.plusKey(r, y + this.axis0), y, r, p});
        }
        sorted.sort(Comparator.<long[]>comparingLong(k -> k[0]).thenComparingLong(k -> k[1]).thenComparingLong(k -> k[2]));
        IntList left = new IntArrayList();
        for (long[] key : sorted) {
            int p = (int) key[3];
            int r = (int) key[2];
            int y = (int) key[1];
            pending.remove(p);
            if (!this.isPlus(r, y)) {
                continue;
            }
            boolean done = false;
            for (int[] v : this.variants(seeds.jog(r, y + this.axis0))) {
                int dr = v[0];
                int da = v[1];
                int sr = v[2];
                int sa = v[3];
                int a2y = y + 2 * da;
                int b1y = y + sa + da;
                int b2y = y + sa + 2 * da;
                if (!this.inRows(a2y) || !this.inRows(b1y) || !this.inRows(b2y)) {
                    continue;
                }
                int a1 = this.idx(r + dr, y + da);
                int a2 = this.idx(r + 2 * dr, a2y);
                int b1 = this.idx(r + sr + dr, b1y);
                int b2 = this.idx(r + sr + 2 * dr, b2y);
                if (!this.groove[a2] || this.groove[b1] || this.groove[b2] || this.box[b1] || this.box[b2]) {
                    continue;
                }
                this.groove[a1] = false;
                this.groove[b1] = true;
                this.groove[b2] = true;
                if (this.localOk(IntList.of(a1, b1, b2), pending)) {
                    done = true;
                    break;
                }
                this.groove[a1] = true;
                this.groove[b1] = false;
                this.groove[b2] = false;
            }
            if (!done) {
                left.add(p);
            }
        }
        return left;
    }

    /** The eight repair variants as (arm ring, arm row, side ring, side row): the first variant, then the other seven in a random sequence. */
    private List<int[]> variants(RandomSource random) {
        List<int[]> out = new ArrayList<>(8);
        for (int[] d : DIRS4) {
            for (int[] s : DIRS4) {
                int[] v = {d[0], d[1], s[0], s[1]};
                if (d[0] * s[0] + d[1] * s[1] == 0 && !java.util.Arrays.equals(v, FIRST_JOG)) {
                    out.add(v);
                }
            }
        }
        for (int i = out.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] a = out.get(i);
            out.set(i, out.get(j));
            out.set(j, a);
        }
        out.addFirst(FIRST_JOG);
        return out;
    }

    /** No cell may be a plus crossing. */
    static IntSet noHubs() {
        return IntSets.EMPTY_SET;
    }

}
