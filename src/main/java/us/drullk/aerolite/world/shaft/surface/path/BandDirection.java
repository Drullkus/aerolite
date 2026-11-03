package us.drullk.aerolite.world.shaft.surface.path;

/**
 * One of the four steps on a band. The ring steps come first, then the axis steps.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum BandDirection {
    RING_PLUS(1, 0),
    RING_MINUS(-1, 0),
    AXIS_PLUS(0, 1),
    AXIS_MINUS(0, -1);

    private final int ring;
    private final int axis;

    BandDirection(int ring, int axis) {
        this.ring = ring;
        this.axis = axis;
    }

    public int ring() {
        return this.ring;
    }

    public int axis() {
        return this.axis;
    }

    public boolean isAxis() {
        return this.axis != 0;
    }

    public BandDirection opposite() {
        return switch (this) {
            case RING_PLUS -> RING_MINUS;
            case RING_MINUS -> RING_PLUS;
            case AXIS_PLUS -> AXIS_MINUS;
            case AXIS_MINUS -> AXIS_PLUS;
        };
    }
}
