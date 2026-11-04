package us.drullk.aerolite.world.shaft.maze;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.shaft.binding.PatternBindings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.binding.maze.MazePatternBinding;
import us.drullk.aerolite.world.shaft.shape.ShapeRings;
import us.drullk.aerolite.world.shaft.surface.PillarRing;
import us.drullk.aerolite.world.shaft.surface.RingShape;
import us.drullk.aerolite.world.shaft.surface.SurfacePattern;

import java.util.List;

/**
 * Makes the maze of a horizontal shaft or a column, and the surface pattern of a variation through the pattern bindings.
 * The shaft layout reaches the bindings and the ring descriptions through this component.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ShaftMazes {

    private final MazeEdges mazeEdges;
    private final PatternBindings bindings;
    private final ShapeRings shapes;

    public ShaftMazes(@Autowired MazeEdges mazeEdges, @Autowired PatternBindings bindings, @Autowired ShapeRings shapes) {
        this.mazeEdges = mazeEdges;
        this.bindings = bindings;
        this.shapes = shapes;
    }

    /** A maze factory for code that runs without the bean context. Its only pattern binding is the maze binding. */
    public ShaftMazes(MazeEdges mazeEdges) {
        this(mazeEdges, PatternBindings.of(List.of(new MazePatternBinding(mazeEdges))), new ShapeRings());
    }

    /**
     * The Pillar maze of an infinite horizontal shaft.
     *
     * @param bias the axis edge weight of the maze sampler
     */
    public ShaftMaze shaft(ShaftMaze.BoxLookup boxes, PositionalRandomFactory random, float bias) {
        return this.bounded(boxes, random, Integer.MIN_VALUE / ShaftMaze.CHUNK, Integer.MAX_VALUE / ShaftMaze.CHUNK - 1, bias);
    }

    /**
     * The Pillar maze of a shaft from the chunk {@code minChunk} to the chunk {@code maxChunk}, both included.
     *
     * @param bias the axis edge weight of the maze sampler
     */
    public ShaftMaze bounded(ShaftMaze.BoxLookup boxes, PositionalRandomFactory random, int minChunk, int maxChunk, float bias) {
        return new ShaftMaze(PillarRing.INSTANCE, this.mazeEdges, boxes, random, minChunk, maxChunk, bias);
    }

    /** The ring description of a shape. */
    public RingShape ring(PillarShape shape) {
        return this.shapes.ring(shape);
    }

    /**
     * The surface pattern of a variation on a shaft from the chunk {@code minChunk} to the chunk {@code maxChunk}, both included.
     * The shape of the variation gives the ring description. The bias of the variation clamps to the slider range.
     */
    public SurfacePattern pattern(ShaftVariation variation, ShaftMaze.BoxLookup boxes, PositionalRandomFactory random, int minChunk, int maxChunk) {
        PatternContext context = new PatternContext(variation.pattern(), this.ring(variation.shape()), boxes, random,
                ShaftVariation.clampBias(variation.bias()), minChunk, maxChunk);
        return this.bindings.create(context);
    }

}
