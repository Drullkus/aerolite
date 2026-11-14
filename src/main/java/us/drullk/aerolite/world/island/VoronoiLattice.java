package us.drullk.aerolite.world.island;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A jittered lattice of sites and the cells of the power rule around them.
 * One site sits in each lattice box. A box is {@code cellSize} wide along X and Z and {@code cellHeight} tall along Y.
 * The row 0 of the lattice starts at {@code layerMinY}. The rows above and below shape the cells of the row 0.
 *
 * <p>The power rule works on scaled positions: the block position with its Y times the Y scale.
 * {@link PowerRule} states the power rule value, the walls, the gauge, the wall distance and the cell radius.
 * A block takes the cell with the least power rule value. Each wall is flat, and each cell is convex.
 * A cell of the row 0 takes its cell weight from the function of the caller.
 * The lattice clamps that cell weight to the weight bounds.
 * Every other cell has the cell weight 0.5.
 * A lost cell does not hold its own site. It has {@code h <= 0} toward one other cell.
 *
 * <p>The cell weight 0.5 for every cell and the Y scale 1 give the plain Voronoi cells of 0.5.0.
 * Each value then equals the value of 0.5.0.
 *
 * <p>The search reach and the wall reach. All distances are in scaled space. {@code a = 0.5 + jitter / 2}.
 * {@code ex = ez = cellSize} and {@code ey = cellHeight * yScale} are the scaled box sizes.
 * {@code s = max(weightMax, 0.5) - min(weightMin, 0.5)} is the weight spread of the lattice.
 * <ol>
 * <li>A block lies in its box. The site of that box is less than {@code a * e} away on each axis.
 * So the nearest site is at most {@code D = a * sqrt(ex^2 + ey^2 + ez^2)} away.</li>
 * <li>The cell i of a block and the nearest site m have {@code |P - Si|^2 - wi * S^2 <= |P - Sm|^2 - wm * S^2}.
 * So {@code |P - Si|^2 <= D^2 + s * S^2 = R^2}. Each block is at most {@code R} from the site of its cell.</li>
 * <li>Along one axis, the site of the box at offset {@code n} is at least {@code max(0, |n| - a) * e} from the block.
 * The search takes each offset with {@code sum of (max(0, |n| - a) * e)^2 <= R^2}.</li>
 * <li>A point of a wall belongs to both cells. By step 2, both sites of a wall lie within {@code R} of it, so within {@code 2 * R} of each other.</li>
 * <li>Along one axis, two sites at the box offset {@code n} are at least {@code max(0, |n| - jitter) * e} apart.
 * The walls of a cell come from each offset with {@code sum of (max(0, |n| - jitter) * e)^2 <= 4 * R^2}.</li>
 * <li>A cell i is lost when a cell j has {@code |Si - Sj|^2 <= (wj - wi) * S^2 <= s * S^2 <= 4 * R^2}. So the wall offsets hold j too.</li>
 * </ol>
 * Take a cell that is not lost. Inside it, a plane that is no wall never decides a value.
 * So the gauge, the wall distance and the cell radius over the wall offsets are exact.
 *
 * <p>The tie rule of the owner lookup applies to equal values of {@code |P - Si|^2 - (wi - 0.5) * S^2}.
 * Of those cells, the cell with the least {@code ix} wins. Then the least {@code iy} wins, then the least {@code iz}.
 * The result does not depend on the cache state or on the thread.
 *
 * <p>The kept walls. A cell entry keeps only the candidate planes that can bound the cell.
 * Clipping a box with every candidate gives the cell polyhedron, a little too large at most.
 * A candidate stays when a vertex lies within {@link #WALL_MARGIN} blocks of its plane, or beyond it.
 * Take a cell that is not lost, so every {@code h > 0}. Its gauge {@code G} is the largest {@code x / h} over the walls.
 * {@code G} is positively homogeneous about the site, and {@code G = 1} on the cell border.
 * A dropped plane j lies at least the margin {@code m} beyond the cell, so {@code x / h <= 1 - m * |N| / h} on the cell.
 * By homogeneity, {@code x / h <= (1 - m * |N| / h) * G} at every point, inside the cell or outside it.
 * That gap is many orders above the rounding, so a dropped plane never gives the largest value.
 * The cell holds the ball of the cell radius around its site. So a dropped plane is at least {@code m} farther than the nearest plane.
 * For a point of the cell the same ball argument holds for the wall distance.
 * For a point outside the cell, the wall distance is the signed distance to the nearest kept wall.
 * The lost test and the cell radius run over every candidate.
 * A clip below the clip tolerance can leave fewer than 4 faces. Then the cell entry keeps every candidate.
 *
 * <p>The size guards. Above {@link #SEARCH_GUARD} search offsets, the owner search keeps no offset array and no cache.
 * Above {@link #WALL_GUARD} wall offsets, the wall candidates keep no offset array.
 * A guarded lattice then tests each box of the reach cube in the loop. It visits the same offsets in the same sequence.
 * So a guarded lattice gives the same values, bit for bit.
 * A cell entry with more than {@link #KEPT_GUARD} kept walls stays out of the cache.
 *
 * <p>The lattice keeps bounded caches of sites, cell weights, search sets and kept walls by lattice box.
 * Each entry is a pure function of its key, so threads can share the caches.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class VoronoiLattice {

    /** The slots of the cache of sites and cell weights. */
    static final int SITE_CACHE_SIZE = 4096;
    /** The slots of the cache of search sets, one per box that holds a block. */
    static final int SEARCH_CACHE_SIZE = 256;
    /** The slots of the cache of kept walls, one per cell. */
    static final int WALL_CACHE_SIZE = 256;
    /** The largest search offset count with an offset array and a search cache. */
    static final int SEARCH_GUARD = 512;
    /** The largest wall offset count with an offset array. */
    static final int WALL_GUARD = 4096;
    /** The largest kept wall count of a cached cell entry. */
    static final int KEPT_GUARD = 256;
    /** The distance in blocks beyond the clipped polyhedron at which a candidate plane drops. */
    static final double WALL_MARGIN = 0.01;

    private static final int SITE_SALT = 0x5173;
    private static final double UNIT = 1.0 / (1 << 21);
    /** The relative slack of the offset bounds. An extra offset changes no value. */
    private static final double BOUND_SLACK = 1.0E-9;

    private final long seed;
    private final int cellSize;
    private final int cellHeight;
    private final int layerMinY;
    private final double jitter;
    private final double yScale;
    private final CellWeights weights;
    private final double weightMin;
    private final double weightMax;
    /** The largest distance from a block to the site of its cell, in scaled blocks. */
    private final double winnerReach;
    /** The box offsets of the owner search. */
    private final OffsetSet searchSet;
    /** The box offsets of the wall candidates. */
    private final OffsetSet wallSet;
    private final BoxCache<Site> sites = new BoxCache<>(SITE_CACHE_SIZE);
    private final BoxCache<Search> searches = new BoxCache<>(SEARCH_CACHE_SIZE);
    private final BoxCache<PowerRule.Walls> walls = new BoxCache<>(WALL_CACHE_SIZE);

    /** One cell: its lattice index and its site in blocks. */
    public record Cell(int ix, int iy, int iz, double siteX, double siteY, double siteZ) {
    }

    /** The gauge at one point and the cell radius. With equal cell weights and the Y scale 1, the cell radius is the half spacing of the cell. */
    public record Gauge(double value, double halfSpacing) {
    }

    /**
     * The values of one cell at one point. A lost cell has NaN values.
     *
     * @param wallDistance the distance from the point to the nearest wall, in blocks
     * @param cellRadius the distance from the site to the nearest wall, in blocks
     */
    public record CellValues(boolean lost, double gauge, double wallDistance, double cellRadius) {
    }

    /**
     * The cell weight of a cell of the row 0. The function is pure, and several threads call it at once.
     * The lattice clamps the result to its cell weight bounds. A result that is not a number counts as 0.5.
     */
    @FunctionalInterface
    public interface CellWeights {

        double weight(int ix, int iz);
    }

    /** A visitor of box offsets. It returns false to stop the visit. */
    @FunctionalInterface
    interface OffsetVisitor {

        boolean visit(int dx, int dy, int dz);
    }

    /** A lattice with the cell weight 0.5 for every cell and the Y scale 1: the plain Voronoi cells. */
    public VoronoiLattice(long seed, int cellSize, int cellHeight, int layerMinY, double jitter) {
        this(seed, cellSize, cellHeight, layerMinY, jitter, 1.0, (ix, iz) -> PowerRule.OTHER_WEIGHT, PowerRule.OTHER_WEIGHT, PowerRule.OTHER_WEIGHT);
    }

    /**
     * @param yScale the factor of Y in the power rule
     * @param weights the cell weight of each cell of the row 0. The lattice clamps it to the weight bounds.
     * @param weightMin the least cell weight that the function gives, at least 0
     * @param weightMax the largest cell weight that the function gives, at most 1
     */
    public VoronoiLattice(long seed, int cellSize, int cellHeight, int layerMinY, double jitter, double yScale, CellWeights weights, double weightMin, double weightMax) {
        this(seed, cellSize, cellHeight, layerMinY, jitter, yScale, weights, weightMin, weightMax, SEARCH_GUARD, WALL_GUARD);
    }

    /** A lattice with its own size guards. A guard of -1 forces the loop path. */
    VoronoiLattice(long seed, int cellSize, int cellHeight, int layerMinY, double jitter, double yScale, CellWeights weights, double weightMin, double weightMax,
                   int searchGuard, int wallGuard) {
        if (!(yScale > 0) || Double.isInfinite(yScale)) {
            throw new IllegalArgumentException("The Y scale " + yScale + " is not positive and finite");
        }
        if (!(0 <= weightMin && weightMin <= weightMax && weightMax <= 1)) {
            throw new IllegalArgumentException("The cell weight bounds " + weightMin + " and " + weightMax + " are not inside 0 to 1");
        }
        this.seed = seed;
        this.cellSize = cellSize;
        this.cellHeight = cellHeight;
        this.layerMinY = layerMinY;
        this.jitter = jitter;
        this.yScale = yScale;
        this.weights = weights;
        this.weightMin = weightMin;
        this.weightMax = weightMax;
        double spread = Math.max(weightMax, PowerRule.OTHER_WEIGHT) - Math.min(weightMin, PowerRule.OTHER_WEIGHT);
        double offset = 0.5 + jitter / 2;
        double[] extent = {cellSize, cellHeight * yScale, cellSize};
        double nearest = offset * offset * PowerRule.squared(extent[0], extent[1], extent[2]);
        this.winnerReach = Math.sqrt(nearest + spread * cellSize * cellSize);
        this.searchSet = new OffsetSet(extent, offset, this.winnerReach, false, searchGuard);
        this.wallSet = new OffsetSet(extent, jitter, 2 * this.winnerReach, true, wallGuard);
    }

    public long seed() {
        return this.seed;
    }

    public int cellSize() {
        return this.cellSize;
    }

    public int cellHeight() {
        return this.cellHeight;
    }

    public int layerMinY() {
        return this.layerMinY;
    }

    public double jitter() {
        return this.jitter;
    }

    public double yScale() {
        return this.yScale;
    }

    public double weightMin() {
        return this.weightMin;
    }

    public double weightMax() {
        return this.weightMax;
    }

    /** The largest distance from a block to the site of its cell, in scaled blocks. */
    public double winnerReach() {
        return this.winnerReach;
    }

    public int searchReachXZ() {
        return this.searchSet.reachXZ;
    }

    public int searchReachY() {
        return this.searchSet.reachY;
    }

    public int wallReachXZ() {
        return this.wallSet.reachXZ;
    }

    public int wallReachY() {
        return this.wallSet.reachY;
    }

    /** The count of box offsets of the owner search. */
    public int searchOffsetCount() {
        return this.searchSet.count(Integer.MAX_VALUE);
    }

    /** The count of box offsets of the wall candidates. */
    public int wallOffsetCount() {
        return this.wallSet.count(Integer.MAX_VALUE);
    }

    /** True when the owner search keeps its offset array and its cache. */
    boolean searchCached() {
        return this.searchSet.offsets != null;
    }

    /** True when the wall candidates keep their offset array. */
    boolean wallOffsetsKept() {
        return this.wallSet.offsets != null;
    }

    /** Visits the box offsets of the wall candidates in their sequence. */
    void visitWallOffsets(OffsetVisitor visitor) {
        this.wallSet.visit(visitor);
    }

    /** The cell of a lattice index. */
    public Cell cell(int ix, int iy, int iz) {
        Site site = this.site(ix, iy, iz);
        return new Cell(ix, iy, iz, site.x, site.y, site.z);
    }

    /** The cell weight of the cell: the clamped weight of the function in the row 0, else 0.5. */
    public double cellWeight(Cell cell) {
        return this.site(cell.ix(), cell.iy(), cell.iz()).weight;
    }

    /**
     * The cell with the least power rule value at the point. The tie rule of the class decides equal values.
     * With equal cell weights and the Y scale 1, it is the cell whose site is nearest.
     */
    public Cell nearest(double x, double y, double z) {
        int bx = Mth.floor(x / this.cellSize);
        int by = Mth.floor((y - this.layerMinY) / this.cellHeight);
        int bz = Mth.floor(z / this.cellSize);
        int[] offsets = this.searchSet.offsets;
        if (offsets != null) {
            Search search = this.searches.get(bx, by, bz, this::search);
            int owner = PowerRule.owner(x, y * this.yScale, z, search.sites, search.weights, search.weights.length, this.cellSize);
            return this.cell(bx + offsets[3 * owner], by + offsets[3 * owner + 1], bz + offsets[3 * owner + 2]);
        }
        // The loop path compares like PowerRule.owner, in the same offset sequence.
        double py = y * this.yScale;
        double area = (double) this.cellSize * this.cellSize;
        double[] best = {Double.POSITIVE_INFINITY};
        int[] found = new int[3];
        this.searchSet.visit((dx, dy, dz) -> {
            Site site = this.site(bx + dx, by + dy, bz + dz);
            double value = PowerRule.ownerKey(x, py, z, site.x, site.y * this.yScale, site.z, site.weight, area);
            if (value < best[0]) {
                best[0] = value;
                found[0] = dx;
                found[1] = dy;
                found[2] = dz;
            }
            return true;
        });
        return this.cell(bx + found[0], by + found[1], bz + found[2]);
    }

    /** The power rule value of the point for the cell, in scaled square blocks. */
    public double powerValue(Cell cell, double x, double y, double z) {
        Site site = this.site(cell.ix(), cell.iy(), cell.iz());
        return PowerRule.value(x, y * this.yScale, z, site.x, site.y * this.yScale, site.z, site.weight, this.cellSize);
    }

    /** The plane offset {@code h} of the wall of the cell toward the other cell, in scaled square blocks. */
    public double wallOffset(Cell cell, Cell other) {
        Site a = this.site(cell.ix(), cell.iy(), cell.iz());
        Site b = this.site(other.ix(), other.iy(), other.iz());
        return PowerRule.h(b.x - a.x, (b.y - a.y) * this.yScale, b.z - a.z, a.weight, b.weight, this.cellSize);
    }

    /** True when the cell does not hold its own site. A lost cell holds no island. */
    public boolean lost(Cell cell) {
        return this.walls(cell).lost();
    }

    /** The distance from the site to the nearest wall, in blocks. It is NaN for a lost cell. */
    public double cellRadius(Cell cell) {
        return this.walls(cell).cellRadius();
    }

    /** The gauge of the cell at the point and the cell radius. A lost cell gives NaN for both. */
    public Gauge evaluate(Cell cell, double x, double y, double z) {
        PowerRule.Walls walls = this.walls(cell);
        return new Gauge(walls.gauge(x, y * this.yScale, z), walls.cellRadius());
    }

    /** The gauge, the wall distance and the cell radius of the cell at the point. */
    public CellValues values(Cell cell, double x, double y, double z) {
        PowerRule.Walls walls = this.walls(cell);
        double py = y * this.yScale;
        return new CellValues(walls.lost(), walls.gauge(x, py, z), walls.wallDistance(x, py, z), walls.cellRadius());
    }

    /** A seeded value of the cell in [0, 1). Each salt gives an independent value. */
    public double value(Cell cell, int salt) {
        return unit(this.hash(cell.ix(), cell.iy(), cell.iz(), salt));
    }

    /** The value of {@link #value} for the lattice index of a lattice with the seed. */
    public static double value(long seed, int ix, int iy, int iz, int salt) {
        return unit(hash(seed, ix, iy, iz, salt));
    }

    /** The kept walls of the cell. The wall {@code i} faces the cell at {@link #wallNeighbor}. */
    PowerRule.Walls walls(Cell cell) {
        PowerRule.Walls found = this.walls.find(cell.ix(), cell.iy(), cell.iz());
        if (found != null) {
            return found;
        }
        PowerRule.Walls built = this.wallsOf(cell.ix(), cell.iy(), cell.iz());
        if (built.count() <= KEPT_GUARD) {
            this.walls.put(cell.ix(), cell.iy(), cell.iz(), built);
        }
        return built;
    }

    /** The cell across the kept wall {@code i} of the cell. */
    Cell wallNeighbor(Cell cell, int i) {
        PowerRule.Walls kept = this.walls(cell);
        return this.cell(cell.ix() + kept.offset(i, 0), cell.iy() + kept.offset(i, 1), cell.iz() + kept.offset(i, 2));
    }

    private Search search(int bx, int by, int bz) {
        int[] offsets = this.searchSet.offsets;
        int count = offsets.length / 3;
        double[] scaled = new double[3 * count];
        double[] cellWeights = new double[count];
        for (int i = 0; i < count; i++) {
            Site site = this.site(bx + offsets[3 * i], by + offsets[3 * i + 1], bz + offsets[3 * i + 2]);
            scaled[3 * i] = site.x;
            scaled[3 * i + 1] = site.y * this.yScale;
            scaled[3 * i + 2] = site.z;
            cellWeights[i] = site.weight;
        }
        return new Search(scaled, cellWeights);
    }

    /**
     * The kept walls of a cell. The 26 boxes around the cell clip the polyhedron first, so the later clips cut little.
     * The first visit runs the lost test and the cell radius over every candidate and clips the polyhedron.
     * The second visit keeps each candidate within the margin of a vertex of the polyhedron.
     */
    private PowerRule.Walls wallsOf(int ix, int iy, int iz) {
        Site own = this.site(ix, iy, iz);
        WallPass pass = new WallPass(own, this.yScale, this.cellSize, this.winnerReach);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        pass.near(this.site(ix + dx, iy + dy, iz + dz));
                    }
                }
            }
        }
        this.wallSet.visit((dx, dy, dz) -> pass.first(this.site(ix + dx, iy + dy, iz + dz)));
        if (pass.lost) {
            return pass.lostWalls();
        }
        pass.keepAll = !pass.clipper.closed();
        this.wallSet.visit((dx, dy, dz) -> {
            pass.second(dx, dy, dz, this.site(ix + dx, iy + dy, iz + dz));
            return true;
        });
        return pass.build();
    }

    private Site site(int ix, int iy, int iz) {
        return this.sites.get(ix, iy, iz, this::computeSite);
    }

    private Site computeSite(int ix, int iy, int iz) {
        long hash = this.hash(ix, iy, iz, SITE_SALT);
        double x = (ix + 0.5 + this.jitter * (unit(hash) - 0.5)) * this.cellSize;
        double y = this.layerMinY + (iy + 0.5 + this.jitter * (unit(hash >>> 21) - 0.5)) * this.cellHeight;
        double z = (iz + 0.5 + this.jitter * (unit(hash >>> 42) - 0.5)) * this.cellSize;
        double weight = PowerRule.OTHER_WEIGHT;
        if (iy == 0) {
            double rolled = this.weights.weight(ix, iz);
            // The reach bounds hold 0.5, so this weight keeps the reach proof.
            weight = Double.isNaN(rolled) ? PowerRule.OTHER_WEIGHT : Mth.clamp(rolled, this.weightMin, this.weightMax);
        }
        return new Site(x, y, z, weight);
    }

    private long hash(int ix, int iy, int iz, int salt) {
        return hash(this.seed, ix, iy, iz, salt);
    }

    private static long hash(long seed, int ix, int iy, int iz, int salt) {
        long hash = RandomSupport.mixStafford13(seed ^ salt);
        hash = RandomSupport.mixStafford13(hash ^ ix);
        hash = RandomSupport.mixStafford13(hash ^ iy);
        return RandomSupport.mixStafford13(hash ^ iz);
    }

    private static double unit(long bits) {
        return (bits & 0x1FFFFF) * UNIT;
    }

    /** The site of one box in blocks and its cell weight. */
    private record Site(double x, double y, double z, double weight) {
    }

    /** The scaled sites and the cell weights of the search offsets around one box. */
    private record Search(double[] sites, double[] weights) {
    }

    /** The two visits of {@link #wallsOf}. Both compute each candidate plane with the same arithmetic as {@link PowerRule#walls}. */
    private static final class WallPass {

        private final double siteX;
        private final double siteY;
        private final double siteZ;
        private final double weight;
        private final double yScale;
        private final double cellSize;
        private final CellGeometry.Clipper clipper;
        private boolean lost;
        /** True when the clipped polyhedron has no volume, so no vertex can rule out a plane. */
        private boolean keepAll;
        private double radius = Double.POSITIVE_INFINITY;
        private double[] v = new double[48];
        private double[] h = new double[16];
        private double[] norm = new double[16];
        private int[] offsets = new int[48];
        private int count;

        private WallPass(Site own, double yScale, double cellSize, double winnerReach) {
            this.siteX = own.x;
            this.siteY = own.y * yScale;
            this.siteZ = own.z;
            this.weight = own.weight;
            this.yScale = yScale;
            this.cellSize = cellSize;
            this.clipper = new CellGeometry.Clipper(new Vec3(own.x, own.y, own.z), winnerReach, yScale);
        }

        /** Clips the polyhedron with the wall toward a near cell. Each such plane holds the cell, so any of them can clip. */
        private void near(Site other) {
            double vx = other.x - this.siteX;
            double vy = other.y * this.yScale - this.siteY;
            double vz = other.z - this.siteZ;
            double h = PowerRule.h(vx, vy, vz, this.weight, other.weight, this.cellSize);
            if (h > 0) {
                this.clipper.clipWall(vx, vy, vz, h, PowerRule.norm(vx, vy, vz, this.yScale), null);
            }
        }

        /** Runs the lost test, the cell radius and the clip for one candidate. It stops the visit at a lost cell. */
        private boolean first(Site other) {
            double vx = other.x - this.siteX;
            double vy = other.y * this.yScale - this.siteY;
            double vz = other.z - this.siteZ;
            double h = PowerRule.h(vx, vy, vz, this.weight, other.weight, this.cellSize);
            if (h <= 0) {
                this.lost = true;
                return false;
            }
            double norm = PowerRule.norm(vx, vy, vz, this.yScale);
            this.radius = Math.min(this.radius, PowerRule.wallRadius(vx, vy, vz, h, norm, this.weight, other.weight, this.yScale));
            this.clipper.clipWall(vx, vy, vz, h, norm, null);
            return true;
        }

        /** Keeps the candidate when a vertex of the polyhedron lies within the margin of its plane. */
        private void second(int dx, int dy, int dz, Site other) {
            double vx = other.x - this.siteX;
            double vy = other.y * this.yScale - this.siteY;
            double vz = other.z - this.siteZ;
            double h = PowerRule.h(vx, vy, vz, this.weight, other.weight, this.cellSize);
            double norm = PowerRule.norm(vx, vy, vz, this.yScale);
            // A plane beyond the ball of every vertex by the margin lies beyond each vertex by the margin.
            if (!this.keepAll && (h / norm - this.clipper.farthest() >= WALL_MARGIN || this.clipper.wallDistance(vx, vy, vz, h, norm) <= -WALL_MARGIN)) {
                return;
            }
            if (this.count == this.h.length) {
                this.v = Arrays.copyOf(this.v, 6 * this.count);
                this.h = Arrays.copyOf(this.h, 2 * this.count);
                this.norm = Arrays.copyOf(this.norm, 2 * this.count);
                this.offsets = Arrays.copyOf(this.offsets, 6 * this.count);
            }
            int at = this.count++;
            this.v[3 * at] = vx;
            this.v[3 * at + 1] = vy;
            this.v[3 * at + 2] = vz;
            this.h[at] = h;
            this.norm[at] = norm;
            this.offsets[3 * at] = dx;
            this.offsets[3 * at + 1] = dy;
            this.offsets[3 * at + 2] = dz;
        }

        private PowerRule.Walls lostWalls() {
            return new PowerRule.Walls(this.siteX, this.siteY, this.siteZ, new double[0], new double[0], new double[0], new int[0], 0, true, Double.NaN);
        }

        private PowerRule.Walls build() {
            return new PowerRule.Walls(this.siteX, this.siteY, this.siteZ, Arrays.copyOf(this.v, 3 * this.count), Arrays.copyOf(this.h, this.count),
                    Arrays.copyOf(this.norm, this.count), Arrays.copyOf(this.offsets, 3 * this.count), this.count, false, this.radius);
        }
    }

    /**
     * The box offsets whose site can lie within a distance, in lexicographic sequence.
     * Up to its guard count it keeps an offset array. Above it, a visit tests each box of the reach cube.
     */
    private static final class OffsetSet {

        private final double[] extent;
        private final double slack;
        private final double bound;
        private final boolean skipCenter;
        private final int reachXZ;
        private final int reachY;
        @Nullable
        private final int[] offsets;

        private OffsetSet(double[] extent, double slack, double distance, boolean skipCenter, int guard) {
            this.extent = extent;
            this.slack = slack;
            this.bound = distance * distance * (1 + BOUND_SLACK);
            this.skipCenter = skipCenter;
            this.reachXZ = reach(extent[0], slack, distance);
            this.reachY = reach(extent[1], slack, distance);
            int count = this.count(guard);
            if (count > guard) {
                this.offsets = null;
            } else {
                int[] out = new int[3 * count];
                int[] at = {0};
                this.loop((dx, dy, dz) -> {
                    out[at[0]++] = dx;
                    out[at[0]++] = dy;
                    out[at[0]++] = dz;
                    return true;
                });
                this.offsets = out;
            }
        }

        /** The largest box offset along one axis whose site can lie within the distance. */
        private static int reach(double extent, double slack, double distance) {
            return Mth.floor(slack + distance / extent * (1 + BOUND_SLACK));
        }

        /** The offset count, or the first count above the limit. */
        private int count(int limit) {
            if (this.offsets != null) {
                return this.offsets.length / 3;
            }
            int[] count = {0};
            this.loop((dx, dy, dz) -> ++count[0] <= limit);
            return count[0];
        }

        private void visit(OffsetVisitor visitor) {
            int[] offsets = this.offsets;
            if (offsets == null) {
                this.loop(visitor);
                return;
            }
            for (int i = 0; i < offsets.length; i += 3) {
                if (!visitor.visit(offsets[i], offsets[i + 1], offsets[i + 2])) {
                    return;
                }
            }
        }

        /** Visits each box of the reach cube within the bound. */
        private void loop(OffsetVisitor visitor) {
            for (int dx = -this.reachXZ; dx <= this.reachXZ; dx++) {
                double gapX = gap(dx, this.slack) * this.extent[0];
                for (int dy = -this.reachY; dy <= this.reachY; dy++) {
                    double gapY = gap(dy, this.slack) * this.extent[1];
                    for (int dz = -this.reachXZ; dz <= this.reachXZ; dz++) {
                        if (this.skipCenter && dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        double gapZ = gap(dz, this.slack) * this.extent[2];
                        if (PowerRule.squared(gapX, gapY, gapZ) <= this.bound && !visitor.visit(dx, dy, dz)) {
                            return;
                        }
                    }
                }
            }
        }

        private static double gap(int offset, double slack) {
            return Math.max(0, Math.abs(offset) - slack);
        }
    }

    /** A pure function of a lattice index. */
    @FunctionalInterface
    private interface BoxFunction<T> {

        T apply(int ix, int iy, int iz);
    }

    /**
     * A bounded cache by lattice index with one entry per slot. A new entry replaces the entry of its slot.
     * Two threads can compute one entry at once. Both get equal values.
     */
    private static final class BoxCache<T> {

        private final AtomicReferenceArray<Slot<T>> slots;
        private final int mask;

        private BoxCache(int size) {
            this.slots = new AtomicReferenceArray<>(size);
            this.mask = size - 1;
        }

        private T get(int ix, int iy, int iz, BoxFunction<T> function) {
            T found = this.find(ix, iy, iz);
            if (found != null) {
                return found;
            }
            T value = function.apply(ix, iy, iz);
            this.put(ix, iy, iz, value);
            return value;
        }

        @Nullable
        private T find(int ix, int iy, int iz) {
            Slot<T> slot = this.slots.get(slot(ix, iy, iz) & this.mask);
            return slot != null && slot.ix() == ix && slot.iy() == iy && slot.iz() == iz ? slot.value() : null;
        }

        private void put(int ix, int iy, int iz, T value) {
            this.slots.set(slot(ix, iy, iz) & this.mask, new Slot<>(ix, iy, iz, value));
        }

        private static int slot(int ix, int iy, int iz) {
            int hash = ix * 0x9E3779B1 ^ iy * 0x85EBCA77 ^ iz * 0xC2B2AE3D;
            return hash ^ hash >>> 15;
        }
    }

    private record Slot<T>(int ix, int iy, int iz, T value) {
    }

}
