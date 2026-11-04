package us.drullk.aerolite.world.shaft.pattern.tiles;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.pattern.BandPattern;
import us.drullk.aerolite.world.shaft.pattern.PatternCache;
import us.drullk.aerolite.world.shaft.pattern.PatternRegion;
import us.drullk.aerolite.world.shaft.pattern.RingFaces;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;
import us.drullk.aerolite.world.shaft.surface.path.BandPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The tiles pattern. Axis grooves run along the pillar. Ring grooves run around it. The grooves cut the outer layer into tiles.
 *
 * <p>The rules follow {@code prototypes/tiles/README.md}. A rule on the face columns 0 to 12 applies to each face between two corners.
 * A face of another width keeps the same period from its first column. An axis groove never sits on a corner.
 * <ul>
 * <li>Small: axis grooves where the face column is 2 modulo 4, ring grooves every 4 rows. Large: axis grooves where the face column
 * is 2 modulo 8, ring grooves every 8 rows. A tile column is a run of ring positions between two axis grooves.
 * The tile column that holds a corner is the corner strip.</li>
 * <li>Stagger X, small only: even courses have axis grooves where the face column is 3 modulo 4, odd courses where it is 1 modulo 4.</li>
 * <li>Stagger Y: the tile columns alternate between phase 0 and phase 1 around the ring. Phase 1 has its ring grooves half a period earlier.
 * The pillar random picks the tile columns of phase 0.</li>
 * <li>Encased: no ring groove crosses the corner strip.</li>
 * <li>Nodes sit on the odd rows: on the even face columns, or on the odd face columns with stagger X.</li>
 * </ul>
 * A tile belongs to the chunk that holds its center row. The last tiles of a chunk have their center row in the last period of the chunk.
 * A cut path that turns inside a small tile leaves an orphan wall block. The owner allows it. A large tile keeps the orphan rule.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class TilesPattern extends BandPattern {

    private static final int SMALL_PERIOD = 4;
    private static final int LARGE_PERIOD = 8;
    private static final int GROOVE_RESIDUE = 2;
    private static final int X_EVEN_RESIDUE = 3;
    private static final int X_ODD_RESIDUE = 1;

    private final TilesSettings.Size size;
    private final TilesSettings.Stagger stagger;
    private final boolean encased;
    private final int flip;
    private final RingLayout[] rings;

    /**
     * The tile columns of one ring.
     *
     * @param axis   the axis groove marks of stagger none and Y, or of the even courses of stagger X
     * @param odd    the axis groove marks of the odd courses of stagger X, else the same as {@code axis}
     * @param runs   the tile columns of {@code axis}
     * @param oddRuns the tile columns of {@code odd}
     * @param runOf  the tile column of each ring position of {@code axis}, -1 on an axis groove
     * @param rows   the ring groove row of each tile column modulo the period, -1 for an encased corner strip
     */
    private record RingLayout(boolean[] axis, boolean[] odd, List<IntList> runs, List<IntList> oddRuns, int[] runOf, int[] rows) {
    }

    /** A pattern with a cache of its own. */
    public TilesPattern(PatternContext context, TilesSettings settings) {
        this(context, settings, new PatternCache());
    }

    public TilesPattern(PatternContext context, TilesSettings settings, PatternCache cache) {
        this(context, settings.size(), settings.stagger(), settings.encased(), settings.paths(), cache);
    }

    protected TilesPattern(PatternContext context, TilesSettings.Size size, TilesSettings.Stagger stagger, boolean encased, SurfacePathSettings paths,
                           PatternCache cache) {
        super(context, paths, cache);
        this.size = size;
        this.stagger = size == TilesSettings.Size.LARGE && stagger == TilesSettings.Stagger.X ? TilesSettings.Stagger.NONE : stagger;
        this.encased = encased && this.stagger != TilesSettings.Stagger.X;
        this.flip = this.pillarBit(SALT_FLIP) ? 1 : 0;
        this.rings = new RingLayout[context.ring().rings()];
        for (int r = 0; r < this.rings.length; r++) {
            this.rings[r] = this.ringLayout(this.faces(r));
        }
    }

    public TilesSettings.Size size() {
        return this.size;
    }

    public TilesSettings.Stagger stagger() {
        return this.stagger;
    }

    public boolean encased() {
        return this.encased;
    }

    @Override
    protected Object options() {
        return List.of(TilesPattern.class, this.size, this.stagger, this.encased, this.paths());
    }

    /** The rows of one tile period. */
    public int period() {
        return this.size == TilesSettings.Size.SMALL ? SMALL_PERIOD : LARGE_PERIOD;
    }

    private RingLayout ringLayout(RingFaces faces) {
        boolean[] axis;
        boolean[] odd;
        if (this.stagger == TilesSettings.Stagger.X) {
            axis = faces.innerMarks(SMALL_PERIOD, X_EVEN_RESIDUE);
            odd = faces.innerMarks(SMALL_PERIOD, X_ODD_RESIDUE);
        } else {
            axis = faces.innerMarks(this.period(), GROOVE_RESIDUE);
            odd = axis;
        }
        List<IntList> runs = RingFaces.runs(axis);
        int[] runOf = new int[faces.length()];
        Arrays.fill(runOf, -1);
        int[] rows = new int[runs.size()];
        for (int i = 0; i < runs.size(); i++) {
            boolean strip = false;
            for (int index : runs.get(i)) {
                runOf[index] = i;
                strip |= faces.column(index) == 0;
            }
            int t = this.period();
            if (this.encased && strip) {
                rows[i] = -1;
            } else if (this.stagger == TilesSettings.Stagger.Y && (i + this.flip) % 2 == 1) {
                rows[i] = t / 2 - 1;
            } else {
                rows[i] = t - 1;
            }
        }
        return new RingLayout(axis, odd, runs, RingFaces.runs(odd), runOf, rows);
    }

    /** The course of stagger X that holds the row. */
    protected static int course(int along) {
        return Math.floorDiv(along, SMALL_PERIOD);
    }

    /** True when the ring position at the row is a groove of the layout, before the tails. */
    protected boolean isGroove(int ring, int index, int along) {
        RingLayout layout = this.rings[ring];
        if (this.stagger == TilesSettings.Stagger.X) {
            if (Math.floorMod(along, SMALL_PERIOD) == SMALL_PERIOD - 1) {
                return true;
            }
            return (Math.floorMod(course(along), 2) == 0 ? layout.axis() : layout.odd())[index];
        }
        if (layout.axis()[index]) {
            return true;
        }
        int row = layout.rows()[layout.runOf()[index]];
        return row >= 0 && Math.floorMod(along, this.period()) == row;
    }

    /** True when the ring position holds nodes on the odd rows. */
    private boolean nodeColumn(int ring, int index) {
        int parity = this.stagger == TilesSettings.Stagger.X ? 1 : 0;
        return this.faces(ring).column(index) % 2 == parity;
    }

    @Override
    protected void layout(Band band, int ring) {
        LongSet tails = this.tails(band, ring);
        for (int along = band.axis0(); along < band.axisEnd(); along++) {
            for (int index = 0; index < band.ringLength(); index++) {
                long pos = band.pos(index, along);
                boolean groove = this.isGroove(ring, index, along) && !tails.contains(pos);
                if (groove) {
                    band.set(pos, BandKind.GROOVE);
                }
                if ((along & 1) != 0 && this.nodeColumn(ring, index)) {
                    (groove ? band.grooveNodes() : band.faceNodes()).add(pos);
                }
            }
        }
    }

    /** The tail of the stagger X tile of course {@code k} around the center ring position, or {@link BandPos#NONE} without tails. */
    protected long tailOf(int ring, int k, int center) {
        return BandPos.NONE;
    }

    /** The tile columns of course {@code k} of stagger X. */
    protected List<IntList> xRuns(int ring, int k) {
        RingLayout layout = this.rings[ring];
        return Math.floorMod(k, 2) == 0 ? layout.runs() : layout.oddRuns();
    }

    /** The tiles of stagger X of the courses {@code [fromCourse, toCourse]}. The center row of a tile is one row below its top. */
    protected List<PatternRegion> xTiles(int ring, int fromCourse, int toCourse) {
        List<PatternRegion> out = new ArrayList<>();
        for (int k = fromCourse; k <= toCourse; k++) {
            for (IntList run : this.xRuns(ring, k)) {
                LongSet blocks = new LongOpenHashSet();
                for (int index : run) {
                    for (int along = SMALL_PERIOD * k; along < SMALL_PERIOD * k + SMALL_PERIOD - 1; along++) {
                        blocks.add(this.pos(ring, index, along));
                    }
                }
                int center = run.getInt(run.size() / 2);
                out.add(new PatternRegion(blocks, this.tailOf(ring, k, center), this.isLast(SMALL_PERIOD * k + 1)));
            }
        }
        return out;
    }

    @Override
    protected List<PatternRegion> regions(int ring, int chunk) {
        int t = this.period();
        int lo = CHUNK * chunk;
        int hi = lo + CHUNK;
        List<PatternRegion> out = new ArrayList<>();
        if (this.stagger == TilesSettings.Stagger.X) {
            return this.xTiles(ring, course(lo), course(hi - 1));
        }
        RingLayout layout = this.rings[ring];
        for (int top = lo - t; top < hi + t; top++) {
            for (int i = 0; i < layout.runs().size(); i++) {
                int row = layout.rows()[i];
                int height = row < 0 ? t : t - 1;
                int first = row < 0 ? 0 : row + 1;
                if (Math.floorMod(top, t) != Math.floorMod(first, t)) {
                    continue;
                }
                int center = top + (height - 1) / 2;
                if (center < lo || center >= hi) {
                    continue;
                }
                IntList run = layout.runs().get(i);
                boolean strip = row < 0;
                if (strip && this.cuts()) {
                    // No groove crosses an encased corner strip, so a cut path there has no end.
                    continue;
                }
                LongSet blocks = new LongOpenHashSet();
                for (int index : run) {
                    for (int along = top; along < top + height; along++) {
                        blocks.add(this.pos(ring, index, along));
                    }
                }
                out.add(new PatternRegion(blocks, this.isLast(center)));
            }
        }
        return out;
    }

    /** True when a tile with this center row belongs to the last tiles of its chunk. */
    private boolean isLast(int center) {
        return Math.floorMod(center, CHUNK) >= CHUNK - this.period();
    }

    @Override
    protected boolean allowOrphans() {
        return this.size == TilesSettings.Size.SMALL;
    }

}
