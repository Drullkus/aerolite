package us.drullk.aerolite.world.shaft.surface.path;

/**
 * The kind of one block of a band.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum BandKind {
    /** A block of the wall layer. It keeps the maze wall block. */
    WALL,
    /** A maze path below the wall layer. */
    GROOVE,
    /** A block on the wall layer that becomes the surface path block. */
    SURFACE,
    /** A block of a junction box. It is not part of the pattern. */
    BOX;

    private static final BandKind[] VALUES = values();

    /** True for the kinds that count as path: groove and surface path. */
    public boolean isPath() {
        return this == GROOVE || this == SURFACE;
    }

    /** True for the kinds on the wall layer: wall and surface path. */
    public boolean isWallLayer() {
        return this == WALL || this == SURFACE;
    }

    static BandKind byId(int id) {
        return VALUES[id];
    }
}
