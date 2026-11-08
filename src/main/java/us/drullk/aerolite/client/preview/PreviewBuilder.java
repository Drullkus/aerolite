package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.client.model.data.ModelData;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.island.IslandGenerators;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.IslandTerrain;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Paints the shafts of the preview region and bakes its sections.
 * A request with new settings, a new seed, a new dimension or a new radius starts a new scene after a debounce.
 * A request that only moves the region center shifts the scene at once.
 * A shift keeps the painted and baked chunks that stay inside the region. It paints and bakes only the chunks that enter it.
 * The painted chunks hold one ring of margin around the region. The margin is not baked.
 * One paint thread paints the chunks in a spiral from the center chunk outward.
 * A chunk goes to the bake pool when it and its neighbor chunks are painted, so the bake sees every block next to the chunk.
 * With the margin, that holds at the region edge too. So the bake of a chunk does not depend on the region position.
 * The bake pool bakes the sections of a chunk in parallel.
 * The chunks go to the render thread in spiral sequence, each one when it and every earlier chunk are baked.
 * A newer request stops an older generation at the next chunk and drops its pending bakes.
 * A request with island settings fills each chunk with the island terrain of the dimension before the pillars.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public final class PreviewBuilder implements AutoCloseable {

    public static final long DEBOUNCE_MILLIS = 300;
    private static final int START_BUFFER_SIZE = 16384;
    private static final long JOIN_MILLIS = 2000;
    private static final int SECTION_LONGS = 4096 / Long.SIZE;
    /** The ring of painted chunks around the region. The bake of an edge chunk reads its neighbors in it. */
    private static final int MARGIN = 1;

    /**
     * The input of one generation.
     *
     * @param center  the chunk that holds the region center
     * @param islands the island settings of the terrain, or null for a preview without terrain
     */
    public record Request(AeroliteSettings settings, long seed, PreviewDimension dimension, int radius, boolean constantAmbientLight,
                          Registry<Biome> biomes, HolderGetter<NormalNoise.NoiseParameters> noise, ChunkPos center, @Nullable IslandSettings islands) {

        /** True when the two requests differ in the center at most. */
        public boolean sameScene(Request other) {
            return this.settings.equals(other.settings) && this.seed == other.seed && this.dimension.equals(other.dimension)
                    && this.radius == other.radius && this.constantAmbientLight == other.constantAmbientLight
                    && Objects.equals(this.islands, other.islands);
        }
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

        /** The mean of the painted fraction and the baked fraction. A step with nothing to do counts as finished. */
        public float fraction() {
            if (this.phase == Phase.WAITING) {
                return 0;
            }
            float painted = this.totalChunks <= 0 ? 1 : this.paintedChunks / (float) this.totalChunks;
            float baked = this.totalSections <= 0 ? 1 : this.bakedSections / (float) this.totalSections;
            return (painted + baked) / 2;
        }
    }

    /**
     * The region and backdrop of one generation.
     *
     * @param scene  the number of the scene; a shift keeps it, and every other change gives a larger one
     * @param center the chunk that holds the region center
     */
    public record Region(long scene, PreviewDimension dimension, int radius, ChunkPos center, Holder<Biome> centerBiome) {
    }

    /** A message from the builder to the render thread. The messages of one scene keep their sequence. */
    public sealed interface Output {
        long generation();

        Region region();
    }

    /** The start of one generation. Within its scene, the render thread drops the chunks outside the new region. */
    public record Started(long generation, Region region) implements Output {
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
     * @param sequenceIndex the place of the chunk in the bake sequence of its generation, 0 for the chunk nearest the center
     * @param pos           the world position of the chunk
     * @param solids        for each section index, a bit per block with a model, or null when no block has a model;
     *                      the bit index is {@code y << 8 | z << 4 | x} in section coordinates
     */
    public record ChunkReady(long generation, int sequenceIndex, ChunkPos pos, Region region, List<Baked> meshes, long[][] solids) implements Output {

        public void free() {
            for (Baked baked : this.meshes) {
                baked.free();
            }
        }
    }

    /** The end of one generation. Every chunk of the generation arrived before it. */
    public record Complete(long generation, Region region) implements Output {
    }

    /** The painted chunks of one scene. Only the paint thread uses it. */
    private static final class Scene {

        final Request request;
        final long number;
        final LevelHeightAccessor heightAccessor;
        final PreviewBiomes biomes;
        @Nullable
        final ShaftLayout layout;
        /** The island terrain of the dimension, or null for a preview without terrain or a dimension without an island density function. */
        @Nullable
        final IslandTerrain terrain;
        final Long2ObjectMap<ProtoChunk> painted = new Long2ObjectOpenHashMap<>();

        Scene(Request request, long number, ShaftMazes mazes, IslandGenerators islands) {
            this.request = request;
            this.number = number;
            PreviewDimension dimension = request.dimension();
            this.heightAccessor = LevelHeightAccessor.create(dimension.minY(), dimension.maxY() - dimension.minY());
            NoiseGeneratorSettings generatorSettings = dimension.noiseSettings().value();
            boolean terrain = request.islands() != null && islands.islands(generatorSettings).isPresent();
            if (terrain) {
                generatorSettings = islands.withSettings(generatorSettings, request.islands());
            }
            RandomState randomState = RandomState.create(generatorSettings, request.noise(), request.seed());
            this.biomes = new PreviewBiomes(dimension.biomeSource(), randomState.sampler(), request.biomes());
            this.terrain = terrain
                    ? new IslandTerrain(generatorSettings, randomState, dimension.biomeSource(), this.biomes::at, request.biomes(), request.seed())
                    : null;
            this.layout = request.settings().variations(dimension.key()).isEmpty()
                    ? null
                    : new ShaftLayout(mazes, request.seed(), dimension.key(), dimension.minY(), dimension.maxY(), request.settings().dimension(dimension.key()));
        }
    }

    private final ShaftPainter painter;
    private final ShaftMazes mazes;
    private final IslandGenerators islands;
    private final BlockRenderDispatcher blockRenderer;
    private final Thread thread;
    private final ExecutorService bakers;
    /** Guards the request state, the delivered chunks and the output of each build. */
    private final Object lock = new Object();
    private final Queue<Output> output = new ConcurrentLinkedQueue<>();
    @Nullable
    private Request pending;
    private long pendingReadyAt;
    private long pendingGeneration;
    @Nullable
    private Request lastRequest;
    /** The chunks of the current scene that went to the output queue. */
    private final LongSet delivered = new LongOpenHashSet();
    private volatile long latestGeneration;
    private volatile boolean closed;
    @Nullable
    private volatile Build current;
    @Nullable
    private Scene scene;
    private long sceneCount;

    public PreviewBuilder(ShaftPainter painter, ShaftMazes mazes, IslandGenerators islands, BlockRenderDispatcher blockRenderer) {
        this.painter = painter;
        this.mazes = mazes;
        this.islands = islands;
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

    /**
     * Starts a new generation. Returns its number.
     * A change of the scene waits for the debounce. A move of the center alone starts at once.
     * A shift that replaces a waiting scene change keeps the wait of that change.
     */
    public long request(Request request) {
        synchronized (this.lock) {
            long now = System.currentTimeMillis();
            if (this.lastRequest == null || !this.lastRequest.sameScene(request)) {
                this.pendingReadyAt = now + DEBOUNCE_MILLIS;
            } else if (this.pending == null) {
                this.pendingReadyAt = now;
            }
            this.pending = request;
            this.lastRequest = request;
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
        Phase phase = build.complete ? Phase.DONE : build.painted.get() < build.paintCount ? Phase.PAINTING : Phase.BAKING;
        return new Progress(phase, build.painted.get(), build.paintCount, build.baked.get(), build.chunkCount * build.sectionsPerChunk);
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
                    while (!this.closed && (this.pending == null || System.currentTimeMillis() < this.pendingReadyAt)) {
                        if (this.pending == null) {
                            this.lock.wait();
                        } else {
                            this.lock.wait(Math.max(1, this.pendingReadyAt - System.currentTimeMillis()));
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

    /** The scene of the request. A request of another scene drops the painted and delivered chunks. */
    private Scene scene(Request request) {
        Scene scene = this.scene;
        if (scene == null || !scene.request.sameScene(request)) {
            scene = new Scene(request, ++this.sceneCount, this.mazes, this.islands);
            this.scene = scene;
            synchronized (this.lock) {
                this.delivered.clear();
            }
        }
        return scene;
    }

    /**
     * Paints the chunks that the region lacks on the paint thread.
     * It hands each chunk that the render thread lacks to the bake pool when its neighbors are painted.
     */
    private void paint(Request request, long generation) {
        PreviewDimension dimension = request.dimension();
        int radius = request.radius();
        int paintRadius = radius + MARGIN;
        ChunkPos center = request.center();
        Scene scene = this.scene(request);
        scene.painted.keySet().removeIf((long key) -> !RegionChunks.contains(center, paintRadius, ChunkPos.getX(key), ChunkPos.getZ(key)));
        int centerY = dimension.minY() + scene.heightAccessor.getHeight() / 2;
        Holder<Biome> centerBiome = scene.biomes.at(QuartPos.fromBlock(center.getMiddleBlockX()), QuartPos.fromBlock(centerY),
                QuartPos.fromBlock(center.getMiddleBlockZ()));
        Region region = new Region(scene.number, dimension, radius, center, centerBiome);

        List<ChunkPos> bakes;
        synchronized (this.lock) {
            if (this.stale(generation)) {
                return;
            }
            this.delivered.removeIf((long key) -> !RegionChunks.contains(center, radius, ChunkPos.getX(key), ChunkPos.getZ(key)));
            bakes = RegionChunks.missing(center, radius, this.delivered);
            this.output.add(new Started(generation, region));
        }
        List<ChunkPos> paints = RegionChunks.missing(center, paintRadius, scene.painted.keySet());

        int size = 2 * paintRadius + 1;
        ProtoChunk[] chunks = new ProtoChunk[size * size];
        for (Long2ObjectMap.Entry<ProtoChunk> entry : scene.painted.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            chunks[RegionChunks.index(center, paintRadius, ChunkPos.getX(key), ChunkPos.getZ(key))] = entry.getValue();
        }
        PreviewBlockGetter getter = new PreviewBlockGetter(chunks, center, paintRadius, dimension.minY(), scene.heightAccessor.getHeight(),
                request.constantAmbientLight(), scene.biomes);
        Build build = new Build(generation, region, bakes, paints.size(), scene.heightAccessor, getter, chunks, paintRadius);
        this.current = build;
        if (bakes.isEmpty()) {
            build.completeEmpty();
            return;
        }

        IntArrayList[] releases = releases(bakes, paints, center, paintRadius);
        IntArrayList ready = releases[paints.size()];
        for (int i = 0; i < ready.size(); i++) {
            this.submit(build, ready.getInt(i));
        }
        for (int paintIndex = 0; paintIndex < paints.size(); paintIndex++) {
            if (this.stale(generation)) {
                build.discard();
                return;
            }
            ChunkPos pos = paints.get(paintIndex);
            ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, scene.heightAccessor, request.biomes(), null);
            if (scene.terrain != null) {
                scene.terrain.generate(chunk);
            }
            if (scene.layout != null) {
                this.painter.paint(scene.layout, chunk);
            }
            chunks[RegionChunks.index(center, paintRadius, pos.x, pos.z)] = chunk;
            scene.painted.put(pos.toLong(), chunk);
            build.painted.incrementAndGet();
            IntArrayList release = releases[paintIndex];
            for (int i = 0; i < release.size(); i++) {
                this.submit(build, release.getInt(i));
            }
        }
    }

    /**
     * For each paint index, the bake indices whose last painted neighbor has that index.
     * The last entry holds the bakes whose neighbors are all painted already.
     *
     * @param paintRadius the radius of the painted chunks, the region radius plus the margin
     */
    private static IntArrayList[] releases(List<ChunkPos> bakes, List<ChunkPos> paints, ChunkPos center, int paintRadius) {
        Long2IntMap paintIndexOf = new Long2IntOpenHashMap();
        paintIndexOf.defaultReturnValue(-1);
        for (int i = 0; i < paints.size(); i++) {
            paintIndexOf.put(paints.get(i).toLong(), i);
        }
        IntArrayList[] releases = new IntArrayList[paints.size() + 1];
        for (int i = 0; i < releases.length; i++) {
            releases[i] = new IntArrayList(4);
        }
        for (int i = 0; i < bakes.size(); i++) {
            ChunkPos pos = bakes.get(i);
            int last = -1;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (RegionChunks.contains(center, paintRadius, pos.x + dx, pos.z + dz)) {
                        last = Math.max(last, paintIndexOf.get(ChunkPos.asLong(pos.x + dx, pos.z + dz)));
                    }
                }
            }
            releases[last < 0 ? paints.size() : last].add(i);
        }
        return releases;
    }

    /** Hands the sections of one chunk that hold blocks to the bake pool. */
    private void submit(Build build, int sequenceIndex) {
        ChunkPos pos = build.bakes.get(sequenceIndex);
        ProtoChunk chunk = build.chunks[RegionChunks.index(build.region.center(), build.paintRadius, pos.x, pos.z)];
        IntArrayList sections = new IntArrayList();
        for (int section = 0; section < build.sectionsPerChunk; section++) {
            if (!chunk.getSection(section).hasOnlyAir()) {
                sections.add(section);
            }
        }
        build.baked.addAndGet(build.sectionsPerChunk - sections.size());
        ChunkBake bake = new ChunkBake(sequenceIndex, pos, sections.size(), build.sectionsPerChunk);
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

    // [VANILLACOPY] SectionCompiler#compile: no fluids, no block entities, no visibility graph, no sorting, a solid bit per model block, then the hidden faces
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
        // The cache keys on the position only, and the hidden faces read the next chunk as air. So they bake with a fresh cache.
        ModelBlockRenderer.enableCaching();
        try {
            HiddenFaces.bake(this.blockRenderer, sectionPos, region, posestack, randomsource, type -> map.computeIfAbsent(type, layer -> {
                ByteBufferBuilder buffer = new ByteBufferBuilder(START_BUFFER_SIZE);
                buffers.put(layer, buffer);
                return new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            }));
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

        final int sequenceIndex;
        final ChunkPos pos;
        final AtomicInteger remaining;
        final List<Baked> meshes = new ArrayList<>();
        final long[][] solids;

        ChunkBake(int sequenceIndex, ChunkPos pos, int sections, int sectionsPerChunk) {
            this.sequenceIndex = sequenceIndex;
            this.pos = pos;
            this.remaining = new AtomicInteger(sections);
            this.solids = new long[sectionsPerChunk][];
        }
    }

    /**
     * The state of one generation. Baked chunks wait here until every earlier chunk of the bake sequence is out.
     * The output state changes under the builder lock, so a newer generation sees each delivered chunk.
     */
    private final class Build {

        final long generation;
        final Region region;
        final List<ChunkPos> bakes;
        final int paintCount;
        final LevelHeightAccessor heightAccessor;
        final PreviewBlockGetter getter;
        /** The painted chunks, indexed by {@link RegionChunks#index} with {@link #paintRadius}. */
        final ProtoChunk[] chunks;
        final int paintRadius;
        final int chunkCount;
        final int sectionsPerChunk;
        final AtomicInteger painted = new AtomicInteger();
        final AtomicInteger baked = new AtomicInteger();
        private final ChunkReady[] waiting;
        private int next;
        private boolean discarded;
        volatile boolean complete;

        Build(long generation, Region region, List<ChunkPos> bakes, int paintCount, LevelHeightAccessor heightAccessor,
              PreviewBlockGetter getter, ProtoChunk[] chunks, int paintRadius) {
            this.generation = generation;
            this.region = region;
            this.bakes = bakes;
            this.paintCount = paintCount;
            this.heightAccessor = heightAccessor;
            this.getter = getter;
            this.chunks = chunks;
            this.paintRadius = paintRadius;
            this.chunkCount = bakes.size();
            this.sectionsPerChunk = heightAccessor.getSectionsCount();
            this.waiting = new ChunkReady[bakes.size()];
        }

        /** Sends the chunk and every waiting chunk after it while the sequence has no gap. */
        void finish(ChunkBake bake) {
            ChunkReady ready = new ChunkReady(this.generation, bake.sequenceIndex, bake.pos, this.region, List.copyOf(bake.meshes), bake.solids);
            synchronized (PreviewBuilder.this.lock) {
                if (this.discarded || PreviewBuilder.this.stale(this.generation)) {
                    ready.free();
                    this.discard();
                    return;
                }
                this.waiting[bake.sequenceIndex] = ready;
                while (this.next < this.waiting.length && this.waiting[this.next] != null) {
                    PreviewBuilder.this.output.add(this.waiting[this.next]);
                    PreviewBuilder.this.delivered.add(this.waiting[this.next].pos().toLong());
                    this.waiting[this.next] = null;
                    this.next++;
                }
                if (this.next == this.waiting.length) {
                    PreviewBuilder.this.output.add(new Complete(this.generation, this.region));
                    this.complete = true;
                }
            }
        }

        /** Ends a generation that bakes no chunk. */
        void completeEmpty() {
            synchronized (PreviewBuilder.this.lock) {
                if (!PreviewBuilder.this.stale(this.generation)) {
                    PreviewBuilder.this.output.add(new Complete(this.generation, this.region));
                    this.complete = true;
                }
            }
        }

        /** Frees the waiting chunks. Later chunks of this generation are freed on arrival. */
        void discard() {
            synchronized (PreviewBuilder.this.lock) {
                this.discarded = true;
                for (int i = this.next; i < this.waiting.length; i++) {
                    if (this.waiting[i] != null) {
                        this.waiting[i].free();
                        this.waiting[i] = null;
                    }
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
