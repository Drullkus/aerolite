package us.drullk.aerolite.world.shaft.surface;

/**
 * A ring node that can cross onto a junction patch from one side of the box or from both sides.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param node    the node, as a node index of {@link RingShape}
 * @param across  the patch index across the pillar of the patch border node that the crossing reaches
 * @param foreign true when the crossing reaches the patch of the other pillar at the box
 * @param side    the sides of the box that offer the crossing
 */
public record CrossingCandidate(int node, int across, boolean foreign, Side side) {

    /** A candidate on both sides of the box. */
    public CrossingCandidate(int node, int across, boolean foreign) {
        this(node, across, foreign, Side.BOTH);
    }

    /** This candidate, offered on the given sides of the box. */
    public CrossingCandidate onSide(Side side) {
        return new CrossingCandidate(this.node, this.across, this.foreign, side);
    }

    /** The sides of a junction box along the pillar axis. */
    public enum Side {
        /** Both sides of the box. */
        BOTH,
        /** The side toward the lower axis positions. */
        BEFORE,
        /** The side toward the higher axis positions. */
        AFTER;

        public boolean before() {
            return this != AFTER;
        }

        public boolean after() {
            return this != BEFORE;
        }
    }
}
