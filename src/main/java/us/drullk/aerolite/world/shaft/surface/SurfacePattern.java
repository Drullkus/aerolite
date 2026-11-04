package us.drullk.aerolite.world.shaft.surface;

/**
 * The surface pattern of one pillar or column. Each segment is a pure function of the seed, the pillar and the chunk index.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public interface SurfacePattern {

    /** The ring description of the shape of the pillar. */
    RingShape ring();

    /** The axis edge weight of the variation of the pillar. */
    float bias();

    /** The first chunk along the axis that the pattern covers. */
    int minChunk();

    /** The last chunk along the axis that the pattern covers. */
    int maxChunk();

    /** The surface of the segment that holds the chunk at index {@code chunk} along the axis. */
    SegmentSurface segmentAt(int chunk);

}
