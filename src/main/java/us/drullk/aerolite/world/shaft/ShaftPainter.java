package us.drullk.aerolite.world.shaft;

import net.minecraft.world.level.chunk.ChunkAccess;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;

/**
 * Writes the shafts, columns and junctions of one chunk.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component
public final class ShaftPainter {

    private final MazeCarver carver;
    private final ShaftFrames frames;
    private final ErosionResistance resistance;

    public ShaftPainter(@Autowired MazeCarver carver, @Autowired ShaftFrames frames, @Autowired ErosionResistance resistance) {
        this.carver = carver;
        this.frames = frames;
        this.resistance = resistance;
    }

    public void paint(ShaftLayout layout, ChunkAccess chunk) {
        new ChunkPaint(this.carver, this.frames, this.resistance, layout, chunk).paint();
    }

}
