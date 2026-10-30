package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.shaders.FogShape;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import us.drullk.aerolite.Aerolite;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the baked sections of the preview, with the sky, the fog, a floor grid and the region outline.
 * The fog covers the blocks only.
 * The model space has the region center at the origin, so the fog grows from the center as it does around a player.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
final class PreviewRenderer {

    private static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final ResourceLocation LIGHTMAP = ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "preview_lightmap");
    private static final int LIGHTMAP_SIZE = 16;
    private static final int GRID_COLOR = 0x80A0A0A0;
    private static final int OUTLINE_COLOR = 0xFFFFFFFF;
    private static final float SKY_NEAR = 1;
    private static final float SKY_FAR = 1000;

    private record SectionBuffer(BlockPos origin, RenderType layer, VertexBuffer buffer) {
    }

    /** The region and backdrop of one finished generation. */
    private record Scene(PreviewDimension dimension, int radius, Holder<Biome> centerBiome) {

        float centerX() {
            return 8;
        }

        float centerY() {
            return (this.dimension.minY() + this.dimension.maxY()) / 2F;
        }

        float centerZ() {
            return 8;
        }
    }

    private final List<SectionBuffer> active = new ArrayList<>();
    private final List<SectionBuffer> pending = new ArrayList<>();
    private long pendingGeneration = -1;
    @Nullable
    private Scene scene;
    private final DynamicTexture lightmap;

    PreviewRenderer() {
        this.lightmap = new DynamicTexture(LIGHTMAP_SIZE, LIGHTMAP_SIZE, false);
        this.lightmap.getPixels().fillRect(0, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, -1);
        this.lightmap.upload();
        Minecraft.getInstance().getTextureManager().register(LIGHTMAP, this.lightmap);
    }

    /** The count of uploaded section layers that the renderer draws. */
    int drawnSections() {
        return this.active.size();
    }

    /** Uploads the meshes of the builder. A finished generation replaces the drawn one. */
    void drain(PreviewBuilder builder) {
        PreviewBuilder.Output output;
        while ((output = builder.poll()) != null) {
            if (output.generation() < builder.latestGeneration()) {
                if (output instanceof PreviewBuilder.Baked baked) {
                    baked.free();
                }
                continue;
            }
            if (output.generation() != this.pendingGeneration) {
                free(this.pending);
                this.pendingGeneration = output.generation();
            }
            switch (output) {
                case PreviewBuilder.Baked baked -> {
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    buffer.upload(baked.mesh());
                    VertexBuffer.unbind();
                    baked.buffer().close();
                    this.pending.add(new SectionBuffer(baked.origin(), baked.layer(), buffer));
                }
                case PreviewBuilder.Complete complete -> {
                    free(this.active);
                    this.active.addAll(this.pending);
                    this.pending.clear();
                    this.scene = new Scene(complete.dimension(), complete.radius(), complete.centerBiome());
                }
            }
        }
    }

    private static void free(List<SectionBuffer> buffers) {
        for (SectionBuffer buffer : buffers) {
            buffer.buffer().close();
        }
        buffers.clear();
    }

    /** Draws the scene into the bound main render target and clears the depth for the GUI. */
    void render(OrbitCamera camera) {
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
        Vec3 sky = Vec3.fromRGB24(biome.getSkyColor());
        Vec3 fog = fogColor(effects, biome, sky, scene.radius());
        Vec3 clear = effects.skyType() == DimensionSpecialEffects.SkyType.NORMAL ? sky : fog;
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
        this.setupFog(effects, scene, fog);

        for (RenderType layer : RenderType.chunkBufferLayers()) {
            this.renderLayer(layer, scene, view, projection);
        }
        // The grid and the outline stay visible through the fog.
        FogRenderer.setupNoFog();
        this.renderLines(scene);

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    // [VANILLACOPY] FogRenderer#setupColor, open air branch: noon brightness, no sunrise, no rain, no void darkness, the center biome only
    private static Vec3 fogColor(DimensionSpecialEffects effects, Biome biome, Vec3 sky, int renderDistanceChunks) {
        float f4 = 0.25F + 0.75F * (float) renderDistanceChunks / 32.0F;
        f4 = 1.0F - (float) Math.pow((double) f4, 0.25);
        float f11 = 1.0F;
        Vec3 vec32 = effects.getBrightnessDependentFogColor(Vec3.fromRGB24(biome.getFogColor()), f11);
        float fogRed = (float) vec32.x();
        float fogGreen = (float) vec32.y();
        float fogBlue = (float) vec32.z();
        fogRed = fogRed + ((float) sky.x - fogRed) * f4;
        fogGreen = fogGreen + ((float) sky.y - fogGreen) * f4;
        fogBlue = fogBlue + ((float) sky.z - fogBlue) * f4;
        return new Vec3(fogRed, fogGreen, fogBlue);
    }

    // [VANILLACOPY] FogRenderer#setupFog, terrain fog of open air: the radius is the render distance, no fluid, no mob effect, no boss fog
    private void setupFog(DimensionSpecialEffects effects, Scene scene, Vec3 fog) {
        float farPlaneDistance = scene.radius() * 16;
        float start;
        float end;
        FogShape shape = FogShape.SPHERE;
        if (effects.isFoggyAt(Mth.floor(scene.centerX()), Mth.floor(scene.centerZ()))) {
            start = farPlaneDistance * 0.05F;
            end = Math.min(farPlaneDistance, 192.0F) * 0.5F;
        } else {
            float f = Mth.clamp(farPlaneDistance / 10.0F, 4.0F, 64.0F);
            start = farPlaneDistance - f;
            end = farPlaneDistance;
            shape = FogShape.CYLINDER;
        }
        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(shape);
        RenderSystem.setShaderFogColor((float) fog.x, (float) fog.y, (float) fog.z);
    }

    // [VANILLACOPY] LevelRenderer#renderSectionLayer: the section list of the preview, a white lightmap, offsets from the region center
    private void renderLayer(RenderType renderType, Scene scene, Matrix4f frustrumMatrix, Matrix4f projectionMatrix) {
        boolean any = false;
        for (SectionBuffer section : this.active) {
            if (section.layer() == renderType) {
                any = true;
                break;
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
        for (SectionBuffer section : this.active) {
            if (section.layer() != renderType) {
                continue;
            }
            BlockPos blockpos = section.origin();
            if (uniform != null) {
                uniform.set(blockpos.getX() - scene.centerX(), blockpos.getY() - scene.centerY(), blockpos.getZ() - scene.centerZ());
                uniform.upload();
            }
            section.buffer().bind();
            section.buffer().draw();
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
        float min = -scene.radius() * 16 - scene.centerX();
        float max = (scene.radius() + 1) * 16 - scene.centerX();
        float bottom = scene.dimension().minY() - scene.centerY();
        float top = scene.dimension().maxY() - scene.centerY();
        BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (float edge = min; edge <= max + 0.5F; edge += 16) {
            line(lines, edge, bottom, min, edge, bottom, max, GRID_COLOR);
            line(lines, min, bottom, edge, max, bottom, edge, GRID_COLOR);
        }
        float[] sides = {min, max};
        float[] levels = {bottom, top};
        for (float side : sides) {
            for (float other : sides) {
                line(lines, side, bottom, other, side, top, other, OUTLINE_COLOR);
            }
            for (float level : levels) {
                line(lines, min, level, side, max, level, side, OUTLINE_COLOR);
                line(lines, side, level, min, side, level, max, OUTLINE_COLOR);
            }
        }
        MeshData mesh = lines.build();
        if (mesh != null) {
            RenderType.lines().draw(mesh);
        }
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
            if (output instanceof PreviewBuilder.Baked baked) {
                baked.free();
            }
        }
        free(this.active);
        free(this.pending);
        Minecraft.getInstance().getTextureManager().release(LIGHTMAP);
    }

}
