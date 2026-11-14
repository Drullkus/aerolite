package us.drullk.aerolite.world.island;

/**
 * A density function that holds islands in the cells of a lattice. The islands preview draws its cells.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public interface IslandCells {

    VoronoiLattice lattice();

    /** True while the cell holds an island. */
    boolean hasIsland(VoronoiLattice.Cell cell);

}
