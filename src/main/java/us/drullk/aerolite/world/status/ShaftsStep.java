package us.drullk.aerolite.world.status;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.level.LevelShafts;
import us.drullk.aerolite.world.level.LevelShaftsBinding;
import us.drullk.aerolite.world.shaft.ShaftPainter;

/**
 * The work of the shafts step: it paints the shafts of the level's shaft data into one chunk.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class ShaftsStep {

    private final LevelShaftsBinding binding;
    private final ShaftPainter painter;

    public ShaftsStep(@Autowired LevelShaftsBinding binding, @Autowired ShaftPainter painter) {
        this.binding = binding;
        this.painter = painter;
    }

    /** Writes the shafts, columns and junctions of one chunk. A level without shaft variations gets no shafts. */
    public void paint(ServerLevel level, ChunkAccess chunk) {
        LevelShafts shafts = this.binding.get(level);
        if (shafts != null && shafts.layout().hasShafts()) {
            this.painter.paint(shafts.layout(), chunk);
        }
    }

}
