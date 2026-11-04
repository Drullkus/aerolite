package us.drullk.aerolite.world.shaft.pattern;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;

/**
 * The band of one ring over a range of chunks after the surface paths and the loop rationing.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param band   the finished band of the chunk range
 * @param base   the band of the layout over the same range, without surface paths and without rationing
 * @param result the rationing of the whole scene, margin included
 * @param tails  the tail blocks inside the chunk range
 */
public record PatternScene(Band band, Band base, LoopRationing.Result result, LongSet tails) {

    public PatternScene {
        tails = LongSets.unmodifiable(new LongOpenHashSet(tails));
    }

}
