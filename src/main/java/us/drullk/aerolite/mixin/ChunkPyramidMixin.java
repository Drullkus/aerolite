package us.drullk.aerolite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import us.drullk.aerolite.world.status.AeroliteChunkStatuses;
import us.drullk.aerolite.world.status.ShaftsTask;

import java.util.function.UnaryOperator;

/**
 * Adds the shafts step before the features step of both chunk pyramids.
 * In the generation pyramid, the features step waits for the shafts of its neighbors.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
// No API adds a chunk step. The pyramids build their steps once while the class initializes.
@Mixin(ChunkPyramid.class)
abstract class ChunkPyramidMixin {

    @Shadow
    @Final
    public static ChunkPyramid GENERATION_PYRAMID;

    @WrapOperation(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/status/ChunkPyramid$Builder;step(Lnet/minecraft/world/level/chunk/status/ChunkStatus;Ljava/util/function/UnaryOperator;)Lnet/minecraft/world/level/chunk/status/ChunkPyramid$Builder;"
            )
    )
    private static ChunkPyramid.Builder aerolite$insertShaftsStep(
            ChunkPyramid.Builder builder,
            ChunkStatus status,
            UnaryOperator<ChunkStep.Builder> task,
            Operation<ChunkPyramid.Builder> original
    ) {
        if (status != ChunkStatus.FEATURES) {
            return original.call(builder, status, task);
        }
        // The pyramids build before the bean context exists.
        ChunkStatus shafts = BuiltInRegistries.CHUNK_STATUS.getOptional(AeroliteChunkStatuses.SHAFTS_ID).orElseThrow();
        // The generation pyramid builds first, so its field is still unset here.
        if (GENERATION_PYRAMID == null) {
            UnaryOperator<ChunkStep.Builder> shaftsTask = step -> step.blockStateWriteRadius(0).setTask(new ShaftsTask());
            UnaryOperator<ChunkStep.Builder> featuresTask = step -> task.apply(step).addRequirement(shafts, 1);
            return original.call(original.call(builder, shafts, shaftsTask), status, featuresTask);
        }
        return original.call(original.call(builder, shafts, UnaryOperator.<ChunkStep.Builder>identity()), status, task);
    }

}
