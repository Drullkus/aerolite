package us.drullk.aerolite.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

/**
 * Opens the cell sizes and the interpolated block state of a noise chunk to the island terrain fill of the preview.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
// No API fills a chunk from noise without a structure manager. The noise generator keeps these three methods protected.
@Mixin(NoiseChunk.class)
public interface NoiseChunkAccessor {

    @Invoker("cellWidth")
    int aerolite$cellWidth();

    @Invoker("cellHeight")
    int aerolite$cellHeight();

    @Nullable
    @Invoker("getInterpolatedState")
    BlockState aerolite$getInterpolatedState();

}
