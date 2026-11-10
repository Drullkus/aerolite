package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.island.CellGeometry;
import us.drullk.aerolite.world.island.VoronoiLattice;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws the baked sections of the preview, with the sky, a floor grid and the region outline.
 * Each baked chunk shows as soon as it arrives. The first chunk of a new scene drops the drawn chunks.
 * The start of a shift drops the drawn chunks outside the new region and keeps the rest.
 * The model space has the region center of the pan at its origin. Each mesh keeps its world origin.
 * An outline edge draws translucent when a pixel of it is in front of a block. Else it draws opaque.
 * The islands preview also draws the cell polyhedra of its region: edges, gridlines and a cross at each site.
 * A first pass draws the parts behind blocks at a reduced opacity. A second pass draws the parts in front at full opacity.
 * The hovered cell draws in gold after the other cells, so it covers the faces it shares with them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
final class PreviewRenderer {

    private static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final ResourceLocation LIGHTMAP = ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "preview_lightmap");
    private static final int LIGHTMAP_SIZE = 16;
    private static final int GRID_COLOR = 0x80A0A0A0;
    private static final int OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int OUTLINE_COVER_COLOR = 0x40FFFFFF;
    private static final float SKY_NEAR = 1;
    private static final float SKY_FAR = 1000;
    private static final int EDGES = 12;
    private static final int MAX_EDGE_SAMPLES = 4096;
    private static final double RAY_START = 1.0E-3;
    /** The red, green and blue of a cell, and of the hovered cell in the chat color gold. */
    private static final int CELL_RGB = 0xFFFFFF;
    private static final int HOVERED_RGB = 0xFFAA00;
    /** The opacity of a cell line part that a block hides. */
    private static final float CELL_COVERED_ALPHA = 0.3F;
    /** The opacity of a gridline in front of the blocks. */
    private static final float GRIDLINE_ALPHA = 0.35F;
    /** The half length of each arm of the site cross, in blocks. */
    private static final float SITE_HALF = 3;

    private record SectionBuffer(BlockPos origin, RenderType layer, VertexBuffer buffer) {
    }

    /** The region and backdrop of the drawn generation. */
    private record Scene(PreviewDimension dimension, int radius, ChunkPos center, Holder<Biome> centerBiome) {

        float centerY() {
            return (this.dimension.minY() + this.dimension.maxY()) / 2F;
        }

        /** The least world X of the region. */
        int minX() {
            return (this.center.x - this.radius) * 16;
        }

        /** The least world Z of the region. */
        int minZ() {
            return (this.center.z - this.radius) * 16;
        }

        int size() {
            return 2 * this.radius + 1;
        }

        int sections() {
            return (this.dimension.maxY() - this.dimension.minY()) >> 4;
        }
    }

    /** The drawn meshes by world chunk position. */
    private final Long2ObjectMap<List<SectionBuffer>> meshes = new Long2ObjectOpenHashMap<>();
    /** The solid bits of the drawn chunks by world chunk position, and then by section index. */
    private final Long2ObjectMap<long[][]> chunkSolids = new Long2ObjectOpenHashMap<>();
    private long activeScene = -1;
    @Nullable
    private Scene scene;
    /** The solid bits of each section of the drawn chunks, by chunk grid index of the region and then section index. */
    private long[][] solids = new long[0][];
    private int solidsVersion;
    /** The world X and Z of the model space origin. */
    private double originX;
    private double originZ;
    private final boolean[] coverEdges = new boolean[EDGES];
    private final Matrix4f coverView = new Matrix4f().zero();
    private final Matrix4f coverProjection = new Matrix4f().zero();
    private int coverVersion = -1;
    private double coverOriginX = Double.NaN;
    private double coverOriginZ = Double.NaN;
    private int coverWidth;
    private int coverHeight;
    private final DynamicTexture lightmap;
    /** The cell polyhedra of the drawn region. */
    private List<CellGeometry.Shape> cells = List.of();

    PreviewRenderer() {
        this.lightmap = new DynamicTexture(LIGHTMAP_SIZE, LIGHTMAP_SIZE, false);
        this.lightmap.getPixels().fillRect(0, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, -1);
        this.lightmap.upload();
        Minecraft.getInstance().getTextureManager().register(LIGHTMAP, this.lightmap);
    }

    /** The count of chunks that the renderer draws. */
    int drawnChunks() {
        return this.meshes.size();
    }

    /**
     * Uploads each baked chunk of the builder and follows each shift.
     * The first chunk of a newer scene replaces the drawn chunks. The messages of an older scene are freed.
     */
    void drain(PreviewBuilder builder) {
        PreviewBuilder.Output output;
        while ((output = builder.poll()) != null) {
            PreviewBuilder.Region region = output.region();
            if (region.scene() < this.activeScene) {
                if (output instanceof PreviewBuilder.ChunkReady ready) {
                    ready.free();
                }
                continue;
            }
            if (region.scene() > this.activeScene) {
                if (output instanceof PreviewBuilder.Started) {
                    // The drawn scene stays until the new scene has a chunk.
                    continue;
                }
                this.clear();
                this.activeScene = region.scene();
            }
            this.follow(region);
            switch (output) {
                case PreviewBuilder.ChunkReady ready -> this.upload(ready);
                case PreviewBuilder.Started started -> {
                    // The follow above dropped the chunks outside the new region.
                }
                case PreviewBuilder.Complete complete -> {
                    // Every chunk of the generation arrived before this message.
                }
            }
        }
    }

    /** The cell polyhedra of the drawn region. */
    List<CellGeometry.Shape> cells() {
        return this.cells;
    }

    /** True when the drawn chunk holds a block with a model. */
    boolean drawsBlocks(int chunkX, int chunkZ) {
        long[][] chunk = this.chunkSolids.get(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return false;
        }
        for (long[] section : chunk) {
            if (section != null) {
                return true;
            }
        }
        return false;
    }

    /** True when the renderer holds the chunk of the drawn region. */
    boolean drawsChunk(int chunkX, int chunkZ) {
        return this.chunkSolids.containsKey(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Takes the region of a message of the drawn scene. A new center drops the chunks outside the region. */
    private void follow(PreviewBuilder.Region region) {
        this.cells = region.cells();
        Scene old = this.scene;
        Scene scene = new Scene(region.dimension(), region.radius(), region.center(), region.centerBiome());
        this.scene = scene;
        if (old != null && old.center().equals(scene.center()) && old.radius() == scene.radius() && old.dimension().equals(scene.dimension())) {
            return;
        }
        var iterator = this.meshes.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<List<SectionBuffer>> entry = iterator.next();
            long key = entry.getLongKey();
            if (!RegionChunks.contains(scene.center(), scene.radius(), ChunkPos.getX(key), ChunkPos.getZ(key))) {
                free(entry.getValue());
                iterator.remove();
                this.chunkSolids.remove(key);
            }
        }
        this.solids = new long[scene.size() * scene.size() * scene.sections()][];
        for (Long2ObjectMap.Entry<long[][]> entry : this.chunkSolids.long2ObjectEntrySet()) {
            this.writeSolids(scene, ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), entry.getValue());
        }
        this.solidsVersion++;
    }

    private void writeSolids(Scene scene, int chunkX, int chunkZ, long[][] chunkSolids) {
        int sections = scene.sections();
        int base = RegionChunks.index(scene.center(), scene.radius(), chunkX, chunkZ) * sections;
        for (int section = 0; section < Math.min(sections, chunkSolids.length); section++) {
            this.solids[base + section] = chunkSolids[section];
        }
    }

    private void upload(PreviewBuilder.ChunkReady ready) {
        List<SectionBuffer> buffers = new ArrayList<>(ready.meshes().size());
        for (PreviewBuilder.Baked baked : ready.meshes()) {
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(baked.mesh());
            VertexBuffer.unbind();
            baked.buffer().close();
            buffers.add(new SectionBuffer(baked.origin(), baked.layer(), buffer));
        }
        long key = ready.pos().toLong();
        List<SectionBuffer> replaced = this.meshes.put(key, buffers);
        if (replaced != null) {
            free(replaced);
        }
        this.chunkSolids.put(key, ready.solids());
        Scene scene = this.scene;
        if (scene != null && RegionChunks.contains(scene.center(), scene.radius(), ready.pos().x, ready.pos().z)) {
            this.writeSolids(scene, ready.pos().x, ready.pos().z, ready.solids());
            this.solidsVersion++;
        }
    }

    /** Frees every drawn chunk. */
    private void clear() {
        for (List<SectionBuffer> buffers : this.meshes.values()) {
            free(buffers);
        }
        this.meshes.clear();
        this.chunkSolids.clear();
        this.scene = null;
        this.cells = List.of();
    }

    private static void free(List<SectionBuffer> buffers) {
        for (SectionBuffer buffer : buffers) {
            buffer.buffer().close();
        }
        buffers.clear();
    }

    /**
     * Draws the scene into the bound main render target and clears the depth for the GUI.
     *
     * @param originX the world X of the model space origin, which the camera orbits
     * @param originZ the world Z of the model space origin
     * @param hovered the cell under the cursor, or null
     */
    void render(OrbitCamera camera, double originX, double originZ, @Nullable VoronoiLattice.Cell hovered) {
        this.originX = originX;
        this.originZ = originZ;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        Scene scene = this.scene;
        if (scene == null) {
            RenderSystem.clearColor(0.05F, 0.05F, 0.08F, 1);
            RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT | GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            return;
        }
        DimensionSpecialEffects effects = DimensionSpecialEffects.forType(scene.dimension().type());
        Biome biome = scene.centerBiome().value();
        Vec3 clear = Vec3.fromRGB24(effects.skyType() == DimensionSpecialEffects.SkyType.NORMAL ? biome.getSkyColor() : biome.getFogColor());
        RenderSystem.clearColor((float) clear.x, (float) clear.y, (float) clear.z, 1);
        RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT | GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        float aspect = target.width / (float) Math.max(1, target.height);
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        Matrix4f view = camera.view();

        if (effects.skyType() == DimensionSpecialEffects.SkyType.END) {
            RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(OrbitCamera.FOV_DEGREES * Mth.DEG_TO_RAD, aspect, SKY_NEAR, SKY_FAR), VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(new Matrix4f(view).setTranslation(0, 0, 0));
            renderEndSky(poseStack);
        }

        Matrix4f projection = camera.projection(aspect);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        modelView.set(view);
        RenderSystem.applyModelViewMatrix();
        // A fog that a world left in the render system must not tint the blocks.
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);

        for (RenderType layer : RenderType.chunkBufferLayers()) {
            this.renderLayer(layer, scene, view, projection);
        }
        this.renderCells(scene, hovered);
        this.updateCoverEdges(scene, camera.position(), view, projection, target.width, target.height);
        this.renderLines(scene);

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    // [VANILLACOPY] LevelRenderer#renderSectionLayer: the section map of the preview, a white lightmap, offsets from the pan center
    private void renderLayer(RenderType renderType, Scene scene, Matrix4f frustrumMatrix, Matrix4f projectionMatrix) {
        boolean any = false;
        for (List<SectionBuffer> buffers : this.meshes.values()) {
            for (SectionBuffer section : buffers) {
                any |= section.layer() == renderType;
            }
        }
        if (!any) {
            return;
        }
        renderType.setupRenderState();
        // No level exists, so no lightmap holds light. Every vertex has full light.
        RenderSystem.setShaderTexture(2, LIGHTMAP);
        ShaderInstance shaderinstance = RenderSystem.getShader();
        if (shaderinstance == null) {
            renderType.clearRenderState();
            return;
        }
        shaderinstance.setDefaultUniforms(VertexFormat.Mode.QUADS, frustrumMatrix, projectionMatrix, Minecraft.getInstance().getWindow());
        shaderinstance.apply();
        Uniform uniform = shaderinstance.CHUNK_OFFSET;
        for (List<SectionBuffer> buffers : this.meshes.values()) {
            for (SectionBuffer section : buffers) {
                if (section.layer() != renderType) {
                    continue;
                }
                BlockPos blockpos = section.origin();
                if (uniform != null) {
                    uniform.set((float) (blockpos.getX() - this.originX), blockpos.getY() - scene.centerY(), (float) (blockpos.getZ() - this.originZ));
                    uniform.upload();
                }
                section.buffer().bind();
                section.buffer().draw();
            }
        }
        if (uniform != null) {
            uniform.set(0.0F, 0.0F, 0.0F);
        }
        shaderinstance.clear();
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    /** One line per chunk border on the bottom build limit, and the twelve edges of the region box. */
    private void renderLines(Scene scene) {
        float minX = (float) (scene.minX() - this.originX);
        float minZ = (float) (scene.minZ() - this.originZ);
        float width = scene.size() * 16;
        float bottom = scene.dimension().minY() - scene.centerY();
        BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (int line = 0; line <= scene.size(); line++) {
            float edge = line * 16;
            line(lines, minX + edge, bottom, minZ, minX + edge, bottom, minZ + width, GRID_COLOR);
            line(lines, minX, bottom, minZ + edge, minX + width, bottom, minZ + edge, GRID_COLOR);
        }
        float[][] edges = this.edges(scene);
        for (int i = 0; i < EDGES; i++) {
            float[] edge = edges[i];
            line(lines, edge[0], edge[1], edge[2], edge[3], edge[4], edge[5], this.coverEdges[i] ? OUTLINE_COVER_COLOR : OUTLINE_COLOR);
        }
        MeshData mesh = lines.build();
        if (mesh != null) {
            RenderType.lines().draw(mesh);
        }
    }

    /** Draws the cell lines behind the blocks at the reduced opacity, then the cell lines in front of them. */
    private void renderCells(Scene scene, @Nullable VoronoiLattice.Cell hovered) {
        if (this.cells.isEmpty()) {
            return;
        }
        RenderType lines = RenderType.lines();
        MeshData covered = this.cellLines(scene, hovered, CELL_COVERED_ALPHA);
        if (covered != null) {
            lines.setupRenderState();
            RenderSystem.depthFunc(GL11.GL_GREATER);
            RenderSystem.depthMask(false);
            BufferUploader.drawWithShader(covered);
            lines.clearRenderState();
        }
        MeshData front = this.cellLines(scene, hovered, 1);
        if (front != null) {
            lines.draw(front);
        }
    }

    /**
     * The lines of every cell at the opacity, the hovered cell last.
     * A face that two drawn cells share draws its gridlines once: the hovered cell draws them, else the cell with the lesser lattice key.
     */
    @Nullable
    private MeshData cellLines(Scene scene, @Nullable VoronoiLattice.Cell hovered, float alpha) {
        LongSet drawn = new LongOpenHashSet();
        for (CellGeometry.Shape shape : this.cells) {
            drawn.add(ChunkPos.asLong(shape.cell().ix(), shape.cell().iz()));
        }
        int alphaBits = Mth.clamp(Math.round(alpha * 255), 0, 255) << 24;
        int gridBits = Mth.clamp(Math.round(alpha * GRIDLINE_ALPHA * 255), 0, 255) << 24;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CellGeometry.Shape hoveredShape = null;
        for (CellGeometry.Shape shape : this.cells) {
            if (shape.cell().equals(hovered)) {
                hoveredShape = shape;
            } else {
                this.cellLines(buffer, scene, shape, hovered, drawn, alphaBits | CELL_RGB, gridBits | CELL_RGB);
            }
        }
        if (hoveredShape != null) {
            this.cellLines(buffer, scene, hoveredShape, hovered, drawn, alphaBits | HOVERED_RGB, gridBits | HOVERED_RGB);
        }
        return buffer.build();
    }

    private void cellLines(BufferBuilder buffer, Scene scene, CellGeometry.Shape shape, @Nullable VoronoiLattice.Cell hovered, LongSet drawn,
                           int edgeColor, int gridColor) {
        boolean isHovered = shape.cell().equals(hovered);
        long own = ChunkPos.asLong(shape.cell().ix(), shape.cell().iz());
        for (CellGeometry.Segment edge : shape.edges()) {
            this.modelLine(buffer, scene, edge.from(), edge.to(), edgeColor);
        }
        for (CellGeometry.Face face : shape.faces()) {
            VoronoiLattice.Cell neighbor = face.neighbor();
            if (!isHovered && neighbor != null && neighbor.iy() == 0) {
                long other = ChunkPos.asLong(neighbor.ix(), neighbor.iz());
                if (neighbor.equals(hovered) || drawn.contains(other) && other < own) {
                    continue;
                }
            }
            for (CellGeometry.Segment line : face.gridlines()) {
                this.modelLine(buffer, scene, line.from(), line.to(), gridColor);
            }
        }
        Vec3 site = shape.site();
        this.modelLine(buffer, scene, site.add(-SITE_HALF, 0, 0), site.add(SITE_HALF, 0, 0), edgeColor);
        this.modelLine(buffer, scene, site.add(0, -SITE_HALF, 0), site.add(0, SITE_HALF, 0), edgeColor);
        this.modelLine(buffer, scene, site.add(0, 0, -SITE_HALF), site.add(0, 0, SITE_HALF), edgeColor);
    }

    /** One line between two world points, in model space. */
    private void modelLine(BufferBuilder buffer, Scene scene, Vec3 from, Vec3 to, int color) {
        float centerY = scene.centerY();
        line(buffer, (float) (from.x - this.originX), (float) from.y - centerY, (float) (from.z - this.originZ),
                (float) (to.x - this.originX), (float) to.y - centerY, (float) (to.z - this.originZ), color);
    }

    /**
     * The twelve edges of the region box in model space.
     * Each row holds the two end points and then the two faces that meet at the edge.
     * The faces are 0 and 1 for least and most X, 2 and 3 for Y, 4 and 5 for Z.
     */
    private float[][] edges(Scene scene) {
        float minX = (float) (scene.minX() - this.originX);
        float maxX = minX + scene.size() * 16;
        float minZ = (float) (scene.minZ() - this.originZ);
        float maxZ = minZ + scene.size() * 16;
        float bottom = scene.dimension().minY() - scene.centerY();
        float top = scene.dimension().maxY() - scene.centerY();
        float[] sidesX = {minX, maxX};
        float[] sidesZ = {minZ, maxZ};
        float[] levels = {bottom, top};
        float[][] edges = new float[EDGES][];
        int next = 0;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                edges[next++] = new float[]{sidesX[a], bottom, sidesZ[b], sidesX[a], top, sidesZ[b], a, 4 + b};
                edges[next++] = new float[]{minX, levels[a], sidesZ[b], maxX, levels[a], sidesZ[b], 2 + a, 4 + b};
                edges[next++] = new float[]{sidesX[b], levels[a], minZ, sidesX[b], levels[a], maxZ, 2 + a, b};
            }
        }
        return edges;
    }

    /**
     * Finds the edges with a pixel in front of a block. Only an edge between two faces that face the camera qualifies.
     * For such an edge, the test samples the edge once per pixel and marches the view ray from each sample through the region.
     * The march steps through sections first and through blocks only in a section with a solid block.
     * The result stays until the view, the window size, the pan center or the drawn chunks change.
     */
    private void updateCoverEdges(Scene scene, Vector3f camera, Matrix4f view, Matrix4f projection, int width, int height) {
        if (this.coverVersion == this.solidsVersion && this.coverWidth == width && this.coverHeight == height
                && this.coverOriginX == this.originX && this.coverOriginZ == this.originZ
                && this.coverView.equals(view) && this.coverProjection.equals(projection)) {
            return;
        }
        this.coverVersion = this.solidsVersion;
        this.coverOriginX = this.originX;
        this.coverOriginZ = this.originZ;
        this.coverWidth = width;
        this.coverHeight = height;
        this.coverView.set(view);
        this.coverProjection.set(projection);
        float minX = (float) (scene.minX() - this.originX);
        float maxX = minX + scene.size() * 16;
        float minZ = (float) (scene.minZ() - this.originZ);
        float maxZ = minZ + scene.size() * 16;
        float bottom = scene.dimension().minY() - scene.centerY();
        float top = scene.dimension().maxY() - scene.centerY();
        boolean[] frontFaces = {camera.x < minX, camera.x > maxX, camera.y < bottom, camera.y > top, camera.z < minZ, camera.z > maxZ};
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        float[][] edges = this.edges(scene);
        for (int i = 0; i < EDGES; i++) {
            float[] edge = edges[i];
            this.coverEdges[i] = frontFaces[(int) edge[6]] && frontFaces[(int) edge[7]]
                    && this.coversBlock(scene, edge, camera, viewProjection, width, height);
        }
    }

    private boolean coversBlock(Scene scene, float[] edge, Vector3f camera, Matrix4f viewProjection, int width, int height) {
        Vector4f start = viewProjection.transform(new Vector4f(edge[0], edge[1], edge[2], 1));
        Vector4f end = viewProjection.transform(new Vector4f(edge[3], edge[4], edge[5], 1));
        double samples;
        if (start.w > 0 && end.w > 0) {
            samples = Math.hypot((start.x / start.w - end.x / end.w) * width / 2, (start.y / start.w - end.y / end.w) * height / 2);
        } else {
            // An end point behind the camera has no pixel, so two samples per block stand in.
            samples = 2 * Math.sqrt(Mth.square(edge[3] - edge[0]) + Mth.square(edge[4] - edge[1]) + Mth.square(edge[5] - edge[2]));
        }
        int count = Mth.clamp((int) Math.ceil(samples) + 1, 2, MAX_EDGE_SAMPLES);
        Vector4f clip = new Vector4f();
        for (int i = 0; i < count; i++) {
            float t = i / (float) (count - 1);
            float x = Mth.lerp(t, edge[0], edge[3]);
            float y = Mth.lerp(t, edge[1], edge[4]);
            float z = Mth.lerp(t, edge[2], edge[5]);
            viewProjection.transform(clip.set(x, y, z, 1));
            if (clip.w <= 0 || Math.abs(clip.x) > clip.w || Math.abs(clip.y) > clip.w) {
                continue;
            }
            double dx = x - camera.x;
            double dy = y - camera.y;
            double dz = z - camera.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0 && this.rayHitsSolid(scene, x + this.originX - scene.minX(), y + scene.centerY() - scene.dimension().minY(), z + this.originZ - scene.minZ(),
                    dx / length, dy / length, dz / length)) {
                return true;
            }
        }
        return false;
    }

    /** Marches a ray from a point on the region box until it leaves the box. The point is relative to the least box corner. */
    private boolean rayHitsSolid(Scene scene, double x, double y, double z, double dx, double dy, double dz) {
        int size = scene.size();
        int sections = scene.sections();
        double exit = Math.min(Math.min(exit(x, dx, size * 16), exit(y, dy, sections * 16)), exit(z, dz, size * 16));
        double t = RAY_START;
        if (t >= exit) {
            return false;
        }
        int cellX = Mth.clamp(Mth.floor((x + dx * t) / 16), 0, size - 1);
        int cellY = Mth.clamp(Mth.floor((y + dy * t) / 16), 0, sections - 1);
        int cellZ = Mth.clamp(Mth.floor((z + dz * t) / 16), 0, size - 1);
        double nextX = next(x, dx, cellX, 16);
        double nextY = next(y, dy, cellY, 16);
        double nextZ = next(z, dz, cellZ, 16);
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dz);
        while (t < exit) {
            double next = Math.min(nextX, Math.min(nextY, nextZ));
            long[] bits = this.solids[(cellX * size + cellZ) * sections + cellY];
            if (bits != null && hitsBlock(bits, cellX, cellY, cellZ, x, y, z, dx, dy, dz, t, Math.min(next, exit))) {
                return true;
            }
            if (next == nextX) {
                cellX += dx > 0 ? 1 : -1;
                nextX += deltaX;
            } else if (next == nextY) {
                cellY += dy > 0 ? 1 : -1;
                nextY += deltaY;
            } else {
                cellZ += dz > 0 ? 1 : -1;
                nextZ += deltaZ;
            }
            if (cellX < 0 || cellX >= size || cellY < 0 || cellY >= sections || cellZ < 0 || cellZ >= size) {
                return false;
            }
            t = next;
        }
        return false;
    }

    /** Marches a ray through the blocks of one section from the ray parameter {@code from} to {@code to}. */
    private static boolean hitsBlock(long[] bits, int cellX, int cellY, int cellZ, double x, double y, double z,
                                     double dx, double dy, double dz, double from, double to) {
        int blockX = Mth.clamp(Mth.floor(x + dx * from), cellX * 16, cellX * 16 + 15);
        int blockY = Mth.clamp(Mth.floor(y + dy * from), cellY * 16, cellY * 16 + 15);
        int blockZ = Mth.clamp(Mth.floor(z + dz * from), cellZ * 16, cellZ * 16 + 15);
        double nextX = next(x, dx, blockX, 1);
        double nextY = next(y, dy, blockY, 1);
        double nextZ = next(z, dz, blockZ, 1);
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dz);
        double t = from;
        while (t < to) {
            int bit = (blockY & 15) << 8 | (blockZ & 15) << 4 | blockX & 15;
            if ((bits[bit >> 6] >>> (bit & 63) & 1) != 0) {
                return true;
            }
            double next = Math.min(nextX, Math.min(nextY, nextZ));
            if (next == nextX) {
                blockX += dx > 0 ? 1 : -1;
                nextX += deltaX;
            } else if (next == nextY) {
                blockY += dy > 0 ? 1 : -1;
                nextY += deltaY;
            } else {
                blockZ += dz > 0 ? 1 : -1;
                nextZ += deltaZ;
            }
            if (blockX >> 4 != cellX || blockY >> 4 != cellY || blockZ >> 4 != cellZ) {
                return false;
            }
            t = next;
        }
        return false;
    }

    /** The ray parameter where the ray leaves the range from 0 to {@code limit} on one axis. */
    private static double exit(double position, double direction, double limit) {
        if (direction > 0) {
            return (limit - position) / direction;
        }
        if (direction < 0) {
            return -position / direction;
        }
        return Double.POSITIVE_INFINITY;
    }

    /** The ray parameter of the next cell border on one axis. */
    private static double next(double position, double direction, int cell, int cellSize) {
        if (direction > 0) {
            return ((cell + 1) * cellSize - position) / direction;
        }
        if (direction < 0) {
            return (cell * cellSize - position) / direction;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static void line(BufferBuilder lines, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        Vector3f normal = new Vector3f(x1 - x0, y1 - y0, z1 - z0).normalize();
        lines.addVertex(x0, y0, z0).setColor(color).setNormal(normal.x, normal.y, normal.z);
        lines.addVertex(x1, y1, z1).setColor(color).setNormal(normal.x, normal.y, normal.z);
    }

    // [VANILLACOPY] LevelRenderer#renderEndSky
    private static void renderEndSky(PoseStack poseStack) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, END_SKY_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();

        for (int i = 0; i < 6; i++) {
            poseStack.pushPose();
            if (i == 1) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }

            if (i == 2) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }

            if (i == 3) {
                poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            }

            if (i == 4) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            }

            if (i == 5) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            }

            Matrix4f matrix4f = poseStack.last().pose();
            BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(-14145496);
            bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(-14145496);
            bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(-14145496);
            bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(-14145496);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            poseStack.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Frees every buffer and the lightmap. Call it after the builder stops. */
    void close(PreviewBuilder builder) {
        PreviewBuilder.Output output;
        while ((output = builder.poll()) != null) {
            if (output instanceof PreviewBuilder.ChunkReady ready) {
                ready.free();
            }
        }
        this.clear();
        Arrays.fill(this.solids, null);
        Minecraft.getInstance().getTextureManager().release(LIGHTMAP);
    }

}
