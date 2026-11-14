package us.drullk.aerolite.world.island;

import javax.annotation.Nullable;

/**
 * The pure math of the power rule over explicit sites. {@link VoronoiLattice} uses it.
 * The tables of the prototype test it.
 * Positions are scaled positions: the block position with its Y times the Y scale.
 * {@code S} is the cell size. The power rule value of a scaled position {@code P} for cell i is {@code |P - Si|^2 - wi * S^2}.
 * For two cells i and j, {@code V = Sj - Si} and {@code h = |V|^2 / 2 + (wi - wj) * S^2 / 2}.
 * The wall of cell i toward cell j is the plane {@code (P - Si) . V = h}. Its block space normal {@code N} is {@code V} with its Y times the Y scale.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
final class PowerRule {

    /** The cell weight of every cell outside the row 0. */
    static final double OTHER_WEIGHT = 0.5;

    private PowerRule() {
    }

    /** The power rule value of the scaled position for the site with the cell weight, in scaled square blocks. */
    static double value(double px, double py, double pz, double sx, double sy, double sz, double weight, double cellSize) {
        return squared(px - sx, py - sy, pz - sz) - weight * cellSize * cellSize;
    }

    /** The plane offset {@code h} of the wall of cell i toward cell j. */
    static double h(double vx, double vy, double vz, double weightI, double weightJ, double cellSize) {
        return squared(vx, vy, vz) * 0.5 + (weightI - weightJ) * cellSize * cellSize * 0.5;
    }

    /**
     * The index of the site with the least power rule value. On equal values the least index wins.
     * The comparison takes {@code |P - Si|^2 - (wi - 0.5) * S^2}. It ranks the cells like the power rule value. It is exact for the weight 0.5.
     *
     * @param sites the scaled sites, three values per site
     */
    static int owner(double px, double py, double pz, double[] sites, double[] weights, int count, double cellSize) {
        double area = cellSize * cellSize;
        int best = 0;
        double bestValue = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            double value = ownerKey(px, py, pz, sites[3 * i], sites[3 * i + 1], sites[3 * i + 2], weights[i], area);
            if (value < bestValue) {
                bestValue = value;
                best = i;
            }
        }
        return best;
    }

    /** The length of the block space normal {@code N} of the wall with the scaled vector {@code V}. */
    static double norm(double vx, double vy, double vz, double yScale) {
        return Math.sqrt(squared(vx, vy * yScale, vz));
    }

    /** The distance {@code h / |N|} from the site to one wall, in blocks. */
    static double wallRadius(double vx, double vy, double vz, double h, double norm, double weightI, double weightJ, double yScale) {
        // Equal weights and the Y scale 1 give the 0.5.0 half spacing bit for bit.
        return weightI == weightJ && yScale == 1 ? Math.sqrt(squared(vx, vy, vz)) / 2.0 : h / norm;
    }

    /** The comparison value of the owner lookup: {@code |P - Si|^2 - (wi - 0.5) * S^2}, with {@code area = S^2}. */
    static double ownerKey(double px, double py, double pz, double sx, double sy, double sz, double weight, double area) {
        return squared(sx - px, sy - py, sz - pz) - (weight - OTHER_WEIGHT) * area;
    }

    /** The island body: the fill minus the gauge, times the cell radius. */
    static double islandBody(double fill, double gauge, double cellRadius) {
        return (fill - gauge) * cellRadius;
    }

    /**
     * The walls of one cell toward explicit other sites.
     *
     * @param others the scaled sites of the other cells, three values per site
     */
    static Walls walls(double sx, double sy, double sz, double weight, double[] others, double[] otherWeights, int count, double cellSize, double yScale) {
        double[] v = new double[3 * count];
        double[] h = new double[count];
        double[] norm = new double[count];
        boolean lost = false;
        double radius = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            double vx = others[3 * i] - sx;
            double vy = others[3 * i + 1] - sy;
            double vz = others[3 * i + 2] - sz;
            v[3 * i] = vx;
            v[3 * i + 1] = vy;
            v[3 * i + 2] = vz;
            h[i] = h(vx, vy, vz, weight, otherWeights[i], cellSize);
            norm[i] = norm(vx, vy, vz, yScale);
            lost |= h[i] <= 0;
            radius = Math.min(radius, wallRadius(vx, vy, vz, h[i], norm[i], weight, otherWeights[i], yScale));
        }
        return new Walls(sx, sy, sz, v, h, norm, null, count, lost, lost ? Double.NaN : radius);
    }

    /**
     * The walls of one cell. For explicit sites it holds one candidate plane per other site.
     * For a lattice cell it holds the kept walls. A plane that is no wall never decides a value inside a cell that is not lost.
     * A lost cell has {@code h <= 0} toward one candidate. Its gauge, wall distance and cell radius are NaN.
     */
    static final class Walls {

        private final double siteX;
        private final double siteY;
        private final double siteZ;
        private final double[] v;
        private final double[] h;
        private final double[] norm;
        @Nullable
        private final int[] offsets;
        private final int count;
        private final boolean lost;
        private final double cellRadius;

        /**
         * @param offsets the box offset of the neighbor of each wall, three values per wall, or null for explicit sites
         */
        Walls(double siteX, double siteY, double siteZ, double[] v, double[] h, double[] norm, @Nullable int[] offsets, int count, boolean lost, double cellRadius) {
            this.siteX = siteX;
            this.siteY = siteY;
            this.siteZ = siteZ;
            this.v = v;
            this.h = h;
            this.norm = norm;
            this.offsets = offsets;
            this.count = count;
            this.lost = lost;
            this.cellRadius = cellRadius;
        }

        boolean lost() {
            return this.lost;
        }

        /** The smallest {@code h / |N|} over the walls, in blocks. */
        double cellRadius() {
            return this.cellRadius;
        }

        int count() {
            return this.count;
        }

        double vx(int wall) {
            return this.v[3 * wall];
        }

        double vy(int wall) {
            return this.v[3 * wall + 1];
        }

        double vz(int wall) {
            return this.v[3 * wall + 2];
        }

        double h(int wall) {
            return this.h[wall];
        }

        /** The box offset of the neighbor across the wall along the axis 0, 1 or 2. Explicit sites have no offsets. */
        int offset(int wall, int axis) {
            if (this.offsets == null) {
                throw new IllegalStateException("The walls of explicit sites have no box offsets");
            }
            return this.offsets[3 * wall + axis];
        }

        /** The length of the block space normal {@code N} of the wall. */
        double norm(int wall) {
            return this.norm[wall];
        }

        /** The largest {@code ((P - Si) . V) / h} over the walls at the scaled position. It is 0 at the site and 1 on the wall. */
        double gauge(double px, double py, double pz) {
            if (this.lost) {
                return Double.NaN;
            }
            double dx = px - this.siteX;
            double dy = py - this.siteY;
            double dz = pz - this.siteZ;
            double gauge = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < this.count; i++) {
                double along = dx * this.v[3 * i] + dy * this.v[3 * i + 1] + dz * this.v[3 * i + 2];
                gauge = Math.max(gauge, along / this.h[i]);
            }
            return gauge;
        }

        /** The smallest {@code (h - (P - Si) . V) / |N|} over the walls at the scaled position, in blocks. */
        double wallDistance(double px, double py, double pz) {
            if (this.lost) {
                return Double.NaN;
            }
            double dx = px - this.siteX;
            double dy = py - this.siteY;
            double dz = pz - this.siteZ;
            double distance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < this.count; i++) {
                double along = dx * this.v[3 * i] + dy * this.v[3 * i + 1] + dz * this.v[3 * i + 2];
                distance = Math.min(distance, (this.h[i] - along) / this.norm[i]);
            }
            return distance;
        }
    }

    static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

}
