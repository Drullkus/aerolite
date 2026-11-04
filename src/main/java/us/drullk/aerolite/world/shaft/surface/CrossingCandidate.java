package us.drullk.aerolite.world.shaft.surface;

/**
 * A ring node that can cross onto a junction patch from each side of the box.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param node    the node, as a node index of {@link RingShape}
 * @param across  the patch index across the pillar of the patch border node that the crossing reaches
 * @param foreign true when the crossing reaches the patch of the other pillar at the box
 */
public record CrossingCandidate(int node, int across, boolean foreign) {
}
