package us.drullk.aerolite.world.shaft.pattern.chaos;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandDirection;
import us.drullk.aerolite.world.shaft.surface.path.BandPos;
import us.drullk.aerolite.world.shaft.surface.path.GrooveEdge;
import us.drullk.aerolite.world.shaft.surface.path.GrooveGraph;
import us.drullk.aerolite.world.shaft.surface.path.LongUnionFind;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;

import javax.annotation.Nullable;

/**
 * The scenes and the checks of the chaos prototype.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class ChaosChecks {

    static final int CHUNK = ChaosParams.CHUNK;
    /** The box center of the box scenes, as in the prototype. */
    static final int BOX_AT = 89;
    private static final int FACE = 12;
    private static final int PATCH_HALF = 4;
    private static final int BOX_HALF = 7;

    private ChaosChecks() {
    }

    /**
     * One parameter set of a check.
     *
     * @param box true puts one junction box at {@link #BOX_AT}, with the patches on faces 1 and 3 of the pillar ring
     */
    record Case(ChaosSettings.Cells cells, int spacing, double jitter, int radius, boolean surface, boolean cut, double share, int ring, long seed,
                boolean box) {

        static Case voronoi(int spacing, double jitter, long seed) {
            return new Case(ChaosSettings.Cells.VORONOI, spacing, jitter, 1, false, false, 1, 48, seed, false);
        }

        Case withCells(ChaosSettings.Cells cells) {
            return new Case(cells, this.spacing, this.jitter, this.radius, this.surface, this.cut, this.share, this.ring, this.seed, this.box);
        }

        Case withRadius(int radius) {
            return new Case(this.cells, this.spacing, this.jitter, radius, this.surface, this.cut, this.share, this.ring, this.seed, this.box);
        }

        Case withPaths(boolean surface, boolean cut) {
            return new Case(this.cells, this.spacing, this.jitter, this.radius, surface, cut, this.share, this.ring, this.seed, this.box);
        }

        Case withShare(double share) {
            return new Case(this.cells, this.spacing, this.jitter, this.radius, this.surface, this.cut, share, this.ring, this.seed, this.box);
        }

        Case withRing(int ring) {
            return new Case(this.cells, this.spacing, this.jitter, this.radius, this.surface, this.cut, this.share, ring, this.seed, this.box);
        }

        Case withBox() {
            return new Case(this.cells, this.spacing, this.jitter, this.radius, this.surface, this.cut, this.share, this.ring, this.seed, true);
        }

        ChaosParams params() {
            return new ChaosParams(this.cells, this.spacing, Math.min(this.jitter, ChaosParams.jitterCap(this.spacing)), this.radius, this.surface || this.cut,
                    this.cut, this.share, 0.75, this.ring);
        }

        @Override
        public String toString() {
            return this.cells.getSerializedName() + " ring " + this.ring + " spacing " + this.spacing + " jitter " + this.jitter + " radius " + this.radius
                    + (this.cut ? " cut" : this.surface ? " surface" : "") + " loops " + this.share + " seed " + this.seed + (this.box ? " box" : "");
        }
    }

    /** The scene of the chunks {@code [lo, hi)} of an unbounded pillar. */
    static ChaosScene.Result scene(Case c, int lo, int hi) {
        ChaosScene.BoxMask boxes = c.box() ? ChaosChecks::prototypeBox : ChaosScene.BoxMask.NONE;
        return new ChaosScene(c.params(), new ChaosSeeds(c.seed(), 0), boxes, -1000, 1000).compute(lo, hi);
    }

    /** The box of the prototype on the pillar ring: the box rows without the 9x9 patches of faces 1 and 3. */
    private static boolean[] prototypeBox(int ring, int axis0, int rows) {
        boolean[] mask = new boolean[ring * rows];
        for (int r = 0; r < ring; r++) {
            boolean patchColumn = Math.abs(r % FACE - FACE / 2) <= PATCH_HALF && (r / FACE == 1 || r / FACE == 3);
            for (int y = 0; y < rows; y++) {
                int offset = Math.abs(axis0 + y - BOX_AT);
                mask[r * rows + y] = offset <= BOX_HALF && !(patchColumn && offset <= PATCH_HALF);
            }
        }
        return mask;
    }

    private static boolean raised(Band band, long pos) {
        return band.get(pos).isWallLayer();
    }

    /** Every wall layer block without a wall layer neighbor. A neighbor outside the band counts as wall layer. */
    static LongList orphans(Band band) {
        LongList out = new LongArrayList();
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            for (int r = 0; r < band.ringLength(); r++) {
                long pos = band.pos(r, axis);
                if (!raised(band, pos)) {
                    continue;
                }
                boolean any = false;
                for (BandDirection d : BandDirection.values()) {
                    long q = band.step(pos, d);
                    any |= q == BandPos.NONE || raised(band, q);
                }
                if (!any) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    /** The count of faces that do not touch the band ends and hold no square of wall layer blocks with the side. */
    static int facesUnder(Band band, int side) {
        LongSet seen = new LongOpenHashSet();
        int under = 0;
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            for (int r = 0; r < band.ringLength(); r++) {
                long start = band.pos(r, axis);
                if (!raised(band, start) || !seen.add(start)) {
                    continue;
                }
                LongSet face = new LongOpenHashSet();
                face.add(start);
                LongArrayList stack = LongArrayList.of(start);
                boolean touches = false;
                while (!stack.isEmpty()) {
                    long pos = stack.popLong();
                    int a = BandPos.axis(pos);
                    touches |= a == band.axis0() || a == band.axisEnd() - 1;
                    for (long q : band.neighbors(pos)) {
                        if (raised(band, q) && seen.add(q)) {
                            face.add(q);
                            stack.add(q);
                        }
                    }
                }
                if (!touches && !hasSquare(band, face, side)) {
                    under++;
                }
            }
        }
        return under;
    }

    private static boolean hasSquare(Band band, LongSet face, int side) {
        for (long pos : face) {
            boolean all = true;
            for (int i = 0; i < side && all; i++) {
                for (int j = 0; j < side && all; j++) {
                    all = face.contains(band.pos(BandPos.ring(pos) + i, BandPos.axis(pos) + j));
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cycles of the kept edges inside the chunks {@code [lo, hi)}, and the component counts of the vertices there, before and after the rationing.
     *
     * @return the cycles, the full count and the kept count
     */
    static int[] vertexForest(GrooveGraph graph, LongList removed, int lo, int hi) {
        LongSet cut = new LongOpenHashSet(removed);
        LongList inside = new LongArrayList();
        LongUnionFind full = new LongUnionFind();
        LongUnionFind kept = new LongUnionFind();
        LongUnionFind local = new LongUnionFind();
        for (long vertex : graph.vertices()) {
            full.find(vertex);
            kept.find(vertex);
            if (inChunks(vertex, lo, hi)) {
                inside.add(vertex);
            }
        }
        int cycles = 0;
        for (GrooveEdge edge : graph.edges()) {
            full.union(edge.u(), edge.v());
            if (edge.chain().longStream().noneMatch(cut::contains)) {
                kept.union(edge.u(), edge.v());
                if (inChunks(edge.u(), lo, hi) && inChunks(edge.v(), lo, hi) && !local.union(edge.u(), edge.v())) {
                    cycles++;
                }
            }
        }
        return new int[] {cycles, full.count(inside), kept.count(inside)};
    }

    private static boolean inChunks(long pos, int lo, int hi) {
        int chunk = LoopRationing.segmentOf(pos);
        return chunk >= lo && chunk < hi;
    }

    /** A short text of the first positions of a list, for an assertion message. */
    static String first(@Nullable LongList list) {
        if (list == null || list.isEmpty()) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(4, list.size()); i++) {
            out.append(BandPos.toString(list.getLong(i))).append(' ');
        }
        return out.toString();
    }

}
