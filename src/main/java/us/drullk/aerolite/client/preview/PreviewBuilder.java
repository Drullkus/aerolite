package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.client.model.data.ModelData;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Paints the shafts of the preview region and bakes its sections on one worker thread.
 * Each request starts a new generation after a debounce. A newer request stops an older generation at the next section.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public final class PreviewBuilder implements AutoCloseable {

    public static final long DEBOUNCE_MILLIS = 300;
    private static final int START_BUFFER_SIZE = 16384;
    private static final long JOIN_MILLIS = 2000;

    /** The input of one generation. */
    public record Request(AeroliteSettings settings, long seed, PreviewDimension dimension, int radius, boolean constantAmbientLight,
                          Registry<Biome> biomes, HolderGetter<NormalNoise.NoiseParameters> noise) {
    }

    public enum Phase {
        WAITING,
        PAINTING,
        BAKING,
        DONE
    }

    /** The progress of the newest generation. */
    public record Progress(Phase phase, int done, int total) {

        public float fraction() {
            return this.total <= 0 ? 0 : this.done / (float) this.total;
        }
    }

    /** A message from the worker to the render thread. */
    public sealed interface Output {
        long generation();
    }

    /** The mesh of one layer of one section. The render thread uploads it and then closes the buffer. */
    public record Baked(long generation, BlockPos origin, RenderType layer, ByteBufferBuilder buffer, MeshData mesh) implements Output {

        public void free() {
            this.mesh.close();
            this.buffer.close();
        }
    }

    /** The end of one generation. */
    public record Complete(long generation, PreviewDimension dimension, int radius, Holder<Biome> centerBiome) implements Output {
    }

    private final ShaftPainter painter;
    private final ShaftMazes mazes;
    private final BlockRenderDispatcher blockRenderer;
    private final Thread thread;
    private final Object lock = new Object();
    private final Queue<Output> output = new ConcurrentLinkedQueue<>();
    @Nullable
    private Request pending;
    private long pendingSince;
    private long pendingGeneration;
    private volatile long latestGeneration;
    private volatile boolean closed;
    private volatile Progress progress = new Progress(Phase.WAITING, 0, 0);

    public PreviewBuilder(ShaftPainter painter, ShaftMazes mazes, BlockRenderDispatcher blockRenderer) {
        this.painter = painter;
        this.mazes = mazes;
        this.blockRenderer = blockRenderer;
        this.thread = new Thread(this::run, "Aerolite Preview Builder");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Starts a new generation after the debounce. Returns its number. */
    public long request(Request request) {
        synchronized (this.lock) {
            this.pending = request;
            this.pendingSince = System.currentTimeMillis();
            this.pendingGeneration = ++this.latestGeneration;
            this.progress = new Progress(Phase.WAITING, 0, 0);
            this.lock.notifyAll();
            return this.pendingGeneration;
        }
    }

    public long latestGeneration() {
        return this.latestGeneration;
    }

    public Progress progress() {
        return this.progress;
    }

    /** The next message of the worker, or null. */
    @Nullable
    public Output poll() {
        return this.output.poll();
    }

    private boolean stale(long generation) {
        return this.closed || generation != this.latestGeneration;
    }

    private void run() {
        while (!this.closed) {
            Request request;
            long generation;
            synchronized (this.lock) {
                try {
                    while (!this.closed && (this.pending == null || System.currentTimeMillis() < this.pendingSince + DEBOUNCE_MILLIS)) {
                        if (this.pending == null) {
                            this.lock.wait();
                        } else {
                            this.lock.wait(Math.max(1, this.pendingSince + DEBOUNCE_MILLIS - System.currentTimeMillis()));
                        }
                    }
                } catch (InterruptedException exception) {
                    return;
                }
                if (this.closed) {
                    return;
                }
                request = this.pending;
                generation = this.pendingGeneration;
                this.pending = null;
            }
            try {
                this.build(request, generation);
            } catch (Throwable throwable) {
                Aerolite.LOGGER.error("The preview build failed", throwable);
            }
        }
    }

    private void setProgress(long generation, Phase phase, int done, int total) {
        if (!this.stale(generation)) {
            this.progress = new Progress(phase, done, total);
        }
    }

    private void build(Request request, long generation) {
        PreviewDimension dimension = request.dimension();
        int radius = request.radius();
        int size = 2 * radius + 1;
        int height = dimension.maxY() - dimension.minY();
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(dimension.minY(), height);
        RandomState randomState = RandomState.create(dimension.noiseSettings().value(), request.noise(), request.seed());
        Climate.Sampler sampler = randomState.sampler();
        int centerY = dimension.minY() + height / 2;
        Holder<Biome> centerBiome = dimension.biomeSource().getNoiseBiome(QuartPos.fromBlock(8), QuartPos.fromBlock(centerY), QuartPos.fromBlock(8), sampler);

        boolean shafts = !request.settings().variations(dimension.key()).isEmpty();
        ShaftLayout layout = shafts
                ? new ShaftLayout(this.mazes, request.seed(), dimension.key(), dimension.minY(), dimension.maxY(), request.settings())
                : null;
        ProtoChunk[] chunks = new ProtoChunk[size * size];
        for (int chunkX = -radius; chunkX <= radius; chunkX++) {
            for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
                if (this.stale(generation)) {
                    return;
                }
                int index = PreviewBlockGetter.index(chunkX, chunkZ, radius);
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, heightAccessor, request.biomes(), null);
                if (layout != null) {
                    this.painter.paint(layout, chunk);
                }
                chunks[index] = chunk;
                this.setProgress(generation, Phase.PAINTING, index + 1, chunks.length);
            }
        }

        PreviewBlockGetter getter = new PreviewBlockGetter(chunks, radius, dimension.minY(), height, request.constantAmbientLight(), dimension.biomeSource(), sampler);
        int sectionsPerChunk = heightAccessor.getSectionsCount();
        int total = chunks.length * sectionsPerChunk;
        int done = 0;
        for (ProtoChunk chunk : chunks) {
            for (int section = 0; section < sectionsPerChunk; section++) {
                if (this.stale(generation)) {
                    return;
                }
                LevelChunkSection levelSection = chunk.getSection(section);
                if (!levelSection.hasOnlyAir()) {
                    SectionPos pos = SectionPos.of(chunk.getPos(), heightAccessor.getSectionYFromSectionIndex(section));
                    this.bake(generation, pos, getter);
                }
                this.setProgress(generation, Phase.BAKING, ++done, total);
            }
        }
        if (!this.stale(generation)) {
            this.output.add(new Complete(generation, dimension, radius, centerBiome));
            this.setProgress(generation, Phase.DONE, total, total);
        }
    }

    // [VANILLACOPY] SectionCompiler#compile: no fluids, no block entities, no visibility graph, no sorting, one mesh message per layer
    private void bake(long generation, SectionPos sectionPos, PreviewBlockGetter region) {
        BlockPos blockpos = sectionPos.origin();
        BlockPos blockpos1 = blockpos.offset(15, 15, 15);
        PoseStack posestack = new PoseStack();
        ModelBlockRenderer.enableCaching();
        List<RenderType> layers = RenderType.chunkBufferLayers();
        Map<RenderType, BufferBuilder> map = new Reference2ObjectArrayMap<>(layers.size());
        Map<RenderType, ByteBufferBuilder> buffers = new Reference2ObjectArrayMap<>(layers.size());
        RandomSource randomsource = RandomSource.create();
        try {
            for (BlockPos blockpos2 : BlockPos.betweenClosed(blockpos, blockpos1)) {
                BlockState blockstate = region.getBlockState(blockpos2);
                if (blockstate.getRenderShape() == RenderShape.MODEL) {
                    var model = this.blockRenderer.getBlockModel(blockstate);
                    var modelData = model.getModelData(region, blockpos2, blockstate, ModelData.EMPTY);
                    randomsource.setSeed(blockstate.getSeed(blockpos2));
                    for (RenderType rendertype2 : model.getRenderTypes(blockstate, randomsource, modelData)) {
                        if (!layers.contains(rendertype2)) {
                            continue;
                        }
                        BufferBuilder bufferbuilder1 = map.computeIfAbsent(rendertype2, type -> {
                            ByteBufferBuilder buffer = new ByteBufferBuilder(START_BUFFER_SIZE);
                            buffers.put(type, buffer);
                            return new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                        });
                        posestack.pushPose();
                        posestack.translate(
                                (float) SectionPos.sectionRelative(blockpos2.getX()),
                                (float) SectionPos.sectionRelative(blockpos2.getY()),
                                (float) SectionPos.sectionRelative(blockpos2.getZ())
                        );
                        this.blockRenderer.renderBatched(blockstate, blockpos2, region, posestack, bufferbuilder1, true, randomsource, modelData, rendertype2);
                        posestack.popPose();
                    }
                }
            }
        } finally {
            ModelBlockRenderer.clearCache();
        }
        for (Map.Entry<RenderType, BufferBuilder> entry : map.entrySet()) {
            ByteBufferBuilder buffer = buffers.get(entry.getKey());
            MeshData meshdata = entry.getValue().build();
            if (meshdata == null) {
                buffer.close();
            } else if (this.stale(generation)) {
                meshdata.close();
                buffer.close();
            } else {
                this.output.add(new Baked(generation, blockpos.immutable(), entry.getKey(), buffer, meshdata));
            }
        }
    }

    /** Stops the worker and waits for it. Call {@link #poll} afterwards to free the meshes that remain. */
    @Override
    public void close() {
        synchronized (this.lock) {
            this.closed = true;
            this.lock.notifyAll();
        }
        try {
            this.thread.join(JOIN_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

}
