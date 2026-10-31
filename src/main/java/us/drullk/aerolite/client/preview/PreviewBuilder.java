package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Paints the shafts of the preview region and bakes its sections.
 * Each request starts a new generation after a debounce.
 * One paint thread paints the chunks in a spiral from the center chunk outward.
 * A chunk goes to the bake pool when it and its neighbor chunks are painted, so the bake sees every block next to the chunk.
 * The bake pool bakes the sections of a chunk in parallel.
 * The chunks go to the render thread in spiral sequence, each one when it and every earlier chunk are baked.
 * A newer request stops an older generation at the next chunk and drops its pending bakes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
public final class PreviewBuilder implements AutoCloseable {

    public static final long DEBOUNCE_MILLIS = 300;
    private static final int START_BUFFER_SIZE = 16384;
    private static final long JOIN_MILLIS = 2000;
    private static final int SECTION_LONGS = 4096 / Long.SIZE;

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

    /**
     * The progress of the newest generation. The total of sections includes the sections with only air.
     * While the generation paints, {@link #done} and {@link #total} count chunks. Afterwards they count sections.
     */
    public record Progress(Phase phase, int paintedChunks, int totalChunks, int bakedSections, int totalSections) {

        /** A progress that counts one kind of step. */
        public Progress(Phase phase, int done, int total) {
            this(phase, done, total, done, total);
        }

        public int done() {
            return this.phase == Phase.PAINTING ? this.paintedChunks : this.bakedSections;
        }

        public int total() {
            return this.phase == Phase.PAINTING ? this.totalChunks : this.totalSections;
        }

        /** The mean of the painted fraction and the baked fraction. */
        public float fraction() {
            float painted = this.totalChunks <= 0 ? 0 : this.paintedChunks / (float) this.totalChunks;
            float baked = this.totalSections <= 0 ? 0 : this.bakedSections / (float) this.totalSections;
            return (painted + baked) / 2;
        }
    }

    /** The region and backdrop of one generation. */
    public record Region(PreviewDimension dimension, int radius, Holder<Biome> centerBiome) {
    }

    /** A message from the builder to the render thread. */
    public sealed interface Output {
        long generation();

        Region region();
    }

    /** The mesh of one layer of one section. The render thread uploads it and then closes the buffer. */
    public record Baked(BlockPos origin, RenderType layer, ByteBufferBuilder buffer, MeshData mesh) {

        public void free() {
            this.mesh.close();
            this.buffer.close();
        }
    }

    /**
     * One baked chunk.
     *
     * @param spiralIndex the place of the chunk in the spiral, 0 for the center chunk
     * @param solids      for each section index, a bit per block with a model, or null when no block has a model;
     *                    the bit index is {@code y << 8 | z << 4 | x} in section coordinates
     */
    public record ChunkReady(long generation, int spiralIndex, ChunkPos pos, Region region, List<Baked> meshes, long[][] solids) implements Output {

        public void free() {
            for (Baked baked : this.meshes) {
                baked.free();
            }
        }
    }

    /** The end of one generation. Every chunk of the generation arrived before it. */
    public record Complete(long generation, Region region) implements Output {
    }

    private final ShaftPainter painter;
    private final ShaftMazes mazes;
    private final BlockRenderDispatcher blockRenderer;
    private final Thread thread;
    private final ExecutorService bakers;
    private final Object lock = new Object();
    private final Queue<Output> output = new ConcurrentLinkedQueue<>();
    @Nullable
    private Request pending;
    private long pendingSince;
    private long pendingGeneration;
    private volatile long latestGeneration;
    private volatile boolean closed;
    @Nullable
    private volatile Build current;

