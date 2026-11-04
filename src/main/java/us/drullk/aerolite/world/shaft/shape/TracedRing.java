package us.drullk.aerolite.world.shaft.shape;

import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.surface.PatchColumn;
import us.drullk.aerolite.world.shaft.surface.RingKind;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;
import us.drullk.aerolite.world.shaft.surface.SectionBlock;
import us.drullk.aerolite.world.shaft.surface.SectionOffset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A ring description that traces the rings of a solid cross section.
 *
 * <p>The shape names a block by the offsets (u, v) from -6 to 6. The offset v is the stacking direction: flange to flange, or plate to plate.
 * The stacking axis maps v to the first or the second offset of the frame. The other offset takes u.
 * A ring block is a solid block with an air block among its 8 neighbors.
 * A ring starts at its start block and takes its first step along +u. The walk tries +u, -v, -u and +v in that sequence.
 * A ring block with two or more air neighbors along u and v is convex. A ring block with none is concave.
 * The floor of a convex block is the inward diagonal. The floors of a concave block are its solid neighbors off the surface.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public abstract class TracedRing implements RingShape {

    private static final int SIZE = 2 * Ring.OUTER + 1;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};

    private final SectionAxis stack;
    private final int[][] us;
    private final int[][] vs;
    private final RingKind[][] kinds;
    private final List<List<List<SectionOffset>>> floors = new ArrayList<>();
    /** The ring and index of each section offset, packed as ring times 256 plus index, or -1. */
    private final int[] lookup = new int[SIZE * SIZE];
    private final List<SectionBlock> section;
    private final double[] resistances = new double[SIZE * SIZE];
    private final List<List<PatchColumn>> patchColumns = new ArrayList<>(4);

    /**
     * @param stack  the frame offset that takes v
     * @param starts the start block (u, v) of each ring
     */
    protected TracedRing(SectionAxis stack, int[][] starts) {
        this.stack = stack;
        Arrays.fill(this.lookup, -1);
        this.us = new int[starts.length][];
        this.vs = new int[starts.length][];
        this.kinds = new RingKind[starts.length][];
        for (int r = 0; r < starts.length; r++) {
            List<int[]> ring = this.trace(starts[r][0], starts[r][1]);
            this.us[r] = new int[ring.size()];
            this.vs[r] = new int[ring.size()];
            this.kinds[r] = new RingKind[ring.size()];
            List<List<SectionOffset>> ringFloors = new ArrayList<>(ring.size());
            for (int index = 0; index < ring.size(); index++) {
                int u = ring.get(index)[0];
                int v = ring.get(index)[1];
                this.us[r][index] = u;
                this.vs[r][index] = v;
                this.classify(r, index, u, v, ringFloors);
                this.lookup[cell(this.firstOf(u, v), this.secondOf(u, v))] = r * 256 + index;
            }
            this.floors.add(List.copyOf(ringFloors));
        }
        List<SectionBlock> blocks = new ArrayList<>();
        for (int first = -Ring.OUTER; first <= Ring.OUTER; first++) {
            for (int second = -Ring.OUTER; second <= Ring.OUTER; second++) {
                int u = this.uOf(first, second);
                int v = this.vOf(first, second);
                if (this.solid(u, v)) {
                    blocks.add(new SectionBlock(first, second, this.lookup[cell(first, second)] >= 0));
                    this.resistances[cell(first, second)] = this.resistanceAt(u, v);
                }
            }
        }
        this.section = List.copyOf(blocks);
        for (SectionAxis normal : SectionAxis.values()) {
            for (int sign : new int[]{1, -1}) {
                this.patchColumns.add(this.tracePatchColumns(normal, sign));
            }
        }
    }

    /** True when the block (u, v) of the cross section is solid. */
    protected abstract boolean solid(int u, int v);

    /** The erosion resistance of the solid block (u, v). */
    protected abstract double resistanceAt(int u, int v);

    /** The kind of a ring block that is neither convex nor concave. */
    protected RingKind faceKind(int u, int v) {
        return RingKind.FACE;
    }

    /** The frame offset that takes v. */
    public SectionAxis stack() {
        return this.stack;
    }

    /** The frame offset that takes u. */
    public SectionAxis cross() {
        return this.stack == SectionAxis.FIRST ? SectionAxis.SECOND : SectionAxis.FIRST;
    }

    protected final int firstOf(int u, int v) {
        return this.stack == SectionAxis.FIRST ? v : u;
    }

    protected final int secondOf(int u, int v) {
        return this.stack == SectionAxis.FIRST ? u : v;
    }

    protected final int uOf(int first, int second) {
        return this.stack == SectionAxis.FIRST ? second : first;
    }

    protected final int vOf(int first, int second) {
        return this.stack == SectionAxis.FIRST ? first : second;
    }

    /** The offset u of the ring block. */
    public int ringU(int ring, int index) {
        return this.us[ring][Math.floorMod(index, this.us[ring].length)];
    }

    /** The offset v of the ring block. */
    public int ringV(int ring, int index) {
        return this.vs[ring][Math.floorMod(index, this.vs[ring].length)];
    }

    /** The ring index of the block (u, v) on the ring, or -1. */
    protected final int indexUV(int ring, int u, int v) {
        return this.index(ring, this.firstOf(u, v), this.secondOf(u, v));
    }

    /** The node index of the ring block (u, v), which sits on an even ring index. */
    protected final int nodeUV(int ring, int u, int v) {
        int index = this.indexUV(ring, u, v);
        if (index < 0 || (index & 1) != 0) {
            throw new IllegalStateException("The block (" + u + ", " + v + ") is no node of ring " + ring);
        }
        return this.node(ring, index);
    }

    private static int cell(int first, int second) {
        return (first + Ring.OUTER) * SIZE + second + Ring.OUTER;
    }

    private boolean onSurface(int u, int v) {
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                if ((du != 0 || dv != 0) && !this.solid(u + du, v + dv)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRing(int u, int v) {
        return this.solid(u, v) && this.onSurface(u, v);
    }

    private List<int[]> trace(int startU, int startV) {
        List<int[]> ring = new ArrayList<>();
        boolean[] seen = new boolean[SIZE * SIZE];
        int u = startU;
        int v = startV;
        int previousU = u - 1;
        int previousV = v;
        while (true) {
            ring.add(new int[]{u, v});
            seen[cell(u, v)] = true;
            int[] next = null;
            for (int[] direction : DIRECTIONS) {
                int nu = u + direction[0];
                int nv = v + direction[1];
                if ((nu != previousU || nv != previousV) && this.isRing(nu, nv) && !seen[cell(nu, nv)]) {
                    next = new int[]{nu, nv};
                    break;
                }
            }
            if (next == null) {
                return ring;
            }
            previousU = u;
            previousV = v;
            u = next[0];
            v = next[1];
        }
    }

    private void classify(int ring, int index, int u, int v, List<List<SectionOffset>> ringFloors) {
        int air = 0;
        int normalU = 0;
        int normalV = 0;
        for (int[] direction : DIRECTIONS) {
            if (!this.solid(u + direction[0], v + direction[1])) {
                air++;
                normalU += direction[0];
                normalV += direction[1];
            }
        }
        List<SectionOffset> blockFloors = new ArrayList<>(2);
        if (air == 0) {
            this.kinds[ring][index] = RingKind.CONCAVE_EDGE;
            for (int[] direction : DIRECTIONS) {
                int fu = u + direction[0];
                int fv = v + direction[1];
                if (this.solid(fu, fv) && !this.onSurface(fu, fv)) {
                    blockFloors.add(new SectionOffset(this.firstOf(fu, fv), this.secondOf(fu, fv)));
                }
            }
        } else {
            if (air >= 2) {
                this.kinds[ring][index] = (index & 1) == 0 ? RingKind.CORNER_NODE : RingKind.CONVEX_EDGE;
            } else {
                this.kinds[ring][index] = this.faceKind(u, v);
            }
            int fu = u - normalU;
            int fv = v - normalV;
            if (this.solid(fu, fv)) {
                blockFloors.add(new SectionOffset(this.firstOf(fu, fv), this.secondOf(fu, fv)));
            }
        }
        ringFloors.add(List.copyOf(blockFloors));
    }

    /** Walks from the patch face inward along the normal, per cross offset, to the first ring block. */
    private List<PatchColumn> tracePatchColumns(SectionAxis normal, int sign) {
        List<PatchColumn> columns = new ArrayList<>(2 * Ring.PATCH_EDGE + 1);
        for (int cross = -Ring.PATCH_EDGE; cross <= Ring.PATCH_EDGE; cross++) {
            PatchColumn found = PatchColumn.NONE;
            for (int depth = Ring.OUTER; depth >= -Ring.OUTER && !found.present(); depth--) {
                int first = normal == SectionAxis.FIRST ? sign * depth : cross;
                int second = normal == SectionAxis.FIRST ? cross : sign * depth;
                int packed = this.lookup[cell(first, second)];
                if (packed >= 0) {
                    found = new PatchColumn(packed / 256, packed % 256);
                }
            }
            columns.add(found);
        }
        return List.copyOf(columns);
    }

    @Override
    public int rings() {
        return this.us.length;
    }

    @Override
    public int length(int ring) {
        return this.us[ring].length;
    }

    @Override
    public int first(int ring, int index) {
        return this.firstOf(this.ringU(ring, index), this.ringV(ring, index));
    }

    @Override
    public int second(int ring, int index) {
        return this.secondOf(this.ringU(ring, index), this.ringV(ring, index));
    }

    @Override
    public int index(int ring, int first, int second) {
        if (Math.abs(first) > Ring.OUTER || Math.abs(second) > Ring.OUTER) {
            return -1;
        }
        int packed = this.lookup[cell(first, second)];
        return packed >= 0 && packed / 256 == ring ? packed % 256 : -1;
    }

    @Override
    public RingKind kind(int ring, int index) {
        return this.kinds[ring][Math.floorMod(index, this.kinds[ring].length)];
    }

    @Override
    public List<SectionOffset> floors(int ring, int index) {
        List<List<SectionOffset>> ringFloors = this.floors.get(ring);
        return ringFloors.get(Math.floorMod(index, ringFloors.size()));
    }

    @Override
    public List<SectionBlock> section() {
        return this.section;
    }

    @Override
    public List<PatchColumn> patchColumns(SectionAxis normal, int sign) {
        return this.patchColumns.get(2 * normal.ordinal() + (sign > 0 ? 0 : 1));
    }

    @Override
    public double resistance(int first, int second) {
        if (Math.abs(first) > Ring.OUTER || Math.abs(second) > Ring.OUTER) {
            return 0;
        }
        return this.resistances[cell(first, second)];
    }

    /** The number of solid blocks from (u, v) to the first air block, in one direction. */
    protected final int run(int u, int v, int du, int dv) {
        int n = 0;
        while (this.solid(u + du * (n + 1), v + dv * (n + 1))) {
            n++;
        }
        return n;
    }

    /** The resistance of a plate block: its run to the nearest air along the plate thickness over the layers to the middle, at most 1. */
    protected final double plateResistance(int u, int v, boolean alongV, int half) {
        int run = alongV
                ? Math.min(this.run(u, v, 0, 1), this.run(u, v, 0, -1))
                : Math.min(this.run(u, v, 1, 0), this.run(u, v, -1, 0));
        return Math.min(1, (double) run / half);
    }

}
