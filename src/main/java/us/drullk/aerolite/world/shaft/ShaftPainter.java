package us.drullk.aerolite.world.shaft;

import net.minecraft.world.level.chunk.ChunkAccess;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;

/**
 * Writes the shafts, columns and junctions of one chunk.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Component
public final class ShaftPainter {

    private final SurfaceCarver carver;
    private final ShaftFrames frames;
    private final ErosionResistance resistance;

    public ShaftPainter(@Autowired SurfaceCarver carver, @Autowired ShaftFrames frames, @Autowired ErosionResistance resistance) {
        this.carver = carver;
        this.frames = frames;
        this.resistance = resistance;
    }

    /** Paints nothing when the dimension of the layout has no variation. */
    public void paint(ShaftLayout layout, ChunkAccess chunk) {
        if (!layout.hasShafts()) {
            return;
        }
        new ChunkPaint(this.carver, this.frames, this.resistance, layout, chunk).paint();
    }

}
