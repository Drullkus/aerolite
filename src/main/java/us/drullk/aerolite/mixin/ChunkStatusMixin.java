package us.drullk.aerolite.mixin;

import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import us.drullk.aerolite.world.status.AeroliteChunkStatuses;

import java.util.EnumSet;

/**
 * Registers the shafts chunk status as the parent of the features status.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
// No API adds a chunk status. Each status takes its index from its parent while the class initializes.
@Mixin(ChunkStatus.class)
abstract class ChunkStatusMixin {

    @Shadow
    @Final
    public static EnumSet<Heightmap.Types> FINAL_HEIGHTMAPS;

    @Shadow
    private static ChunkStatus register(String name, ChunkStatus parent, EnumSet<Heightmap.Types> heightmapsAfter, ChunkType chunkType) {
        throw new AssertionError();
    }

    @ModifyArg(
            method = "<clinit>",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/status/ChunkStatus;CARVERS:Lnet/minecraft/world/level/chunk/status/ChunkStatus;", opcode = Opcodes.PUTSTATIC)),
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/status/ChunkStatus;register(Ljava/lang/String;Lnet/minecraft/world/level/chunk/status/ChunkStatus;Ljava/util/EnumSet;Lnet/minecraft/world/level/chunk/status/ChunkType;)Lnet/minecraft/world/level/chunk/status/ChunkStatus;",
                    ordinal = 0
            ),
            index = 1
    )
    private static ChunkStatus aerolite$insertShafts(ChunkStatus carvers) {
        return register(AeroliteChunkStatuses.SHAFTS_ID.toString(), carvers, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    }

}
