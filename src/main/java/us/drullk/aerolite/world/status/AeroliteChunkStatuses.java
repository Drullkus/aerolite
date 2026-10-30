package us.drullk.aerolite.world.status;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import tamaized.beanification.Component;
import us.drullk.aerolite.Aerolite;

/**
 * The shafts chunk status. It sits between the carvers and the features.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component
public final class AeroliteChunkStatuses {

    public static final ResourceLocation SHAFTS_ID = ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "shafts");

    public ChunkStatus shafts() {
        return BuiltInRegistries.CHUNK_STATUS.getOptional(SHAFTS_ID).orElseThrow(() -> new IllegalStateException("The shafts chunk status is not registered"));
    }

}
