package us.drullk.aerolite.world.island;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;

/**
 * A jittered lattice of sites and the 3D Voronoi cells around them.
 * One site sits in each lattice box. A box is {@code cellSize} wide along X and Z and {@code cellHeight} tall along Y.
 * The row 0 of the lattice starts at {@code layerMinY}. The rows above and below shape the cells of the row 0.
 * The gauge of a cell is 0 at its site and 1 on its wall. Its level sets are copies of the cell, scaled about the site.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public final class VoronoiLattice {

    private static final int SITE_SALT = 0x5173;
    private static final double UNIT = 1.0 / (1 << 21);

    private final long seed;
    private final int cellSize;
    private final int cellHeight;
    private final int layerMinY;
    private final double jitter;
    /** The reach of the nearest site search along X and Z, in lattice steps. */
    private final int searchReachXZ;
    /** The reach of the nearest site search along Y, in lattice steps. */
    private final int searchReachY;
    /** The reach of the wall search along X and Z, in lattice steps. */
    private final int wallReachXZ;
    /** The reach of the wall search along Y, in lattice steps. */
    private final int wallReachY;

    /** One cell: its lattice index and its site. */
    public record Cell(int ix, int iy, int iz, double siteX, double siteY, double siteZ) {
    }

    /** The gauge at one point and the half spacing of the cell. The half spacing is the shortest site to wall distance along a site pair. */
    public record Gauge(double value, double halfSpacing) {
    }

    public VoronoiLattice(long seed, int cellSize, int cellHeight, int layerMinY, double jitter) {
        this.seed = seed;
        this.cellSize = cellSize;
        this.cellHeight = cellHeight;
        this.layerMinY = layerMinY;
        this.jitter = jitter;
        // A point is at most this far from its own site, so its nearest site is nearer.
        double offset = 0.5 + jitter / 2;
        double farthest = Math.sqrt(2 * (offset * cellSize) * (offset * cellSize) + (offset * cellHeight) * (offset * cellHeight));
        this.searchReachXZ = searchReach(farthest, cellSize, offset);
        this.searchReachY = searchReach(farthest, cellHeight, offset);
        // Every point of a cell is within that distance of its site, so a wall site is within twice that distance.
        this.wallReachXZ = searchReach(2 * farthest, cellSize, jitter);
        this.wallReachY = searchReach(2 * farthest, cellHeight, jitter);
    }

    /** The largest lattice offset whose site can lie within the distance. A site at the offset is at least {@code (offset - slack) * size} away. */
    private static int searchReach(double distance, int size, double slack) {
        return Math.max(1, Mth.ceil(distance / size + slack) - 1);
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

    public int searchReachXZ() {
        return this.searchReachXZ;
    }

    public int searchReachY() {
        return this.searchReachY;
    }

    public int wallReachXZ() {
        return this.wallReachXZ;
    }

    public int wallReachY() {
        return this.wallReachY;
    }

    /** The cell of a lattice index. */
    public Cell cell(int ix, int iy, int iz) {
        double[] site = new double[3];
        this.site(ix, iy, iz, site);
        return new Cell(ix, iy, iz, site[0], site[1], site[2]);
    }

    /** The cell whose site is nearest to the point. The search covers the lattice block of the search reach around the point. */
    public Cell nearest(double x, double y, double z) {
        int bx = Mth.floor(x / this.cellSize);
        int by = Mth.floor((y - this.layerMinY) / this.cellHeight);
        int bz = Mth.floor(z / this.cellSize);
        double[] site = new double[3];
        double best = Double.POSITIVE_INFINITY;
        int nx = bx;
        int ny = by;
        int nz = bz;
        for (int dx = -this.searchReachXZ; dx <= this.searchReachXZ; dx++) {
            for (int dy = -this.searchReachY; dy <= this.searchReachY; dy++) {
                for (int dz = -this.searchReachXZ; dz <= this.searchReachXZ; dz++) {
                    this.site(bx + dx, by + dy, bz + dz, site);
                    double distance = squared(site[0] - x, site[1] - y, site[2] - z);
                    if (distance < best) {
                        best = distance;
                        nx = bx + dx;
                        ny = by + dy;
                        nz = bz + dz;
                    }
                }
            }
        }
        return this.cell(nx, ny, nz);
    }

    /**
     * The gauge of the cell at the point. It is the largest plane coordinate over the bisector planes to the sites within the wall reach.
     * A plane that is not a wall never raises the largest value, so the gauge is exact.
     */
    public Gauge evaluate(Cell cell, double x, double y, double z) {
        double px = x - cell.siteX();
        double py = y - cell.siteY();
        double pz = z - cell.siteZ();
        double[] site = new double[3];
        double gauge = Double.NEGATIVE_INFINITY;
        double halfSpacing = Double.POSITIVE_INFINITY;
        for (int dx = -this.wallReachXZ; dx <= this.wallReachXZ; dx++) {
            for (int dy = -this.wallReachY; dy <= this.wallReachY; dy++) {
                for (int dz = -this.wallReachXZ; dz <= this.wallReachXZ; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    this.site(cell.ix() + dx, cell.iy() + dy, cell.iz() + dz, site);
                    double vx = site[0] - cell.siteX();
                    double vy = site[1] - cell.siteY();
                    double vz = site[2] - cell.siteZ();
                    double spacing = squared(vx, vy, vz);
                    gauge = Math.max(gauge, 2.0 * (px * vx + py * vy + pz * vz) / spacing);
                    halfSpacing = Math.min(halfSpacing, spacing);
                }
            }
        }
        return new Gauge(gauge, Math.sqrt(halfSpacing) / 2.0);
    }

    /** A seeded value of the cell in [0, 1). Each salt gives an independent value. */
    public double value(Cell cell, int salt) {
        return unit(this.hash(cell.ix(), cell.iy(), cell.iz(), salt));
    }

    private void site(int ix, int iy, int iz, double[] out) {
        long hash = this.hash(ix, iy, iz, SITE_SALT);
        out[0] = (ix + 0.5 + this.jitter * (unit(hash) - 0.5)) * this.cellSize;
        out[1] = this.layerMinY + (iy + 0.5 + this.jitter * (unit(hash >>> 21) - 0.5)) * this.cellHeight;
        out[2] = (iz + 0.5 + this.jitter * (unit(hash >>> 42) - 0.5)) * this.cellSize;
    }

    private long hash(int ix, int iy, int iz, int salt) {
        long hash = RandomSupport.mixStafford13(this.seed ^ salt);
        hash = RandomSupport.mixStafford13(hash ^ ix);
        hash = RandomSupport.mixStafford13(hash ^ iy);
        return RandomSupport.mixStafford13(hash ^ iz);
    }

    private static double unit(long bits) {
        return (bits & 0x1FFFFF) * UNIT;
    }

    private static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

}
