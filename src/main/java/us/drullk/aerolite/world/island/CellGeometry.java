package us.drullk.aerolite.world.island;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The polyhedra of the cells of a {@link VoronoiLattice}, without distortion and without the fill fraction.
 * A cell starts as a box around its site that holds every point of the cell.
 * Each kept wall of the power rule then clips it, in block space. The rows above and below count too.
 * A lost cell has no polyhedron.
 * Each face carries the gridlines where the planes at every {@link #GRID_SPACING} blocks on X, Y and Z cut it.
 * Each face gives the distance from a point on it to its nearest edge.
 * The ray test and the chunk overlap work on the face planes and on the vertices.
 * Neighbor cells each hold a copy of an edge between them. {@link #distinctEdges} gives each such edge once, with its owner cells.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-12_19-19_6eca7213-840e-4a42-ba65-011a09997226.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class CellGeometry {

    /** The spacing of the gridlines, the chunk section size. */
    public static final int GRID_SPACING = 16;
    /** The distance in blocks below which a point counts as on a plane. */
    private static final double EPSILON = 1.0E-6;
    /** The rounding of an edge end point for the edge dedupe, in steps per block. */
    private static final double EDGE_KEY_SCALE = 1.0E4;

    /** One line segment in world coordinates. */
    public record Segment(Vec3 from, Vec3 to) {
    }

    /**
     * One face of a cell: a convex polygon on the plane {@code normal . p = offset}. The normal has unit length and points out of the cell.
     *
     * @param neighbor the cell across the face, or null for a face of the start box
     */
    public record Face(List<Vec3> vertices, Vec3 normal, double offset, @Nullable VoronoiLattice.Cell neighbor, List<Segment> gridlines) {

        /** The signed distance of the point to the face plane. It is negative inside the cell. */
        public double distance(Vec3 point) {
            return this.normal.dot(point) - this.offset;
        }

        /** The distance from a point on the face to the nearest edge of the face polygon. */
        public double edgeDistance(Vec3 point) {
            double nearest = Double.POSITIVE_INFINITY;
            int count = this.vertices.size();
            for (int i = 0; i < count; i++) {
                Vec3 a = this.vertices.get(i);
                Vec3 edge = this.vertices.get((i + 1) % count).subtract(a);
                Vec3 relative = point.subtract(a);
                double along = Mth.clamp(relative.dot(edge) / edge.lengthSqr(), 0, 1);
                nearest = Math.min(nearest, relative.distanceToSqr(edge.scale(along)));
            }
            return Math.sqrt(nearest);
        }
    }

    /** One distinct edge of a list of shapes, with each cell that holds it. */
    public record CellEdge(Segment segment, List<VoronoiLattice.Cell> owners) {
    }

    /** The polyhedron of one cell, its faces, its edges without duplicates and its vertices without duplicates. */
    public record Shape(VoronoiLattice.Cell cell, List<Face> faces, List<Segment> edges, List<Vec3> vertices) {

        public Vec3 site() {
            return new Vec3(this.cell.siteX(), this.cell.siteY(), this.cell.siteZ());
        }

        /**
         * The ray parameter where the ray enters the polyhedron, 0 for an origin inside it, or positive infinity for a miss.
         * The direction needs no unit length.
         */
        public double raycast(Vec3 origin, Vec3 direction) {
            double enter = 0;
            double exit = Double.POSITIVE_INFINITY;
            for (Face face : this.faces) {
                double along = face.normal().dot(direction);
                double distance = face.distance(origin);
                if (Math.abs(along) < 1.0E-12) {
                    if (distance > 0) {
                        return Double.POSITIVE_INFINITY;
                    }
                    continue;
                }
                double t = -distance / along;
                if (along < 0) {
                    enter = Math.max(enter, t);
                } else {
                    exit = Math.min(exit, t);
                }
                if (enter > exit) {
                    return Double.POSITIVE_INFINITY;
                }
            }
            return enter;
        }

        /** The packed positions of the chunks whose column overlaps the polyhedron by a positive area. */
        public LongList chunks() {
            List<double[]> hull = hull(this.vertices);
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (double[] point : hull) {
                minX = Math.min(minX, point[0]);
                maxX = Math.max(maxX, point[0]);
                minZ = Math.min(minZ, point[1]);
                maxZ = Math.max(maxZ, point[1]);
            }
            LongList chunks = new LongArrayList();
            for (int chunkX = Mth.floor(minX / 16); chunkX <= Mth.floor(maxX / 16); chunkX++) {
                for (int chunkZ = Mth.floor(minZ / 16); chunkZ <= Mth.floor(maxZ / 16); chunkZ++) {
                    if (overlapsSquare(hull, chunkX * 16, chunkZ * 16, 16)) {
                        chunks.add(ChunkPos.asLong(chunkX, chunkZ));
                    }
                }
            }
            return chunks;
        }
    }

    private CellGeometry() {
    }

    /** The first island function or {@code aerolite:voronoi_islands} function of the tree, depth first, or null. */
    @Nullable
    public static IslandCells findIslands(DensityFunction function) {
        IslandCells[] found = new IslandCells[1];
        function.mapAll(visited -> {
            if (found[0] == null && visited instanceof IslandCells islands) {
                found[0] = islands;
            }
            return visited;
        });
        return found[0];
    }

    /**
     * The polyhedron of the cell, or null for a lost cell. The kept walls of the cell clip it in their sequence.
     * A cell thinner than the clip tolerance has no polyhedron either.
     */
    @Nullable
    public static Shape shape(VoronoiLattice lattice, VoronoiLattice.Cell cell) {
        PowerRule.Walls walls = lattice.walls(cell);
        if (walls.lost()) {
            return null;
        }
        Clipper clipper = new Clipper(new Vec3(cell.siteX(), cell.siteY(), cell.siteZ()), lattice.winnerReach(), lattice.yScale());
        for (int i = 0; i < walls.count(); i++) {
            clipper.clipWall(walls.vx(i), walls.vy(i), walls.vz(i), walls.h(i), walls.norm(i), lattice.wallNeighbor(cell, i));
        }
        if (!clipper.closed()) {
            return null;
        }
        List<Face> faces = clipper.faces();
        List<Face> withGrid = new ArrayList<>(faces.size());
        for (Face face : faces) {
            withGrid.add(new Face(face.vertices(), face.normal(), face.offset(), face.neighbor(), gridlines(face.vertices(), face.normal())));
        }
        return new Shape(cell, List.copyOf(withGrid), edges(withGrid), vertices(withGrid));
    }

    /**
     * A polyhedron around a site that wall planes of the power rule clip one by one, in block space.
     * It starts as a box that holds every point of the cell.
     * Each point of the cell lies within the winner reach of its site in scaled space.
     */
    static final class Clipper {

        private final Vec3 site;
        private final double yScale;
        private List<Face> faces;
        /** The largest distance from the site to a vertex copy, in blocks. */
        private double farthest;

        Clipper(Vec3 site, double winnerReach, double yScale) {
            this.site = site;
            this.yScale = yScale;
            this.faces = box(site, winnerReach, winnerReach / yScale);
            this.farthest = this.measure();
        }

        /** Keeps the part on the side of the site of one wall. The wall has the scaled vector {@code V}, the offset {@code h} and the normal length {@code |N|}. */
        void clipWall(double vx, double vy, double vz, double h, double norm, @Nullable VoronoiLattice.Cell neighbor) {
            if (h / norm - this.farthest > EPSILON) {
                // The plane lies beyond the ball that holds every vertex, so it cuts nothing.
                return;
            }
            Vec3 normal = this.normal(vx, vy, vz, norm);
            double offset = this.offset(normal, h, norm);
            if (this.largestDistance(normal, offset) > EPSILON) {
                this.faces = clip(this.faces, normal, offset, neighbor);
                this.farthest = this.measure();
            }
        }

        /** The largest distance from the site to a vertex copy, in blocks. */
        double farthest() {
            return this.farthest;
        }

        private double measure() {
            double largest = 0;
            for (Face face : this.faces) {
                for (Vec3 vertex : face.vertices()) {
                    largest = Math.max(largest, vertex.distanceToSqr(this.site));
                }
            }
            return Math.sqrt(largest);
        }

        /** The largest signed distance in blocks from a vertex of the polyhedron to the wall. It is negative when the wall lies beyond every vertex. */
        double wallDistance(double vx, double vy, double vz, double h, double norm) {
            Vec3 normal = this.normal(vx, vy, vz, norm);
            return this.largestDistance(normal, this.offset(normal, h, norm));
        }

        List<Face> faces() {
            return this.faces;
        }

        /** True while the polyhedron has at least 4 faces. A clip below the tolerance can remove every face. */
        boolean closed() {
            return this.faces.size() >= 4;
        }

        private Vec3 normal(double vx, double vy, double vz, double norm) {
            return new Vec3(vx / norm, vy * this.yScale / norm, vz / norm);
        }

        private double offset(Vec3 normal, double h, double norm) {
            return normal.dot(this.site) + h / norm;
        }

        /** The largest signed distance over every vertex copy of every face. It decides a cut like {@link #clip}. */
        private double largestDistance(Vec3 normal, double offset) {
            double largest = Double.NEGATIVE_INFINITY;
            for (Face face : this.faces) {
                for (Vec3 vertex : face.vertices()) {
                    largest = Math.max(largest, normal.dot(vertex) - offset);
                }
            }
            return largest;
        }
    }

    /** The nearest shape that the ray hits, or null. */
    @Nullable
    public static Shape nearestHit(List<Shape> shapes, Vec3 origin, Vec3 direction) {
        Shape nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Shape shape : shapes) {
            double t = shape.raycast(origin, direction);
            if (t < best) {
                best = t;
                nearest = shape;
            }
        }
        return nearest;
    }

    /** Each distinct edge of the shapes once. Its owners are the cells of the shapes that hold it, in shape sequence. */
    public static List<CellEdge> distinctEdges(List<Shape> shapes) {
        Set<List<Long>> points = new HashSet<>();
        Map<List<Long>, CellEdge> edges = new LinkedHashMap<>();
        for (Shape shape : shapes) {
            for (Segment edge : shape.edges()) {
                List<Long> key = edgeKey(snappedKey(points, edge.from()), snappedKey(points, edge.to()));
                edges.computeIfAbsent(key, absent -> new CellEdge(edge, new ArrayList<>())).owners().add(shape.cell());
            }
        }
        return edges.values().stream().map(edge -> new CellEdge(edge.segment(), List.copyOf(edge.owners()))).toList();
    }

    /** The six faces of the box around the site. The half size is {@code reach} on X and Z and {@code reachY} on Y. */
    private static List<Face> box(Vec3 site, double reach, double reachY) {
        double x0 = site.x - reach;
        double x1 = site.x + reach;
        double y0 = site.y - reachY;
        double y1 = site.y + reachY;
        double z0 = site.z - reach;
        double z1 = site.z + reach;
        List<Face> faces = new ArrayList<>(6);
        faces.add(boxFace(new Vec3(-1, 0, 0), -x0, new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0)));
        faces.add(boxFace(new Vec3(1, 0, 0), x1, new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1)));
        faces.add(boxFace(new Vec3(0, -1, 0), -y0, new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1)));
        faces.add(boxFace(new Vec3(0, 1, 0), y1, new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0)));
        faces.add(boxFace(new Vec3(0, 0, -1), -z0, new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0)));
        faces.add(boxFace(new Vec3(0, 0, 1), z1, new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)));
        return faces;
    }

    private static Face boxFace(Vec3 normal, double offset, Vec3... vertices) {
        return new Face(List.of(vertices), normal, offset, null, List.of());
    }

    /** Keeps the part of the polyhedron with {@code normal . p <= offset}. The cut adds one face on the plane. */
    private static List<Face> clip(List<Face> faces, Vec3 normal, double offset, VoronoiLattice.Cell neighbor) {
        List<Face> kept = new ArrayList<>(faces.size() + 1);
        List<Vec3> cut = new ArrayList<>();
        boolean changed = false;
        for (Face face : faces) {
            List<Vec3> polygon = face.vertices();
            List<Vec3> clipped = new ArrayList<>(polygon.size() + 1);
            int count = polygon.size();
            for (int i = 0; i < count; i++) {
                Vec3 a = polygon.get(i);
                Vec3 b = polygon.get((i + 1) % count);
                double da = normal.dot(a) - offset;
                double db = normal.dot(b) - offset;
                if (da <= EPSILON) {
                    clipped.add(a);
                    if (da >= -EPSILON) {
                        cut.add(a);
                    }
                } else {
                    changed = true;
                }
                if (da < -EPSILON && db > EPSILON || da > EPSILON && db < -EPSILON) {
                    Vec3 crossing = crossing(a, da, b, db);
                    clipped.add(crossing);
                    cut.add(crossing);
                }
            }
            List<Vec3> polygonKept = dedupe(clipped);
            if (polygonKept.size() >= 3 && area(polygonKept, face.normal()) > EPSILON) {
                kept.add(polygonKept.size() == polygon.size() && polygonKept.equals(polygon)
                        ? face
                        : new Face(polygonKept, face.normal(), face.offset(), face.neighbor(), List.of()));
            }
        }
        if (!changed) {
            // The plane touches the polyhedron at most, so it adds no face.
            return faces;
        }
        List<Vec3> cap = sortAround(distinct(cut), normal);
        if (cap.size() >= 3 && area(cap, normal) > EPSILON) {
            kept.add(new Face(cap, normal, offset, neighbor, List.of()));
        }
        return kept;
    }

    /** The point where the plane cuts the segment. The end points go in a fixed sequence, so both faces of an edge get the same point. */
    private static Vec3 crossing(Vec3 a, double da, Vec3 b, double db) {
        if (compare(a, b) > 0) {
            return crossing(b, db, a, da);
        }
        double t = da / (da - db);
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static int compare(Vec3 a, Vec3 b) {
        int x = Double.compare(a.x, b.x);
        if (x != 0) {
            return x;
        }
        int y = Double.compare(a.y, b.y);
        return y != 0 ? y : Double.compare(a.z, b.z);
    }

    /** The polygon without a vertex that repeats the vertex before it. */
    private static List<Vec3> dedupe(List<Vec3> polygon) {
        List<Vec3> result = new ArrayList<>(polygon.size());
        for (Vec3 point : polygon) {
            if (result.isEmpty() || result.getLast().distanceToSqr(point) > EPSILON * EPSILON) {
                result.add(point);
            }
        }
        while (result.size() > 1 && result.getFirst().distanceToSqr(result.getLast()) <= EPSILON * EPSILON) {
            result.removeLast();
        }
        return result;
    }

    private static List<Vec3> distinct(List<Vec3> points) {
        List<Vec3> result = new ArrayList<>(points.size());
        for (Vec3 point : points) {
            boolean seen = false;
            for (Vec3 other : result) {
                if (other.distanceToSqr(point) <= EPSILON * EPSILON) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                result.add(point);
            }
        }
        return result;
    }

    /** The points of a convex polygon in angle sequence around their mean, counterclockwise seen along the normal. */
    private static List<Vec3> sortAround(List<Vec3> points, Vec3 normal) {
        if (points.size() < 3) {
            return points;
        }
        Vec3 mean = Vec3.ZERO;
        for (Vec3 point : points) {
            mean = mean.add(point);
        }
        Vec3 center = mean.scale(1.0 / points.size());
        Vec3 helper = Math.abs(normal.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = normal.cross(helper).normalize();
        Vec3 v = normal.cross(u);
        List<Vec3> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(point -> {
            Vec3 relative = point.subtract(center);
            return Math.atan2(relative.dot(v), relative.dot(u));
        }));
        return sorted;
    }

    /** The area of a planar polygon. */
    private static double area(List<Vec3> polygon, Vec3 normal) {
        Vec3 sum = Vec3.ZERO;
        Vec3 first = polygon.getFirst();
        for (int i = 1; i + 1 < polygon.size(); i++) {
            sum = sum.add(polygon.get(i).subtract(first).cross(polygon.get(i + 1).subtract(first)));
        }
        return Math.abs(sum.dot(normal)) / 2;
    }

    /** The edges of the faces. An edge that two faces share appears once. */
    private static List<Segment> edges(List<Face> faces) {
        Set<List<Long>> seen = new HashSet<>();
        List<Segment> edges = new ArrayList<>();
        for (Face face : faces) {
            List<Vec3> polygon = face.vertices();
            for (int i = 0; i < polygon.size(); i++) {
                Vec3 a = polygon.get(i);
                Vec3 b = polygon.get((i + 1) % polygon.size());
                if (seen.add(edgeKey(key(a), key(b)))) {
                    edges.add(new Segment(a, b));
                }
            }
        }
        return List.copyOf(edges);
    }

    /** The key of an edge from the keys of its end points, the same for both directions. */
    private static List<Long> edgeKey(List<Long> keyA, List<Long> keyB) {
        List<Long> key = new ArrayList<>(6);
        boolean forward = compareKeys(keyA, keyB) <= 0;
        key.addAll(forward ? keyA : keyB);
        key.addAll(forward ? keyB : keyA);
        return key;
    }

    private static int compareKeys(List<Long> a, List<Long> b) {
        for (int i = 0; i < a.size(); i++) {
            int compared = Long.compare(a.get(i), b.get(i));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static List<Long> key(Vec3 point) {
        return List.of(Math.round(point.x * EDGE_KEY_SCALE), Math.round(point.y * EDGE_KEY_SCALE), Math.round(point.z * EDGE_KEY_SCALE));
    }

    /**
     * The key of a seen point within half a rounding step on each axis, else the key of the point, which joins the seen keys.
     * So two copies of one point on the two sides of a rounding step get one key.
     */
    private static List<Long> snappedKey(Set<List<Long>> seen, Vec3 point) {
        double half = 0.5 / EDGE_KEY_SCALE;
        for (int corner = 0; corner < 8; corner++) {
            List<Long> key = key(point.add((corner & 1) == 0 ? -half : half, (corner & 2) == 0 ? -half : half, (corner & 4) == 0 ? -half : half));
            if (seen.contains(key)) {
                return key;
            }
        }
        List<Long> key = key(point);
        seen.add(key);
        return key;
    }

    private static List<Vec3> vertices(List<Face> faces) {
        List<Vec3> all = new ArrayList<>();
        for (Face face : faces) {
            all.addAll(face.vertices());
        }
        return List.copyOf(distinct(all));
    }

    /**
     * The segments where the planes at every {@link #GRID_SPACING} blocks on X, Y and Z cut the convex polygon.
     * A plane parallel to the polygon gives no segment, and a plane through its border gives none either.
     */
    static List<Segment> gridlines(List<Vec3> polygon, Vec3 normal) {
        List<Segment> lines = new ArrayList<>();
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(component(normal, axis)) > 1 - 1.0E-9) {
                continue;
            }
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Vec3 point : polygon) {
                min = Math.min(min, component(point, axis));
                max = Math.max(max, component(point, axis));
            }
            for (int step = Mth.ceil((min + EPSILON) / GRID_SPACING); step * (double) GRID_SPACING < max - EPSILON; step++) {
                double plane = step * (double) GRID_SPACING;
                List<Vec3> points = new ArrayList<>(2);
                for (int i = 0; i < polygon.size(); i++) {
                    Vec3 a = polygon.get(i);
                    Vec3 b = polygon.get((i + 1) % polygon.size());
                    double da = component(a, axis) - plane;
                    double db = component(b, axis) - plane;
                    if (Math.abs(da) <= EPSILON) {
                        points.add(a);
                    } else if (da < 0 && db > EPSILON || da > 0 && db < -EPSILON) {
                        points.add(crossing(a, da, b, db));
                    }
                }
                List<Vec3> ends = distinct(points);
                if (ends.size() == 2) {
                    lines.add(new Segment(ends.get(0), ends.get(1)));
                }
            }
        }
        return List.copyOf(lines);
    }

    private static double component(Vec3 point, int axis) {
        return axis == 0 ? point.x : axis == 1 ? point.y : point.z;
    }

    /** The convex hull of the X and Z of the points, counterclockwise. */
    private static List<double[]> hull(List<Vec3> points) {
        List<double[]> sorted = new ArrayList<>(points.size());
        for (Vec3 point : points) {
            sorted.add(new double[] {point.x, point.z});
        }
        sorted.sort(Comparator.<double[]>comparingDouble(point -> point[0]).thenComparingDouble(point -> point[1]));
        List<double[]> hull = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = hull.size();
            for (double[] point : sorted) {
                while (hull.size() >= start + 2 && cross(hull.get(hull.size() - 2), hull.getLast(), point) <= 0) {
                    hull.removeLast();
                }
                hull.add(point);
            }
            hull.removeLast();
            sorted = sorted.reversed();
        }
        return hull;
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** True when the convex polygon and the square overlap by a positive area. The test separates along the square axes and the polygon edge normals. */
    private static boolean overlapsSquare(List<double[]> hull, double x, double z, double size) {
        double[][] square = {{x, z}, {x + size, z}, {x + size, z + size}, {x, z + size}};
        List<double[]> axes = new ArrayList<>();
        axes.add(new double[] {1, 0});
        axes.add(new double[] {0, 1});
        for (int i = 0; i < hull.size(); i++) {
            double[] a = hull.get(i);
            double[] b = hull.get((i + 1) % hull.size());
            double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (length > EPSILON) {
                axes.add(new double[] {(a[1] - b[1]) / length, (b[0] - a[0]) / length});
            }
        }
        for (double[] axis : axes) {
            double polygonMin = Double.POSITIVE_INFINITY;
            double polygonMax = Double.NEGATIVE_INFINITY;
            for (double[] point : hull) {
                double projected = point[0] * axis[0] + point[1] * axis[1];
                polygonMin = Math.min(polygonMin, projected);
                polygonMax = Math.max(polygonMax, projected);
            }
            double squareMin = Double.POSITIVE_INFINITY;
            double squareMax = Double.NEGATIVE_INFINITY;
            for (double[] point : square) {
                double projected = point[0] * axis[0] + point[1] * axis[1];
                squareMin = Math.min(squareMin, projected);
                squareMax = Math.max(squareMax, projected);
            }
            if (polygonMax <= squareMin + EPSILON || squareMax <= polygonMin + EPSILON) {
                return false;
            }
        }
        return true;
    }

}
