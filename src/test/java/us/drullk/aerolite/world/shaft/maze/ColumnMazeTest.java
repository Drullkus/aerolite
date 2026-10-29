package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the pieces and column patches of a column maze.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
class ColumnMazeTest {

    private static List<MazeEdge> allEdges(ColumnMaze maze) {
        List<MazeEdge> edges = new ArrayList<>();
        maze.pieces().forEach(piece -> edges.addAll(piece.edges()));
        maze.patches().forEach(patch -> edges.addAll(patch.edges()));
        return edges;
    }

    @Test
    void columnIsOneTree() {
        for (long seed = 0; seed < 4; seed++) {
            List<ColumnMaze.Junction> junctions = List.of(new ColumnMaze.Junction(7, 1, true), new ColumnMaze.Junction(120, -1, false));
            ColumnMaze maze = ColumnMaze.build(-64, 320, junctions, new XoroshiroRandomSource(seed).forkPositional());
            assertEquals(3, maze.pieces().size());
            TreeCheck.assertOneTree(allEdges(maze));
        }
    }

    @Test
    void junctionAtTheBottomLimitLeavesAnEmptyPiece() {
        List<ColumnMaze.Junction> junctions = List.of(new ColumnMaze.Junction(7, 1, true), new ColumnMaze.Junction(60, 1, false));
        ColumnMaze maze = ColumnMaze.build(0, 256, junctions, new XoroshiroRandomSource(2).forkPositional());
        assertTrue(maze.pieces().getFirst().isEmpty());
        TreeCheck.assertOneTree(allEdges(maze));
    }

    @Test
    void oneRulingFitsBetweenJunctions() {
        assertTrue(ColumnMaze.fits(List.of(new ColumnMaze.Junction(23, 1, true), new ColumnMaze.Junction(40, 1, false))));
        assertFalse(ColumnMaze.fits(List.of(new ColumnMaze.Junction(23, 1, true), new ColumnMaze.Junction(38, 1, false))));
    }

    @Test
    void columnNodesFaceThePatch() {
        ColumnMaze.Junction alongX = new ColumnMaze.Junction(0, 1, true);
        ColumnMaze.Junction alongZ = new ColumnMaze.Junction(0, -1, false);
        for (int i = -2; i <= 2; i++) {
            int x = alongX.columnNode(i) * 2;
            assertEquals(2 * i, Ring.first(x));
            assertEquals(Ring.OUTER, Ring.second(x));
            int z = alongZ.columnNode(i) * 2;
            assertEquals(-Ring.OUTER, Ring.first(z));
            assertEquals(2 * i, Ring.second(z));
        }
    }

}