    public PreviewBuilder(ShaftPainter painter, ShaftMazes mazes, BlockRenderDispatcher blockRenderer) {
        this.painter = painter;
        this.mazes = mazes;
        this.blockRenderer = blockRenderer;
        AtomicInteger bakerCount = new AtomicInteger();
        this.bakers = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), runnable -> {
            Thread baker = new Thread(runnable, "Aerolite Preview Baker " + bakerCount.incrementAndGet());
            baker.setDaemon(true);
            return baker;
        });
        this.thread = new Thread(this::run, "Aerolite Preview Painter");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Starts a new generation after the debounce. Returns its number. */
    public long request(Request request) {
        synchronized (this.lock) {
            this.pending = request;
            this.pendingSince = System.currentTimeMillis();
            this.pendingGeneration = ++this.latestGeneration;
            this.lock.notifyAll();
            return this.pendingGeneration;
        }
    }

    public long latestGeneration() {
        return this.latestGeneration;
    }

    public Progress progress() {
        Build build = this.current;
        if (build == null || build.generation != this.latestGeneration) {
            return new Progress(Phase.WAITING, 0, 0);
        }
        Phase phase = build.complete ? Phase.DONE : build.painted.get() < build.chunkCount ? Phase.PAINTING : Phase.BAKING;
        return new Progress(phase, build.painted.get(), build.chunkCount, build.baked.get(), build.chunkCount * build.sectionsPerChunk);
    }

    /** The next message of the builder, or null. */
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
                this.paint(request, generation);
            } catch (Throwable throwable) {
                Aerolite.LOGGER.error("The preview build failed", throwable);
            }
        }
    }

    /**
     * The chunk offsets of the region in spiral sequence: the center chunk, then each ring outward.
     * Each ring starts at its corner of least X and least Z.
     */
    static ChunkPos[] spiral(int radius) {
        int size = 2 * radius + 1;
        ChunkPos[] spiral = new ChunkPos[size * size];
        int next = 0;
        spiral[next++] = new ChunkPos(0, 0);
        for (int ring = 1; ring <= radius; ring++) {
            for (int x = -ring; x < ring; x++) {
                spiral[next++] = new ChunkPos(x, -ring);
            }
            for (int z = -ring; z < ring; z++) {
                spiral[next++] = new ChunkPos(ring, z);
            }
            for (int x = ring; x > -ring; x--) {
                spiral[next++] = new ChunkPos(x, ring);
            }
            for (int z = ring; z > -ring; z--) {
                spiral[next++] = new ChunkPos(-ring, z);
            }
        }
        return spiral;
    }

    /** Paints the chunks on the paint thread and hands each chunk to the bake pool when its neighbors are painted. */
    private void paint(Request request, long generation) {
        PreviewDimension dimension = request.dimension();
        int radius = request.radius();
        int height = dimension.maxY() - dimension.minY();
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(dimension.minY(), height);
        RandomState randomState = RandomState.create(dimension.noiseSettings().value(), request.noise(), request.seed());
        Climate.Sampler sampler = randomState.sampler();
        int centerY = dimension.minY() + height / 2;
        Holder<Biome> centerBiome = dimension.biomeSource().getNoiseBiome(QuartPos.fromBlock(8), QuartPos.fromBlock(centerY), QuartPos.fromBlock(8), sampler);

        boolean shafts = !request.settings().variations(dimension.key()).isEmpty();
        ShaftLayout layout = shafts
                ? new ShaftLayout(this.mazes, request.seed(), dimension.key(), dimension.minY(), dimension.maxY(), request.settings().dimension(dimension.key()))
                : null;
        ChunkPos[] spiral = spiral(radius);
        ProtoChunk[] chunks = new ProtoChunk[spiral.length];
        PreviewBlockGetter getter = new PreviewBlockGetter(chunks, radius, dimension.minY(), height, request.constantAmbientLight(), dimension.biomeSource(), sampler);
        Build build = new Build(generation, new Region(dimension, radius, centerBiome), spiral, heightAccessor, getter);
        this.current = build;

        IntArrayList[] releases = releases(spiral, radius);
        for (int spiralIndex = 0; spiralIndex < spiral.length; spiralIndex++) {
            if (this.stale(generation)) {
                build.discard();
                return;
            }
            ChunkPos pos = spiral[spiralIndex];
            ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, heightAccessor, request.biomes(), null);
            if (layout != null) {
                this.painter.paint(layout, chunk);
            }
            chunks[PreviewBlockGetter.index(pos.x, pos.z, radius)] = chunk;
            build.painted.incrementAndGet();
            IntArrayList release = releases[spiralIndex];
            if (release != null) {
                for (int i = 0; i < release.size(); i++) {
                    this.submit(build, release.getInt(i), chunks);
                }
            }
        }
    }

    /** For each spiral index, the chunks whose last painted neighbor has that index. */
    private static IntArrayList[] releases(ChunkPos[] spiral, int radius) {
        int[] spiralIndexOf = new int[spiral.length];
        for (int i = 0; i < spiral.length; i++) {
            spiralIndexOf[PreviewBlockGetter.index(spiral[i].x, spiral[i].z, radius)] = i;
        }
        IntArrayList[] releases = new IntArrayList[spiral.length];
        for (int i = 0; i < spiral.length; i++) {
            int last = i;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = spiral[i].x + dx;
                    int z = spiral[i].z + dz;
                    if (Math.abs(x) <= radius && Math.abs(z) <= radius) {
                        last = Math.max(last, spiralIndexOf[PreviewBlockGetter.index(x, z, radius)]);
                    }
                }
            }
            if (releases[last] == null) {
                releases[last] = new IntArrayList(4);
            }
            releases[last].add(i);
        }
        return releases;
    }

    /** Hands the sections of one chunk that hold blocks to the bake pool. */
    private void submit(Build build, int spiralIndex, ProtoChunk[] chunks) {
        ChunkPos pos = build.spiral[spiralIndex];
        ProtoChunk chunk = chunks[PreviewBlockGetter.index(pos.x, pos.z, build.region.radius())];
        IntArrayList sections = new IntArrayList();
        for (int section = 0; section < build.sectionsPerChunk; section++) {
            if (!chunk.getSection(section).hasOnlyAir()) {
                sections.add(section);
            }
        }
        build.baked.addAndGet(build.sectionsPerChunk - sections.size());
        ChunkBake bake = new ChunkBake(spiralIndex, pos, sections.size(), build.sectionsPerChunk);
        if (sections.isEmpty()) {
            build.finish(bake);
            return;
        }
        for (int i = 0; i < sections.size(); i++) {
            int section = sections.getInt(i);
            this.bakers.execute(() -> this.bakeSection(build, bake, section));
        }
    }

    private void bakeSection(Build build, ChunkBake bake, int section) {
        try {
            if (!this.stale(build.generation)) {
                SectionPos pos = SectionPos.of(bake.pos, build.heightAccessor.getSectionYFromSectionIndex(section));
                long[] solids = new long[SECTION_LONGS];
                List<Baked> meshes = this.bake(pos, build.getter, solids);
                boolean any = false;
                for (long bits : solids) {
                    any |= bits != 0;
                }
                synchronized (bake) {
                    bake.meshes.addAll(meshes);
                    bake.solids[section] = any ? solids : null;
                }
            }
        } catch (Throwable throwable) {
            Aerolite.LOGGER.error("The preview bake failed", throwable);
        } finally {
            build.baked.incrementAndGet();
            if (bake.remaining.decrementAndGet() == 0) {
                build.finish(bake);
            }
        }
    }

    // [VANILLACOPY] SectionCompiler#compile: no fluids, no block entities, no visibility graph, no sorting, a solid bit per model block
    private List<Baked> bake(SectionPos sectionPos, PreviewBlockGetter region, long[] solids) {
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
                    int bit = (blockpos2.getY() & 15) << 8 | (blockpos2.getZ() & 15) << 4 | blockpos2.getX() & 15;
                    solids[bit >> 6] |= 1L << (bit & 63);
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
        List<Baked> baked = new ArrayList<>(map.size());
        for (Map.Entry<RenderType, BufferBuilder> entry : map.entrySet()) {
            ByteBufferBuilder buffer = buffers.get(entry.getKey());
            MeshData meshdata = entry.getValue().build();
            if (meshdata == null) {
                buffer.close();
            } else {
                baked.add(new Baked(blockpos.immutable(), entry.getKey(), buffer, meshdata));
            }
        }
        return baked;
    }

    /** The bake state of one chunk. Its meshes and solids change under its own lock. */
    private static final class ChunkBake {

        final int spiralIndex;
        final ChunkPos pos;
        final AtomicInteger remaining;
        final List<Baked> meshes = new ArrayList<>();
        final long[][] solids;

        ChunkBake(int spiralIndex, ChunkPos pos, int sections, int sectionsPerChunk) {
            this.spiralIndex = spiralIndex;
            this.pos = pos;
            this.remaining = new AtomicInteger(sections);
            this.solids = new long[sectionsPerChunk][];
        }
    }

    /** The state of one generation. Baked chunks wait here until every earlier chunk of the spiral is out. */
    private final class Build {

        final long generation;
        final Region region;
        final ChunkPos[] spiral;
        final LevelHeightAccessor heightAccessor;
        final PreviewBlockGetter getter;
        final int chunkCount;
        final int sectionsPerChunk;
        final AtomicInteger painted = new AtomicInteger();
        final AtomicInteger baked = new AtomicInteger();
        private final ChunkReady[] waiting;
        private int next;
        private boolean discarded;
        volatile boolean complete;

        Build(long generation, Region region, ChunkPos[] spiral, LevelHeightAccessor heightAccessor, PreviewBlockGetter getter) {
            this.generation = generation;
            this.region = region;
            this.spiral = spiral;
            this.heightAccessor = heightAccessor;
            this.getter = getter;
            this.chunkCount = spiral.length;
            this.sectionsPerChunk = heightAccessor.getSectionsCount();
            this.waiting = new ChunkReady[spiral.length];
        }

        /** Sends the chunk and every waiting chunk after it while the spiral has no gap. */
        synchronized void finish(ChunkBake bake) {
            ChunkReady ready = new ChunkReady(this.generation, bake.spiralIndex, bake.pos, this.region, List.copyOf(bake.meshes), bake.solids);
            if (this.discarded || PreviewBuilder.this.stale(this.generation)) {
                ready.free();
                this.discard();
                return;
            }
            this.waiting[bake.spiralIndex] = ready;
            while (this.next < this.waiting.length && this.waiting[this.next] != null) {
                PreviewBuilder.this.output.add(this.waiting[this.next]);
                this.waiting[this.next] = null;
                this.next++;
            }
            if (this.next == this.waiting.length) {
                PreviewBuilder.this.output.add(new Complete(this.generation, this.region));
                this.complete = true;
            }
        }

        /** Frees the waiting chunks. Later chunks of this generation are freed on arrival. */
        synchronized void discard() {
            this.discarded = true;
            for (int i = this.next; i < this.waiting.length; i++) {
                if (this.waiting[i] != null) {
                    this.waiting[i].free();
                    this.waiting[i] = null;
                }
            }
        }
    }

    /** Stops the paint thread and the bake pool and waits for them. Call {@link #poll} afterwards to free the meshes that remain. */
    @Override
    public void close() {
        synchronized (this.lock) {
            this.closed = true;
            this.lock.notifyAll();
        }
        try {
            this.thread.join(JOIN_MILLIS);
            // The queued bakes see the closed flag, so each one ends at once.
            this.bakers.shutdown();
            this.bakers.awaitTermination(JOIN_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

}
