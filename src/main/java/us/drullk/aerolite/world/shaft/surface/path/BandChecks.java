package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.function.LongPredicate;

/**
 * The checks of a whole band. Each check gives the positions that break its rule, sorted. A valid band gives none.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class BandChecks {

    private BandChecks() {
    }

    /** Every 2x2 square of path blocks, as the position of its low corner. The ring wraps. The axis does not. */
    public static LongList twoWide(Band band) {
        LongList out = new LongArrayList();
        for (int axis = band.axis0(); axis < band.axisEnd() - 1; axis++) {
            for (int ring = 0; ring < band.ringLength(); ring++) {
                if (band.get(ring, axis).isPath() && band.get(ring + 1, axis).isPath()
                    && band.get(ring, axis + 1).isPath() && band.get(ring + 1, axis + 1).isPath()) {
                    out.add(band.pos(ring, axis));
                }
            }
        }
        return out;
    }

    /** Every path block with path neighbors on all four sides. A block outside the band is no path. */
    public static LongList crossings(Band band) {
        LongList out = new LongArrayList();
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            for (int ring = 0; ring < band.ringLength(); ring++) {
                long pos = band.pos(ring, axis);
                if (band.get(pos).isPath() && pathOnAllSides(band, pos)) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    private static boolean pathOnAllSides(Band band, long pos) {
        for (BandDirection direction : BandDirection.values()) {
            if (!band.isPath(band.step(pos, direction))) {
                return false;
            }
        }
        return true;
    }

    /** Every path block whose next block along the axis on the same corner is a path block too. */
    public static LongList cornerRuns(Band band) {
        LongList out = new LongArrayList();
        for (int axis = band.axis0(); axis < band.axisEnd() - 1; axis++) {
            for (int corner : band.corners()) {
                if (band.get(corner, axis).isPath() && band.get(corner, axis + 1).isPath()) {
                    out.add(band.pos(corner, axis));
                }
            }
        }
        out.sort(null);
        return out;
    }

    /** Every wall block with grooves on all four sides. A block outside the band is no groove. */
    public static LongList orphans(Band band) {
        LongList out = new LongArrayList();
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            for (int ring = 0; ring < band.ringLength(); ring++) {
                long pos = band.pos(ring, axis);
                if (band.get(pos) == BandKind.WALL && isOrphan(band, pos)) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    private static boolean isOrphan(Band band, long pos) {
        for (BandDirection direction : BandDirection.values()) {
            long next = band.step(pos, direction);
            if (next == BandPos.NONE || band.get(next) != BandKind.GROOVE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every narrow face, as its lowest position. A face is a group of wall layer blocks that link around the ring and along the axis.
     * A narrow face holds no 2x2 square of wall layer blocks. A face that touches the first or the last axis position of the band does not count.
     */
    public static LongList narrowFaces(Band band) {
        LongList out = new LongArrayList();
        LongSet seen = new LongOpenHashSet();
        for (int axis = band.axis0(); axis < band.axisEnd(); axis++) {
            for (int ring = 0; ring < band.ringLength(); ring++) {
                long start = band.pos(ring, axis);
                if (seen.contains(start) || !band.get(start).isWallLayer()) {
                    continue;
                }
                boolean wide = false;
                boolean touchesEnd = false;
                LongArrayList stack = LongArrayList.of(start);
                seen.add(start);
                while (!stack.isEmpty()) {
                    long pos = stack.popLong();
                    int a = BandPos.axis(pos);
                    touchesEnd |= a == band.axis0() || a == band.axisEnd() - 1;
                    wide |= wallSquare(band, pos, p -> band.get(p).isWallLayer());
                    for (long next : band.neighbors(pos)) {
                        if (band.get(next).isWallLayer() && seen.add(next)) {
                            stack.add(next);
                        }
                    }
                }
                if (!wide && !touchesEnd) {
                    out.add(start);
                }
            }
        }
        return out;
    }

    /** True when the position is the low corner of a 2x2 square whose four blocks pass the test. */
    static boolean wallSquare(Band band, long pos, LongPredicate wall) {
        long up = band.step(pos, BandDirection.AXIS_PLUS);
        if (up == BandPos.NONE) {
            return false;
        }
        long side = band.step(pos, BandDirection.RING_PLUS);
        long corner = band.step(up, BandDirection.RING_PLUS);
        return wall.test(pos) && wall.test(up) && wall.test(side) && wall.test(corner);
    }

}
