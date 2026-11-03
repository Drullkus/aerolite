package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * The fit check of one surface path against a band, the random pick of one fitting path, and the placement.
 *
 * <p>The base rules always hold. A path block must be wall now. A path block touches path blocks only at its own neighbors
 * in the path and at its end grooves. So no path is 2 wide, and a U with touching legs fails.
 * Each step of the path links, so no path runs along a corner. No end groove gets path neighbors on all four sides.
 *
 * <p>The {@link Rules} add the rules of a pattern: the cut mode, the orphan rule, the face width rule and the exempt blocks.
 *
 * <p>The pick draws like the prototype: one {@code nextDouble} for the class of the path, then one {@code nextInt} for the path.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class PathFit {

    /** The chance that a pick wants a path that ends on a face node. */
    public static final double DEAD_END_SHARE = 0.5;

    private PathFit() {
    }

    /**
     * The rules of a pattern for its surface paths.
     *
     * @param cut true when the path carves grooves, false when it writes surface path blocks
     * @param allowOrphans true when a cut path can leave a wall block with grooves on all four sides
     * @param faceWidth true when each wall face beside the path must keep a 2x2 square of wall blocks
     * @param exempt the blocks of a path that can touch the two path blocks beside them around the ring, for example the Scales tail blocks
     */
    public record Rules(boolean cut, boolean allowOrphans, boolean faceWidth, LongSet exempt) {

        /** Surface path blocks, no face width rule, no exempt block. */
        public static final Rules SURFACE = new Rules(false, false, false, LongSets.EMPTY_SET);

        /** Cut paths without orphans, no face width rule, no exempt block. */
        public static final Rules CUT = new Rules(true, false, false, LongSets.EMPTY_SET);

        public Rules {
            exempt = LongSets.unmodifiable(new LongOpenHashSet(exempt));
        }

        public Rules withOrphans(boolean allow) {
            return new Rules(this.cut, allow, this.faceWidth, this.exempt);
        }

        public Rules withFaceWidth(boolean keep) {
            return new Rules(this.cut, this.allowOrphans, keep, this.exempt);
        }

        public Rules withExempt(LongSet blocks) {
            return new Rules(this.cut, this.allowOrphans, this.faceWidth, blocks);
        }
    }

    /** True when the path can go on the band under the rules. */
    public static boolean fits(Band band, SurfacePath path, Rules rules) {
        LongList blocks = path.blocks();
        LongSet own = new LongOpenHashSet(blocks);
        if (own.size() != blocks.size()) {
            return false;
        }
        for (long pos : blocks) {
            if (!band.inside(pos) || band.get(pos) != BandKind.WALL) {
                return false;
            }
        }
        LongPredicate on = q -> own.contains(q) || band.isPath(q);
        LongList seq = path.sequence();
        for (int i = 1; i <= blocks.size(); i++) {
            long pos = seq.getLong(i);
            LongSet allowed = new LongOpenHashSet();
            allowed.add(seq.getLong(i - 1));
            if (i + 1 < seq.size()) {
                allowed.add(seq.getLong(i + 1));
            }
            if (rules.exempt().contains(pos)) {
                allowed.add(band.step(pos, BandDirection.RING_PLUS));
                allowed.add(band.step(pos, BandDirection.RING_MINUS));
            }
            for (long q : band.neighbors(pos)) {
                if (on.test(q) && !allowed.contains(q)) {
                    return false;
                }
            }
        }
        for (int i = 0; i + 1 < seq.size(); i++) {
            if (band.direction(seq.getLong(i), seq.getLong(i + 1)) == null || !band.linked(seq.getLong(i), seq.getLong(i + 1))) {
                return false;
            }
        }
        for (long end : path.ends()) {
            int count = 0;
            for (long q : band.neighbors(end)) {
                if (on.test(q)) {
                    count++;
                }
            }
            if (count >= 4) {
                return false;
            }
        }
        for (long pos : blocks) {
            for (int dr = -1; dr <= 0; dr++) {
                for (int da = -1; da <= 0; da++) {
                    long low = band.pos(BandPos.ring(pos) + dr, BandPos.axis(pos) + da);
                    if (BandChecks.wallSquare(band, low, q -> band.inside(q) && on.test(q))) {
                        return false;
                    }
                }
            }
        }
        if (rules.cut() && !rules.allowOrphans() && leavesOrphan(band, own, on)) {
            return false;
        }
        return !rules.faceWidth() || keepsFaceWidth(band, own);
    }

    /** True when a wall block next to the cut path gets grooves on all four sides. */
    private static boolean leavesOrphan(Band band, LongSet own, LongPredicate on) {
        for (long pos : own) {
            for (long w : band.neighbors(pos)) {
                if (own.contains(w) || band.get(w) != BandKind.WALL) {
                    continue;
                }
                LongList around = band.neighbors(w);
                boolean grooved = around.size() == 4;
                for (int i = 0; grooved && i < 4; i++) {
                    long q = around.getLong(i);
                    grooved = on.test(q) && band.get(q) != BandKind.SURFACE;
                }
                if (grooved) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True when each wall face beside the path keeps a 2x2 square of wall blocks. The path blocks count as no wall.
     * A face that reaches the first or the last axis position of the band counts as wide.
     */
    private static boolean keepsFaceWidth(Band band, LongSet own) {
        LongPredicate wall = q -> band.inside(q) && !own.contains(q) && band.get(q) == BandKind.WALL;
        LongSet done = new LongOpenHashSet();
        for (long pos : own) {
            for (long start : band.neighbors(pos)) {
                if (!wall.test(start) || done.contains(start)) {
                    continue;
                }
                boolean wide = false;
                LongArrayList stack = LongArrayList.of(start);
                done.add(start);
                while (!stack.isEmpty() && !wide) {
                    long q = stack.popLong();
                    int axis = BandPos.axis(q);
                    wide = axis == band.axis0() || axis == band.axisEnd() - 1 || BandChecks.wallSquare(band, q, wall);
                    for (long next : band.neighbors(q)) {
                        if (wall.test(next) && done.add(next)) {
                            stack.add(next);
                        }
                    }
                }
                if (!wide) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * One fitting candidate, or null: a path that ends on a face node with the chance {@link #DEAD_END_SHARE}, else a path between two grooves.
     * The pick falls back to the other class when one class has no fitting path.
     */
    @Nullable
    public static SurfacePath pick(Band band, List<SurfacePath> candidates, RandomSource random, Rules rules) {
        return pick(band, candidates, random, rules, DEAD_END_SHARE);
    }

    /** {@link #pick} with its own chance of a path that ends on a face node. */
    @Nullable
    public static SurfacePath pick(Band band, List<SurfacePath> candidates, RandomSource random, Rules rules, double deadEndShare) {
        List<SurfacePath> through = new ArrayList<>();
        List<SurfacePath> dead = new ArrayList<>();
        for (SurfacePath path : candidates) {
            if (fits(band, path, rules)) {
                (path.isThrough() ? through : dead).add(path);
            }
        }
        boolean wantDead = random.nextDouble() < deadEndShare;
        List<SurfacePath> pool = wantDead ? (dead.isEmpty() ? through : dead) : (through.isEmpty() ? dead : through);
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** Writes the path blocks: grooves when cut, else surface path blocks. */
    public static void place(Band band, SurfacePath path, boolean cut) {
        for (long pos : path.blocks()) {
            if (band.inside(pos)) {
                band.set(pos, cut ? BandKind.GROOVE : BandKind.SURFACE);
            }
        }
    }

}
