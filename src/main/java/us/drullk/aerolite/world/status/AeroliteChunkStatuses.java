package us.drullk.aerolite.world.status;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.jetbrains.annotations.ApiStatus;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteChunkGenerator;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The shafts chunk status. It sits between the carvers and the features.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 */
public final class AeroliteChunkStatuses {

    public static final ResourceLocation SHAFTS_ID = ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "shafts");

    @Nullable
    private static ChunkStatus shafts;

    private AeroliteChunkStatuses() {
    }

    public static ChunkStatus shafts() {
        return Objects.requireNonNull(shafts, "The shafts chunk status is not registered");
    }

    /** Takes the status that the chunk status registration creates. */
    @ApiStatus.Internal
    public static void bind(ChunkStatus status) {
        shafts = status;
    }

    /** The generation task of the shafts step. */
    public static CompletableFuture<ChunkAccess> generateShafts(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk
    ) {
        if (context.generator() instanceof AeroliteChunkGenerator generator) {
            generator.buildShafts(context.level(), chunk);
        }
        return CompletableFuture.completedFuture(chunk);
    }

}
