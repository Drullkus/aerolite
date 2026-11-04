package us.drullk.aerolite.world.shaft.binding;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.shaft.maze.ShaftMaze;
import us.drullk.aerolite.world.shaft.surface.RingShape;

/**
 * The input of a pattern binding for one pillar or column.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param settings the pattern settings of the variation
 * @param ring     the ring description of the shape of the variation
 * @param boxes    the junction boxes along the pillar
 * @param random   the positional random of the pillar; a segment draws from it at its own coordinates
 * @param bias     the axis edge weight of the variation, clamped to the slider range
 * @param minChunk the first chunk along the axis
 * @param maxChunk the last chunk along the axis
 */
public record PatternContext(PatternSettings settings, RingShape ring, ShaftMaze.BoxLookup boxes, PositionalRandomFactory random, float bias,
                             int minChunk, int maxChunk) {

    public PatternContext withSettings(PatternSettings settings) {
        return new PatternContext(settings, this.ring, this.boxes, this.random, this.bias, this.minChunk, this.maxChunk);
    }

}
