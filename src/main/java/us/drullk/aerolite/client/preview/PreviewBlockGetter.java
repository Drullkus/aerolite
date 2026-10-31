package us.drullk.aerolite.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blocks of the preview region for the block renderer. Every block has full light.
 * The bake threads read it in parallel. A chunk that is not painted yet reads as air.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
final class PreviewBlockGetter implements BlockAndTintGetter, LightChunkGetter {

    private final ProtoChunk[] chunks;
    private final ChunkPos center;
    private final int radius;
    private final int minY;
    private final int height;
    private final boolean constantAmbientLight;
    private final PreviewBiomes biomeLookup;
    private final Map<Long, Holder<Biome>> biomes = new ConcurrentHashMap<>();
    private final LevelLightEngine lightEngine;

    /**
     * @param chunks the chunks of the region around {@code center}, indexed by {@link RegionChunks#index}; the paint thread fills it
     */
    PreviewBlockGetter(ProtoChunk[] chunks, ChunkPos center, int radius, int minY, int height, boolean constantAmbientLight, PreviewBiomes biomeLookup) {
        this.chunks = chunks;
        this.center = center;
        this.radius = radius;
        this.minY = minY;
        this.height = height;
        this.constantAmbientLight = constantAmbientLight;
        this.biomeLookup = biomeLookup;
        this.lightEngine = new LevelLightEngine(this, false, false);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (!RegionChunks.contains(this.center, this.radius, chunkX, chunkZ)
                || pos.getY() < this.minY || pos.getY() >= this.minY + this.height) {
            return Blocks.AIR.defaultBlockState();
        }
        ProtoChunk chunk = this.chunks[RegionChunks.index(this.center, this.radius, chunkX, chunkZ)];
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
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
        return this.height;
    }

    @Override
    public int getMinBuildHeight() {
        return this.minY;
    }

    // [VANILLACOPY] ClientLevel#getShade, with the ambient light flag of the previewed dimension
    @Override
    public float getShade(Direction direction, boolean shade) {
        boolean flag = this.constantAmbientLight;
        if (!shade) {
            return flag ? 0.9F : 1.0F;
        }
        return switch (direction) {
            case DOWN -> flag ? 0.9F : 0.5F;
            case UP -> flag ? 0.9F : 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    // [VANILLACOPY] ClientLevel#getShade(float, float, float, boolean), with the ambient light flag of the previewed dimension
    @Override
    public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
        if (!shade) {
            return this.constantAmbientLight ? 0.9F : 1.0F;
        }
        return QuadLighter.calculateShade(normalX, normalY, normalZ, this.constantAmbientLight);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Override
    public int getBrightness(LightLayer lightType, BlockPos blockPos) {
        return LightEngine.MAX_LEVEL;
    }

    @Override
    public int getRawBrightness(BlockPos blockPos, int amount) {
        return LightEngine.MAX_LEVEL;
    }

    /** The tint of the biome at the block, without the blend of the client level. */
    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        int quartX = QuartPos.fromBlock(pos.getX());
        int quartY = QuartPos.fromBlock(pos.getY());
        int quartZ = QuartPos.fromBlock(pos.getZ());
        Holder<Biome> biome = this.biomes.computeIfAbsent(BlockPos.asLong(quartX, quartY, quartZ),
                key -> this.biomeLookup.at(quartX, quartY, quartZ));
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public BlockGetter getLevel() {
        return this;
    }

}
