package us.drullk.aerolite.world.shaft.surface;

/**
 * The ring block that faces one column of a junction patch.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param ring  the ring, or -1 when no ring block faces the column
 * @param index the ring index, or -1 when no ring block faces the column
 */
public record PatchColumn(int ring, int index) {

    /** No ring block faces the column. */
    public static final PatchColumn NONE = new PatchColumn(-1, -1);

    public boolean present() {
        return this.ring >= 0;
    }
}
