package us.drullk.aerolite.world.shaft.pattern;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import us.drullk.aerolite.world.shaft.surface.path.BandPos;

/**
 * One region of a pattern, a brick or a tile. A surface path runs between the face nodes of one region.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param blocks the band positions of the region
 * @param tail   the tail block of a scale, or {@link BandPos#NONE}
 * @param last   true when the region belongs to the last regions of its chunk, which draw first
 */
public record PatternRegion(LongSet blocks, long tail, boolean last) {

    public PatternRegion {
        blocks = LongSets.unmodifiable(new LongOpenHashSet(blocks));
    }

    public PatternRegion(LongSet blocks, boolean last) {
        this(blocks, BandPos.NONE, last);
    }

}
