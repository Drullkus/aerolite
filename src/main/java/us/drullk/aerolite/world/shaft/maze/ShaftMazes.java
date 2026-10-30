package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;

/**
 * Makes the maze of a horizontal shaft or a column.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component
public final class ShaftMazes {

    private final Ring ring;
    private final MazeEdges mazeEdges;

    public ShaftMazes(@Autowired Ring ring, @Autowired MazeEdges mazeEdges) {
        this.ring = ring;
        this.mazeEdges = mazeEdges;
    }

    /** The maze of an infinite horizontal shaft. */
    public ShaftMaze shaft(ShaftMaze.BoxLookup boxes, PositionalRandomFactory random) {
        return this.bounded(boxes, random, Integer.MIN_VALUE / ShaftMaze.CHUNK, Integer.MAX_VALUE / ShaftMaze.CHUNK - 1);
    }

    /** The maze of a shaft from the chunk {@code minChunk} to the chunk {@code maxChunk}, both included. */
    public ShaftMaze bounded(ShaftMaze.BoxLookup boxes, PositionalRandomFactory random, int minChunk, int maxChunk) {
        return new ShaftMaze(this.ring, this.mazeEdges, boxes, random, minChunk, maxChunk);
    }

}
