package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * The faces on a chunk border that an opaque block of the next chunk hides. The normal bake culls them.
 * A shift drops the next chunk and keeps this chunk, so this chunk bakes them too. They sit inside solid blocks until then.
 * Each hidden face bakes with the next chunk read as air, so its shading fits the exposed state.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
final class HiddenFaces {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private HiddenFaces() {
    }

    /**
     * Bakes the hidden faces of the border blocks of one section into the buffers of the section.
     * Call it with the model renderer cache off: the cache keys on the position only, and this bake reads other blocks than the normal bake.
     *
     * @param buffers the buffer of each layer of the section
     */
    static void bake(BlockRenderDispatcher renderer, SectionPos sectionPos, PreviewBlockGetter region, PoseStack pose, RandomSource random,
                     Function<RenderType, VertexConsumer> buffers) {
        ChunkColumnView view = new ChunkColumnView(region, new ChunkPos(sectionPos.x(), sectionPos.z()));
        BlockPos origin = sectionPos.origin();
        List<RenderType> layers = RenderType.chunkBufferLayers();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (x != 0 && x != 15 && z != 0 && z != 15) {
                        continue;
                    }
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState state = region.getBlockState(pos);
                    if (state.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    for (Direction direction : HORIZONTAL) {
                        if (!leaves(x, z, direction)) {
                            continue;
                        }
                        neighbor.setWithOffset(pos, direction);
                        // The normal bake drew a face that the next chunk does not hide.
                        if (Block.shouldRenderFace(state, region, pos, direction, neighbor)) {
                            continue;
                        }
                        bakeFace(renderer, view, state, pos, direction, pose, random, layers, buffers);
                    }
                }
            }
        }
    }

    /** True when the step from the section block at {@code x}, {@code z} in {@code direction} leaves the chunk. */
    private static boolean leaves(int x, int z, Direction direction) {
        return switch (direction) {
            case WEST -> x == 0;
            case EAST -> x == 15;
            case NORTH -> z == 0;
            case SOUTH -> z == 15;
            default -> false;
        };
    }

    private static void bakeFace(BlockRenderDispatcher renderer, ChunkColumnView view, BlockState state, BlockPos pos, Direction direction,
                                 PoseStack pose, RandomSource random, List<RenderType> layers, Function<RenderType, VertexConsumer> buffers) {
        BakedModel model = renderer.getBlockModel(state);
        ModelData modelData = model.getModelData(view, pos, state, ModelData.EMPTY);
        FaceModel face = new FaceModel(model, direction);
        long seed = state.getSeed(pos);
        random.setSeed(seed);
        for (RenderType layer : model.getRenderTypes(state, random, modelData)) {
            if (!layers.contains(layer)) {
                continue;
            }
            pose.pushPose();
            pose.translate(SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ()));
            renderer.getModelRenderer().tesselateBlock(view, face, state, pos, pose, buffers.apply(layer), true, random, seed, OverlayTexture.NO_OVERLAY, modelData, layer);
            pose.popPose();
        }
    }

    /** A model that keeps only the quads of one face. The quads without a face are already in the normal bake. */
    private static final class FaceModel extends BakedModelWrapper<BakedModel> {

        private final Direction face;

        FaceModel(BakedModel model, Direction face) {
            super(model);
            this.face = face;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return side == this.face ? super.getQuads(state, side, random) : List.of();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType layer) {
            return side == this.face ? super.getQuads(state, side, random, data, layer) : List.of();
        }

    }

    /** The region with every block outside one chunk column read as air. */
    private static final class ChunkColumnView implements BlockAndTintGetter {

        private final PreviewBlockGetter region;
        private final ChunkPos chunk;

        ChunkColumnView(PreviewBlockGetter region, ChunkPos chunk) {
            this.region = region;
            this.chunk = chunk;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getX() >> 4 != this.chunk.x || pos.getZ() >> 4 != this.chunk.z) {
                return AIR;
            }
            return this.region.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return this.region.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.region.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.region.getShade(direction, shade);
        }

        @Override
        public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
            return this.region.getShade(normalX, normalY, normalZ, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.region.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer lightType, BlockPos pos) {
            return this.region.getBrightness(lightType, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return this.region.getRawBrightness(pos, amount);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return this.region.getBlockTint(pos, resolver);
        }

    }

}
