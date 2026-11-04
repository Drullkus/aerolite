package us.drullk.aerolite.world.shaft.binding.maze;

import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.shaft.binding.PatternBinding;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.maze.MazeEdges;
import us.drullk.aerolite.world.shaft.maze.MazePattern;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

/**
 * The binding of the maze pattern. The maze settings of the context pick the maze type.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class MazePatternBinding implements PatternBinding {

    private final MazeEdges mazeEdges;

    public MazePatternBinding(@Autowired MazeEdges mazeEdges) {
        this.mazeEdges = mazeEdges;
    }

    @Override
    public Class<? extends PatternSettings> settings() {
        return MazeSettings.class;
    }

    @Override
    public SurfacePattern create(PatternContext context) {
        return new MazePattern(new ShaftMaze(context.ring(), this.mazeEdges, context.boxes(), context.random(), context.minChunk(), context.maxChunk(), context.bias(),
                (MazeSettings) context.settings()));
    }

}
