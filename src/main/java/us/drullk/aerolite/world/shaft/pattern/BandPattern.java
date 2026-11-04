package us.drullk.aerolite.world.shaft.pattern;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.maze.Ring;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SectionAxis;
import us.drullk.aerolite.world.shaft.surface.SegmentSurface;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.BandPos;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;
import us.drullk.aerolite.world.shaft.surface.path.PathFit;
import us.drullk.aerolite.world.shaft.surface.path.SurfaceCandidates;
import us.drullk.aerolite.world.shaft.surface.path.SurfacePath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A groove pattern on the bands of a pillar, with surface paths in its regions and the loop share rationing.
 * Bricks, Tiles and Scales extend it.
 *
 * <p>A scene of a chunk range builds the layout with a margin of {@link #MARGIN} chunks on each side.
 * Then it places the surface paths of each chunk and rations the cycles segment by segment.
 * The surface paths of a chunk are a pure function of the seed, the pillar and the chunk:
 * the last regions of a chunk draw first from their own random, on the layout of the chunk and its two neighbors.
 * The other regions of the chunk then fit around the last regions of the chunk and of the chunk before it.
 * So every chunk of a scene is the same for any scene that holds it.
 *
 * <p>A junction box is box on every ring position of its rows, except its patch area.
 * The patch area is the 9x9 square inside the bezel of the patch face. The pattern continues there.
 * The bezel and the box edges cut the grooves. The patch belongs to the segment that holds every chunk of its box.
 *
 * <p>A segment is one chunk, or the chunks that one or more junction boxes touch.
 * A segment surface copies the grooves and the surface paths of its chunks. The extra row after the segment stays wall.
 * The {@link PatternCache} keeps the path draws and the finished band of each chunk, so a neighbor chunk reads them again.
 * A pattern of a short axis range, such as a column, builds one scene for all of it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public abstract class BandPattern implements SurfacePattern {

    /** The blocks of one chunk along the axis. */
    public static final int CHUNK = LoopRationing.SEGMENT_LENGTH;
    /** The chunks of margin on each side of a scene. */
    public static final int MARGIN = LoopRationing.margin(LoopRationing.DEFAULT_LOOK);
    /** The longest axis range, in chunks, that one scene covers whole. A column fits. */
    private static final int WHOLE_SPAN = 64;
    /** The chunks on each side of a segment that one scene also builds. */
    private static final int WINDOW_REACH = 2;
    private static final int NO_CROSS = Integer.MIN_VALUE;

    // The first coordinate of each positional random. The maze uses 0 and 1.
    protected static final int SALT_FLIP = 101;
    protected static final int SALT_TAIL = 102;
    protected static final int SALT_HEIGHTS = 103;
    protected static final int SALT_JOINTS_LAST = 104;
    protected static final int SALT_JOINTS = 105;
    private static final int SALT_PATHS_LAST = 106;
    private static final int SALT_PATHS = 107;
    private static final int SALT_SEGMENT = 108;
    private static final int SALT_FINGERPRINT = 109;

    private final PatternContext context;
    private final SurfacePathSettings paths;
    private final PatternCache cache;
    private final List<Long> fingerprint;
    private final RingFaces[] faces;

    /**
     * @param cache the draws and chunk bands that outlive this pattern object
     */
    protected BandPattern(PatternContext context, SurfacePathSettings paths, PatternCache cache) {
        this.context = context;
        this.paths = paths;
        this.cache = cache;
        this.fingerprint = List.of(context.random().at(SALT_FINGERPRINT, 0, 0).nextLong(), context.random().at(SALT_FINGERPRINT, 1, 0).nextLong());
        this.faces = new RingFaces[context.ring().rings()];
        for (int r = 0; r < this.faces.length; r++) {
            this.faces[r] = RingFaces.of(context.ring(), r);
        }
    }

    /** The pattern class and every option that changes its blocks, for the keys of the cache. */
    protected abstract Object options();

    /** Paints the grooves and the node sets of the layout over the axis range of the all-wall band. */
    protected abstract void layout(Band band, int ring);

    /** The regions of the chunk, in their draw sequence. */
    protected abstract List<PatternRegion> regions(int ring, int chunk);

    /** The tail blocks in the axis range of the band. A tail never carves, unless a cut path runs over it. */
    protected LongSet tails(Band band, int ring) {
        return LongSets.EMPTY_SET;
    }

    /** True when a cut path can leave a wall block with grooves on all four sides. */
    protected boolean allowOrphans() {
        return false;
    }

    /** True when a surface path can run over the tail of its region. */
    protected boolean pathsOverTails() {
        return false;
    }

    public PatternContext context() {
        return this.context;
    }

    public SurfacePathSettings paths() {
        return this.paths;
    }

    protected RingFaces faces(int ring) {
        return this.faces[ring];
    }

    /** True when the pattern places surface paths. */
    public boolean placesPaths() {
        return this.paths.surfacePaths();
    }

    /** True when the surface paths carve grooves. */
    public boolean cuts() {
        return this.paths.surfacePaths() && this.paths.cutting();
    }

    /** The loop share. Loops off gives 0. */
    public double share() {
        return this.paths.allowLoops() ? this.paths.loopShare() : 0;
    }

    @Override
    public RingShape ring() {
        return this.context.ring();
    }

    @Override
    public float bias() {
        return this.context.bias();
    }

    @Override
    public int minChunk() {
        return this.context.minChunk();
    }

    @Override
    public int maxChunk() {
        return this.context.maxChunk();
    }

    /** The random of one stream at one chunk of one ring. */
    protected RandomSource random(int salt, int chunk, int ring) {
        return this.context.random().at(salt, chunk, ring);
    }

    /** One bit of the pillar random. */
    protected boolean pillarBit(int salt) {
        return this.context.random().at(salt, 0, 0).nextBoolean();
    }

    /** The packed band position of a ring position, wrapped to the ring of the index. */
    protected long pos(int ring, int index, int along) {
        return BandPos.pack(Math.floorMod(index, this.context.ring().length(ring)), along);
    }

    /** The layout of the ring over the chunks {@code [firstChunk, endChunk)}, with the junction boxes. */
    public Band base(int ring, int firstChunk, int endChunk) {
        Band band = this.context.ring().band(ring, CHUNK * firstChunk, CHUNK * (endChunk - firstChunk));
        this.layout(band, ring);
        for (ShaftMaze.Box box : this.context.boxes().boxes(band.axis0() - Ring.BOX, band.axisEnd() - 1 + Ring.BOX)) {
            int from = Math.max(band.axis0(), box.center() - Ring.BOX);
            int to = Math.min(band.axisEnd() - 1, box.center() + Ring.BOX);
            for (int along = from; along <= to; along++) {
                for (int index = 0; index < band.ringLength(); index++) {
                    if (!this.isPatchCell(ring, index, along, box)) {
                        long pos = band.pos(index, along);
                        band.set(pos, BandKind.BOX);
                        band.grooveNodes().remove(pos);
                        band.faceNodes().remove(pos);
                    }
                }
            }
        }
        return band;
    }

    /** True when the ring position at the axis position lies in the patch area of the box. */
    public boolean isPatchCell(int ring, int index, int along, ShaftMaze.Box box) {
        return Math.abs(along - box.center()) <= Ring.PATCH_EDGE && this.patchCross(ring, index, box) != NO_CROSS;
    }

    /** The offset across the patch face of a ring position on the patch area, else {@link #NO_CROSS}. */
    private int patchCross(int ring, int index, ShaftMaze.Box box) {
        RingShape shape = this.context.ring();
        boolean second = ShaftMaze.normal(box) == SectionAxis.SECOND;
        int normal = second ? shape.second(ring, index) : shape.first(ring, index);
        int cross = second ? shape.first(ring, index) : shape.second(ring, index);
        return normal == box.sign() * Ring.OUTER && Math.abs(cross) <= Ring.PATCH_EDGE ? cross : NO_CROSS;
    }

    /** The band of the ring over the chunks {@code [firstChunk, lastChunk]} after the surface paths and the rationing. */
    public PatternScene scene(int ring, int firstChunk, int lastChunk) {
        int lo = firstChunk - MARGIN;
        int end = lastChunk + 1 + MARGIN;
        Band band = this.base(ring, lo, end);
        int axis0 = CHUNK * firstChunk;
        int length = CHUNK * (lastChunk - firstChunk + 1);
        Band base = band.crop(axis0, length);
        LongSet tails = this.tails(band, ring);
        LongSet fixed = new LongOpenHashSet(tails);
        if (this.placesPaths()) {
            PathDraws draws = new PathDraws(ring);
            for (int c = lo; c < end; c++) {
                for (SurfacePath path : draws.chunk(c)) {
                    PathFit.place(band, path, this.cuts());
                    if (!this.cuts()) {
                        fixed.addAll(path.ends());
                    }
                }
            }
        }
        LoopRationing.Result result = LoopRationing.ration(band, fixed, firstChunk - 2, lastChunk + 3,
                s -> this.random(SALT_SEGMENT, s, ring), this.share());
        Band out = band.crop(axis0, length);
        LongSet inside = new LongOpenHashSet();
        tails.forEach(pos -> {
            if (out.inside(pos)) {
                inside.add(pos);
            }
        });
        return new PatternScene(out, base, result, inside);
    }

    /** The key of a cache entry. The boxes are those whose box covers a block of the chunks {@code [firstReach, lastReach]}. */
    private PatternCache.Key key(PatternCache.Kind kind, int ring, int chunk, int firstReach, int lastReach) {
        List<ShaftMaze.Box> boxes = this.boxesTouching(firstReach, lastReach);
        return new PatternCache.Key(List.of(this.options(), this.ring()), this.fingerprint, kind, ring, chunk, boxes);
    }

    /** The surface paths of the chunks of one scene, each drawn once. */
    private final class PathDraws {

        private final int ring;
        private final Int2ObjectMap<Band> neighborhoods = new Int2ObjectOpenHashMap<>();

        PathDraws(int ring) {
            this.ring = ring;
        }

        /** A copy of the layout of the chunk and its two neighbors. */
        Band neighborhood(int chunk) {
            return this.neighborhoods.computeIfAbsent(chunk, c -> BandPattern.this.base(this.ring, c - 1, c + 2)).copy();
        }

        /** The surface paths of the last regions of the chunk. They need no other chunk. */
        List<SurfacePath> last(int chunk) {
            return BandPattern.this.cache.get(BandPattern.this.key(PatternCache.Kind.LAST_PATHS, this.ring, chunk, chunk - 1, chunk + 1),
                    () -> BandPattern.this.choose(this.neighborhood(chunk), this.ring,
                            BandPattern.this.regions(this.ring, chunk).stream().filter(PatternRegion::last).toList(),
                            BandPattern.this.random(SALT_PATHS_LAST, chunk, this.ring)));
        }

        /** The surface paths of the chunk: the last regions first, then the other regions around them. */
        List<SurfacePath> chunk(int chunk) {
            return BandPattern.this.cache.get(BandPattern.this.key(PatternCache.Kind.CHUNK_PATHS, this.ring, chunk, chunk - 2, chunk + 1),
                    () -> this.draw(chunk));
        }

        private List<SurfacePath> draw(int chunk) {
            Band band = this.neighborhood(chunk);
            for (SurfacePath path : this.last(chunk - 1)) {
                PathFit.place(band, path, BandPattern.this.cuts());
            }
            for (SurfacePath path : this.last(chunk)) {
                PathFit.place(band, path, BandPattern.this.cuts());
            }
            List<SurfacePath> out = new ArrayList<>(this.last(chunk));
            out.addAll(BandPattern.this.choose(band, this.ring,
                    BandPattern.this.regions(this.ring, chunk).stream().filter(region -> !region.last()).toList(),
                    BandPattern.this.random(SALT_PATHS, chunk, this.ring)));
            return List.copyOf(out);
        }
    }

    /** Picks and places the surface paths of the regions in their sequence. */
    private List<SurfacePath> choose(Band band, int ring, List<PatternRegion> regions, RandomSource random) {
        boolean over = this.pathsOverTails();
        PathFit.Rules rules = (this.cuts() ? PathFit.Rules.CUT : PathFit.Rules.SURFACE)
                .withOrphans(this.allowOrphans())
                .withExempt(over ? this.tails(band, ring) : LongSets.EMPTY_SET);
        List<SurfacePath> out = new ArrayList<>();
        for (PatternRegion region : regions) {
            if (random.nextDouble() >= this.paths.pathChance()) {
                continue;
            }
            LongSet cells = new LongOpenHashSet();
            region.blocks().forEach(pos -> {
                if (band.inside(pos) && band.get(pos) != BandKind.BOX) {
                    cells.add(pos);
                }
            });
            long tail = region.tail();
            if (over && band.inside(tail) && band.get(tail) == BandKind.WALL) {
                cells.add(tail);
            }
            LongSet nodes = new LongOpenHashSet();
            band.faceNodes().forEach(pos -> {
                if (cells.contains(pos) && pos != tail) {
                    nodes.add(pos);
                }
            });
            SurfacePath path = PathFit.pick(band, SurfaceCandidates.of(band, cells, nodes), random, rules);
            if (path != null) {
                PathFit.place(band, path, this.cuts());
                out.add(path);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public SegmentSurface segmentAt(int chunk) {
        int lo = chunk;
        int hi = chunk;
        List<ShaftMaze.Box> members;
        while (true) {
            members = this.boxesTouching(lo, hi);
            int nextLo = lo;
            int nextHi = hi;
            for (ShaftMaze.Box box : members) {
                nextLo = Math.min(nextLo, Math.floorDiv(box.center() - Ring.BOX, CHUNK));
                nextHi = Math.max(nextHi, Math.floorDiv(box.center() + Ring.BOX, CHUNK));
            }
            if (nextLo == lo && nextHi == hi) {
                break;
            }
            lo = nextLo;
            hi = nextHi;
        }
        lo = Math.max(lo, this.minChunk());
        hi = Math.min(hi, this.maxChunk());
        SegmentSurface surface = new SegmentSurface(this.ring(), lo, hi, members);
        for (int r = 0; r < this.ring().rings(); r++) {
            for (int c = lo; c <= hi; c++) {
                this.copy(surface, r, this.chunkBand(r, c, lo, hi));
            }
        }
        return surface;
    }

    /** The boxes that cover at least one block of the chunks {@code [lo, hi]}, by center. */
    private List<ShaftMaze.Box> boxesTouching(int lo, int hi) {
        List<ShaftMaze.Box> out = new ArrayList<>(this.context.boxes().boxes(CHUNK * lo - Ring.BOX, CHUNK * hi + CHUNK - 1 + Ring.BOX));
        out.sort(Comparator.comparingInt(ShaftMaze.Box::center));
        return out;
    }

    private PatternCache.Key bandKey(int ring, int chunk) {
        return this.key(PatternCache.Kind.CHUNK_BAND, ring, chunk, chunk - MARGIN - 2, chunk + MARGIN + 2);
    }

    /**
     * The finished band of one chunk. A missing band builds the scene of the segment {@code [lo, hi]} and keeps each chunk of it.
     * A pattern of a short axis range builds one scene for all of it.
     */
    private Band chunkBand(int ring, int chunk, int lo, int hi) {
        Band band = this.cache.find(this.bandKey(ring, chunk));
        if (band != null) {
            return band;
        }
        int first;
        int last;
        if ((long) this.maxChunk() - this.minChunk() < WHOLE_SPAN) {
            first = this.minChunk();
            last = this.maxChunk();
        } else {
            // Neighbor chunks along the pillar read the kept bands.
            first = Math.max(lo - WINDOW_REACH, this.minChunk());
            last = Math.min(hi + WINDOW_REACH, this.maxChunk());
        }
        Band scene = this.scene(ring, first, last).band();
        for (int c = first; c <= last; c++) {
            Band crop = scene.crop(CHUNK * c, CHUNK);
            crop.grooveNodes().clear();
            crop.faceNodes().clear();
            this.cache.put(this.bandKey(ring, c), crop);
            if (c == chunk) {
                band = crop;
            }
        }
        return Objects.requireNonNull(band, "The scene holds no band of the chunk");
    }

    /** Copies the grooves and surface paths of a chunk band into the band or the patches of the surface. */
    private void copy(SegmentSurface surface, int r, Band chunk) {
        Band band = surface.band(r);
        for (BandKind kind : List.of(BandKind.GROOVE, BandKind.SURFACE)) {
            for (long pos : chunk.blocksOf(kind)) {
                if (band.get(pos) != BandKind.BOX) {
                    band.set(pos, kind);
                    continue;
                }
                int along = BandPos.axis(pos);
                for (ShaftMaze.Box box : surface.boxes()) {
                    int cross = this.patchCross(r, BandPos.ring(pos), box);
                    int offset = along - box.center();
                    if (cross != NO_CROSS && Math.abs(offset) <= Ring.PATCH_EDGE) {
                        boolean shaft = box.patching() == ShaftMaze.Patching.SHAFT;
                        surface.patch(box.center()).set(shaft ? offset : cross, shaft ? cross : offset, kind);
                    }
                }
            }
        }
    }

}
