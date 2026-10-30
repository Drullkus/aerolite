package us.drullk.aerolite.world.shaft.maze;

/**
 * A node of the maze grid, named in the coordinates of its shaft.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
public sealed interface MazeNode {

    /** A node on the ruling at the block {@code along} of the shaft axis, at ring node {@code node}. */
    record Ruling(int along, int node) implements MazeNode {
    }

    /** A node of the patch on the junction box whose center is at {@code owner} along the shaft axis. */
    record Patch(int owner, int i, int j) implements MazeNode {
    }

}
