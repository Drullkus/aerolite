package us.drullk.aerolite.world.status;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTask;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import tamaized.beanification.BeanContext;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * The generation task of the shafts step. Any level with bound shaft data builds shafts, whatever its generator.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public final class ShaftsTask implements ChunkStatusTask {

    /** The pyramids build this task before the bean context exists, so the step resolves on first use. */
    @Nullable
    private volatile ShaftsStep step;

    @Override
    public CompletableFuture<ChunkAccess> doWork(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        this.step().paint(context.level(), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    private ShaftsStep step() {
        ShaftsStep resolved = this.step;
        if (resolved == null) {
            resolved = BeanContext.inject(ShaftsStep.class);
            this.step = resolved;
        }
        return resolved;
    }

}
