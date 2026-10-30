package us.drullk.aerolite.world.shaft.maze;

/**
 * A tree edge of the maze, which carves a maze path.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
public record MazeEdge(MazeNode a, MazeNode b) {

    /** Maps a virtual node to the real node that its edge reaches in the neighbor tree. */
    @FunctionalInterface
    interface VirtualTarget {
        MazeNode target(int virtualNode, MazeNode partner);
    }

}
