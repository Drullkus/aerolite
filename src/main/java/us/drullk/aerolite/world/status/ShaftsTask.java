package us.drullk.aerolite.world.status;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTask;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import us.drullk.aerolite.world.AeroliteChunkGenerator;

import java.util.concurrent.CompletableFuture;

/**
 * The generation task of the shafts step. Only an Aerolite generator builds shafts.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
public final class ShaftsTask implements ChunkStatusTask {

    @Override
    public CompletableFuture<ChunkAccess> doWork(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        if (context.generator() instanceof AeroliteChunkGenerator generator) {
            generator.buildShafts(context.level(), chunk);
        }
        return CompletableFuture.completedFuture(chunk);
    }

}
