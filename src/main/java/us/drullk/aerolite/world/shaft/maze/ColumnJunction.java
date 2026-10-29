package us.drullk.aerolite.world.shaft.maze;

/**
 * A junction on a column.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 *
 * @param center the Y of the junction center
 * @param sign   the sign of the column patch face, along the second offset of the horizontal shaft
 * @param alongX true when the horizontal shaft runs along X
 */
public record ColumnJunction(int center, int sign, boolean alongX) {

    /** The box of this junction in the column maze. */
    public ShaftMaze.Box box() {
        return new ShaftMaze.Box(this.center, this.sign, this.alongX ? ShaftMaze.Patching.COLUMN_ALONG_X : ShaftMaze.Patching.COLUMN_ALONG_Z);
    }

}
