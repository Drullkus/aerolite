package us.drullk.aerolite.world.shaft.surface;

/**
 * The kind of one block of a ring.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum RingKind {
    /** A convex corner on an even index. It is a node without an axis edge. */
    CORNER_NODE,
    /** A convex corner on an odd index. It is a ring edge. */
    CONVEX_EDGE,
    /** A concave corner on an odd index. It is a ring edge with two floor blocks. */
    CONCAVE_EDGE,
    /** The block between the two corner nodes of a plate side. */
    PLATE_SIDE,
    /** A block of a flat face. */
    FACE;

    /** True for a convex corner. An axis step on a convex corner never links in a band. */
    public boolean isConvexCorner() {
        return this == CORNER_NODE || this == CONVEX_EDGE;
    }
}
