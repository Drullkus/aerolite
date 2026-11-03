package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.shaft.MixedRandomFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Small synthetic patterns on pillar bands, and the scene of a window with surface paths and loop rationing.
 *
 * <p>The tiles pattern is the small tile layout with stagger x of the tiles prototype: ring grooves on every fourth row,
 * axis grooves at face columns 3, 7 and 11 on even courses and 1, 5 and 9 on odd courses. It has T junctions only.
 *
 * <p>The ladder pattern has two axis grooves at ring positions 3 and 9 and two rungs in each odd chunk.
 * So each tree segment holds two components, and the edges between the rungs span three chunks.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
final class TestPatterns {

    static final int CHUNK = LoopRationing.SEGMENT_LENGTH;
    static final int MARGIN = LoopRationing.margin(LoopRationing.DEFAULT_LOOK);
    static final double CHANCE = 0.75;

    private TestPatterns() {
    }

    enum Pattern { TILES, LADDER }

    enum Mode { PLAIN, SURFACE, CUT }

    record Params(Pattern pattern, Mode mode, long seed, int pillar, double share, boolean allowOrphans) {
        Params withShare(double value) {
            return new Params(this.pattern, this.mode, this.seed, this.pillar, value, this.allowOrphans);
        }

        PathFit.Rules rules() {
            return (this.mode == Mode.CUT ? PathFit.Rules.CUT : PathFit.Rules.SURFACE).withOrphans(this.allowOrphans);
        }
    }

    record Scene(Band band, LoopRationing.Result result) {
    }

    static boolean tileGroove(int ring, int axis) {
        if (Math.floorMod(axis, 4) == 3) {
            return true;
        }
        int column = Math.floorMod(ring, 12);
        boolean evenCourse = Math.floorMod(Math.floorDiv(axis, 4), 2) == 0;
        return evenCourse ? column == 3 || column == 7 || column == 11 : column == 1 || column == 5 || column == 9;
    }

    /** The tiles base band of the chunks {@code [lo, hi)}. Nodes sit on odd rows and odd ring positions. */
    static Band tiles(int lo, int hi) {
        Band band = Band.pillar(lo * CHUNK, (hi - lo) * CHUNK);
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            for (int ring = 0; ring < band.ringLength(); ring++) {
                boolean groove = tileGroove(ring, axis);
                if (groove) {
                    band.set(ring, axis, BandKind.GROOVE);
                }
                if ((axis & 1) != 0 && (ring & 1) != 0) {
                    (groove ? band.grooveNodes() : band.faceNodes()).add(band.pos(ring, axis));
                }
            }
        }
        return band;
    }

    /** The ladder base band of the chunks {@code [lo, hi)}. */
    static Band ladder(int lo, int hi) {
        Band band = Band.pillar(lo * CHUNK, (hi - lo) * CHUNK);
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            band.set(3, axis, BandKind.GROOVE);
            band.set(9, axis, BandKind.GROOVE);
            int row = Math.floorMod(axis, CHUNK);
            if (Math.floorMod(Math.floorDiv(axis, CHUNK), 2) == 1 && (row == 4 || row == 10)) {
                for (int ring = 4; ring < 9; ring++) {
                    band.set(ring, axis, BandKind.GROOVE);
                }
            }
        }
        return band;
    }

    static Band base(Pattern pattern, int lo, int hi) {
        return pattern == Pattern.TILES ? tiles(lo, hi) : ladder(lo, hi);
    }

    /** The regions of the tiles of one course, by ring position. */
    static List<LongSet> courseTiles(Band band, int course) {
        int top = course * 4;
        int first = Math.floorMod(course, 2) == 0 ? 4 : 2;
        List<LongSet> out = new ArrayList<>();
        for (int start = first; start < first + band.ringLength(); start += 4) {
            LongSet region = new LongOpenHashSet();
            for (int ring = start; ring < start + 3; ring++) {
                for (int axis = top; axis < top + 3; axis++) {
                    region.add(band.pos(ring, axis));
                }
            }
            out.add(region);
        }
        return out;
    }

    /**
     * The surface paths of one chunk: one path at most in each tile of the courses 1 and 2 of the chunk.
     * No path of those courses touches a block of another chunk. So the paths of a chunk need no other chunk.
     */
    static List<SurfacePath> chunkPaths(Params params, int c) {
        Band band = tiles(c - 1, c + 2);
        RandomSource random = new MixedRandomFactory(params.seed()).fork("paths").at(params.pillar(), c, 0);
        List<SurfacePath> out = new ArrayList<>();
        for (int course = 4 * c + 1; course <= 4 * c + 2; course++) {
            for (LongSet region : courseTiles(band, course)) {
                if (random.nextFloat() >= CHANCE) {
                    continue;
                }
                LongSet nodes = new LongOpenHashSet();
                band.faceNodes().forEach(pos -> {
                    if (region.contains(pos)) {
                        nodes.add(pos);
                    }
                });
                SurfacePath path = PathFit.pick(band, SurfaceCandidates.of(band, region, nodes), random, params.rules());
                if (path != null) {
                    PathFit.place(band, path, params.mode() == Mode.CUT);
                    out.add(path);
                }
            }
        }
        return out;
    }

    /** The band of the chunks {@code [lo, hi)} after surface paths and loop rationing, built with a margin on each side. */
    static Scene scene(Params params, int lo, int hi) {
        Band band = base(params.pattern(), lo - MARGIN, hi + MARGIN);
        LongSet pins = new LongOpenHashSet();
        if (params.pattern() == Pattern.TILES && params.mode() != Mode.PLAIN) {
            for (int c = lo - MARGIN; c < hi + MARGIN; c++) {
                for (SurfacePath path : chunkPaths(params, c)) {
                    PathFit.place(band, path, params.mode() == Mode.CUT);
                    if (params.mode() == Mode.SURFACE) {
                        pins.addAll(path.ends());
                    }
                }
            }
        }
        MixedRandomFactory segments = new MixedRandomFactory(params.seed()).fork("segment");
        LoopRationing.Result result = LoopRationing.ration(band, pins, lo - 2, hi + 2,
            s -> segments.at(params.pillar(), s, 0), params.share());
        return new Scene(band.crop(lo * CHUNK, (hi - lo) * CHUNK), result);
    }

    /**
     * The cycles of the kept edges inside the chunks {@code [lo, hi)}, and the component counts of the vertices there.
     * The counts join vertices through the whole graph, before and after the rationing. So a path that leaves the range still joins two vertices.
     *
     * @return the cycles, the full count and the kept count
     */
    static int[] vertexForest(GrooveGraph graph, LongList removed, int lo, int hi) {
        LongSet cut = new LongOpenHashSet(removed);
        LongList inside = new LongArrayList();
        for (long vertex : graph.vertices()) {
            if (inChunks(vertex, lo, hi)) {
                inside.add(vertex);
            }
        }
        LongUnionFind full = new LongUnionFind();
        LongUnionFind kept = new LongUnionFind();
        LongUnionFind local = new LongUnionFind();
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

}
