package us.drullk.aerolite.world.shaft.surface.path;

/**
 * Packs a band position into one {@code long}: the axis position in the high 32 bits, the ring position in the low 32 bits.
 *
 * <p>The ring position of a packed position is always wrapped into {@code [0, ring length)}.
 * So the signed comparison of two packed positions sorts by axis position first, then by ring position.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class BandPos {

    /** The value for no position, for example a step out of the axis range. No band holds the axis position {@link Integer#MIN_VALUE}. */
    public static final long NONE = Long.MIN_VALUE;

    private BandPos() {
    }

    /** The packed position. The ring position must be wrapped already. */
    public static long pack(int ring, int axis) {
        return ((long) axis << 32) | (ring & 0xFFFFFFFFL);
    }

    public static int ring(long pos) {
        return (int) pos;
    }

    public static int axis(long pos) {
        return (int) (pos >> 32);
    }

    public static String toString(long pos) {
        return pos == NONE ? "none" : "(" + ring(pos) + ", " + axis(pos) + ")";
    }

}
