package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.PresetTools;
import us.drullk.aerolite.world.AeroliteSettings;

import javax.annotation.Nullable;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BiConsumer;

/**
 * The preview of the shaft settings. It shows the shafts of a region around the region center.
 * The ImGui panel on the right edits a copy of the settings. Done sends the copy back. Cancel drops it.
 * A previewed dimension without variations shows the empty region: sky, grid and outline.
 * The movement keys and a middle drag move the region center. A move across a chunk border shifts the region.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_bf29e15f-47bb-42fe-94f0-29ae22f43f5a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class PreviewScreen extends Screen {

    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 32;
    private static final int BUTTON_WIDTH = 100;
    private static final int MARGIN = 8;

    private final Screen parent;
    private final PresetTools tools;
    private final BiConsumer<AeroliteSettings, String> onDone;
    private final List<PreviewDimension> dimensions;
    private final List<ResourceKey<Level>> levels;
    private final Registry<Biome> biomes;
    private final HolderGetter<NormalNoise.NoiseParameters> noise;
    private final OrbitCamera camera = new OrbitCamera();
    private final RegionPan pan = new RegionPan();
    private final ProviderTabs providerTabs = new ProviderTabs();
    /** The center chunk of the last request. */
    @Nullable
    private ChunkPos requestedCenter;
    private long lastFrameNanos;
    @Nullable
    private String centerEditX;
    @Nullable
    private String centerEditZ;
    private AeroliteSettings settings;
    private String seedText;
    /** The seed of the last seed text that parses. */
    private long seed;
    private int dimensionIndex;
    private int radius;
    @Nullable
    private PreviewBuilder builder;
    @Nullable
    private PreviewRenderer renderer;
    private boolean orbiting;

    /**
     * @param seedText the seed text of the world creation screen; an empty text gives a random seed
     * @param onDone    takes the settings and the seed text when the user clicks Done
     */
    public PreviewScreen(Screen parent, WorldCreationContext context, PresetTools tools, AeroliteSettings settings, String seedText,
                         BiConsumer<AeroliteSettings, String> onDone) {
        super(Component.translatable("createWorld.customize.aerolite.preview.title"));
        this.parent = parent;
        this.tools = tools;
        this.onDone = onDone;
        this.settings = settings;
        OptionalLong parsed = WorldOptions.parseSeed(seedText);
        if (parsed.isPresent()) {
            this.seed = parsed.getAsLong();
            this.seedText = seedText;
        } else {
            this.seed = WorldOptions.randomSeed();
            this.seedText = Long.toString(this.seed);
        }
        tools.edits().useRegistries(context.worldgenLoadContext());
        this.dimensions = PreviewDimension.of(context, tools.edits());
        this.levels = tools.edits().worldDimensions(context);
        this.biomes = context.worldgenLoadContext().registryOrThrow(Registries.BIOME);
        this.noise = context.worldgenLoadContext().lookupOrThrow(Registries.NOISE);
        this.radius = Mth.clamp(Minecraft.getInstance().options.renderDistance().get(), MIN_RADIUS, MAX_RADIUS);
    }

    /** Opens the preview. The window then reports the width left of the panel. */
    public void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(this);
        minecraft.resizeDisplay();
    }

    public PresetTools tools() {
        return this.tools;
    }

    /** The open field tabs of the variation boxes of the panel. */
    public ProviderTabs providerTabs() {
        return this.providerTabs;
    }

    public AeroliteSettings settings() {
        return this.settings;
    }

    public void setSettings(AeroliteSettings settings) {
        if (!settings.equals(this.settings)) {
            this.settings = settings;
            this.requestBuild();
        }
    }

    /** The text of the seed field. */
    public String seedText() {
        return this.seedText;
    }

    /**
     * Parses the text like the seed field of the world creation screen. A number is the seed. Other text hashes.
     * A text without a seed keeps the last seed.
     */
    public void setSeedText(String text) {
        this.seedText = text;
        OptionalLong parsed = WorldOptions.parseSeed(text);
        if (parsed.isPresent() && parsed.getAsLong() != this.seed) {
            this.seed = parsed.getAsLong();
            this.requestBuild();
        }
    }

    /** Picks a random seed and shows its decimal number in the seed field. */
    public void reseed() {
        this.seed = WorldOptions.randomSeed();
        this.seedText = Long.toString(this.seed);
        this.requestBuild();
    }

    /** The seed text that gives the previewed seed. */
    private String chosenSeedText() {
        return WorldOptions.parseSeed(this.seedText).isPresent() ? this.seedText : Long.toString(this.seed);
    }

    /** The dimensions that the preview can show. */
    public List<PreviewDimension> dimensions() {
        return this.dimensions;
    }

    /** Every dimension of the world being created, in the sequence of the variation sections. */
    public List<ResourceKey<Level>> levels() {
        return this.levels;
    }

    public int dimensionIndex() {
        return this.dimensionIndex;
    }

    /** The index of a dimension in {@link #dimensions()}, or -1 when the preview cannot show it. */
    public int dimensionIndex(ResourceKey<Level> key) {
        for (int index = 0; index < this.dimensions.size(); index++) {
            if (this.dimensions.get(index).key().equals(key)) {
                return index;
            }
        }
        return -1;
    }

    /** The key of the previewed dimension, or null when the context holds no dimension that the preview can show. */
    @Nullable
    public ResourceKey<Level> dimensionKey() {
        PreviewDimension dimension = this.dimension();
        return dimension == null ? null : dimension.key();
    }

    /** Previews the dimension of {@code key}. A dimension that the preview cannot show changes nothing. */
    public void setDimension(ResourceKey<Level> key) {
        int index = this.dimensionIndex(key);
        if (index >= 0) {
            this.setDimensionIndex(index);
        }
    }

    public OrbitCamera camera() {
        return this.camera;
    }

    public void setDimensionIndex(int index) {
        if (index != this.dimensionIndex && index >= 0 && index < this.dimensions.size()) {
            this.dimensionIndex = index;
            this.frameCamera();
            this.requestBuild();
        }
    }

    public int radius() {
        return this.radius;
    }

    /** The world X of the region center in blocks. */
    public double centerX() {
        return this.pan.x();
    }

    /** The world Z of the region center in blocks. */
    public double centerZ() {
        return this.pan.z();
    }

    /** Moves the region center. A move to another chunk shifts the region at once. */
    public void setCenter(double x, double z) {
        this.pan.set(x, z);
        this.followCenter();
    }

    /** The text of the panel field of one center axis while the panel edits it, or null. */
    @Nullable
    public String centerEdit(Direction.Axis axis) {
        return axis == Direction.Axis.X ? this.centerEditX : this.centerEditZ;
    }

    public void setCenterEdit(Direction.Axis axis, @Nullable String text) {
        if (axis == Direction.Axis.X) {
            this.centerEditX = text;
        } else {
            this.centerEditZ = text;
        }
    }

    /** Puts the region center on the origin. */
    public void resetCenter() {
        this.setCenter(0, 0);
    }

    /** Requests a shift when the region center left the chunk of the last request. */
    private void followCenter() {
        ChunkPos center = new ChunkPos(this.pan.chunkX(), this.pan.chunkZ());
        if (!center.equals(this.requestedCenter)) {
            this.requestBuild();
        }
    }

    public void setRadius(int radius) {
        int clamped = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        if (clamped != this.radius) {
            this.radius = clamped;
            this.frameCamera();
            this.requestBuild();
        }
    }

    public PreviewBuilder.Progress progress() {
        return this.builder == null ? new PreviewBuilder.Progress(PreviewBuilder.Phase.WAITING, 0, 0) : this.builder.progress();
    }

    /** The panel width in ImGui units. */
    public int panelUnits() {
        return this.tools.viewport().panelUnits();
    }

    /** Moves the panel edge. Call it outside the frame render, because it resizes the main render target. */
    public void applyPanelUnits(int units) {
        if (units != this.tools.viewport().panelUnits() && this.minecraft != null && this.minecraft.screen == this) {
            this.tools.viewport().setPanelUnits(units);
            this.minecraft.resizeDisplay();
        }
    }

    @Nullable
    private PreviewDimension dimension() {
        return this.dimensions.isEmpty() ? null : this.dimensions.get(this.dimensionIndex);
    }

    private void frameCamera() {
        PreviewDimension dimension = this.dimension();
        if (dimension != null) {
            this.camera.frame((2 * this.radius + 1) * 8, (dimension.maxY() - dimension.minY()) / 2F);
        }
    }

    private void requestBuild() {
        PreviewDimension dimension = this.dimension();
        if (this.builder == null || dimension == null) {
            return;
        }
        boolean constantAmbientLight = DimensionSpecialEffects.forType(dimension.type()).constantAmbientLight();
        ChunkPos center = new ChunkPos(this.pan.chunkX(), this.pan.chunkZ());
        this.requestedCenter = center;
        this.builder.request(new PreviewBuilder.Request(this.settings, this.seed, dimension, this.radius, constantAmbientLight, this.biomes, this.noise, center));
    }

    /** The movement keys and the middle drag work only while no widget and no panel field takes the keyboard. */
    private boolean panAllowed() {
        return this.getFocused() == null && !this.tools.panel().wantsTextInput();
    }

    /** The movement key of a key event, or null. */
    @Nullable
    private RegionPan.Key panKey(int keyCode, int scanCode) {
        Options options = Minecraft.getInstance().options;
        if (options.keyUp.matches(keyCode, scanCode)) {
            return RegionPan.Key.FORWARD;
        }
        if (options.keyDown.matches(keyCode, scanCode)) {
            return RegionPan.Key.BACK;
        }
        if (options.keyLeft.matches(keyCode, scanCode)) {
            return RegionPan.Key.LEFT;
        }
        if (options.keyRight.matches(keyCode, scanCode)) {
            return RegionPan.Key.RIGHT;
        }
        return null;
    }

    /** True while a Ctrl key is held. */
    private static boolean ctrlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** Moves the region center by the held keys over the real frame time. */
    private void movePan() {
        long now = System.nanoTime();
        double seconds = this.lastFrameNanos == 0 ? 0 : (now - this.lastFrameNanos) / 1.0E9;
        this.lastFrameNanos = now;
        if (!this.panAllowed()) {
            this.pan.releaseAll();
            return;
        }
        this.pan.move(seconds, this.camera.yaw(), ctrlDown());
        this.followCenter();
    }

    /** The projection times the view, with the region center at the model space origin. */
    private Matrix4f viewProjection() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        return this.camera.projection(target.width / (float) Math.max(1, target.height)).mul(this.camera.view());
    }

    private float ndcX(double mouseX) {
        return (float) (2 * mouseX / Math.max(1, this.width) - 1);
    }

    private float ndcY(double mouseY) {
        return (float) (1 - 2 * mouseY / Math.max(1, this.height));
    }

    /** Runs before the first init, so the window reports the reduced width to it. */
    @Override
    public void added() {
        this.tools.viewport().activate(this.tools.config().panelWidth());
        if (this.builder == null) {
            Minecraft minecraft = Minecraft.getInstance();
            this.builder = new PreviewBuilder(this.tools.painter(), this.tools.mazes(), minecraft.getBlockRenderer());
            this.renderer = new PreviewRenderer();
            this.frameCamera();
            this.requestBuild();
        }
    }

    @Override
    protected void init() {
        int y = this.height - 20 - MARGIN;
        int x = this.width / 2 - BUTTON_WIDTH - MARGIN / 2;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.onDone.accept(this.settings, this.chosenSeedText());
            this.closeTo(this.parent);
        }).bounds(x, y, BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(x + BUTTON_WIDTH + MARGIN, y, BUTTON_WIDTH, 20).build());
    }

    private void closeTo(Screen next) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(next);
        minecraft.resizeDisplay();
    }

    @Override
    public void onClose() {
        this.closeTo(this.parent);
    }

    @Override
    public void removed() {
        this.tools.viewport().deactivate();
        if (this.builder != null) {
            this.builder.close();
            if (this.renderer != null) {
                this.renderer.close(this.builder);
            }
        }
        this.builder = null;
        this.renderer = null;
        this.tools.icons().clear();
        // A close by another path also gives the main render target the full width again.
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.tell(() -> {
            if (minecraft.getMainRenderTarget().width != minecraft.getWindow().getWidth()) {
                minecraft.resizeDisplay();
            }
        });
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.flush();
        if (this.renderer != null && this.builder != null) {
            this.movePan();
            this.renderer.drain(this.builder);
            this.renderer.render(this.camera, this.pan.x(), this.pan.z());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.tools.icons().flush(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.setFocused(null);
            this.orbiting = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            this.setFocused(null);
            this.pan.beginDrag(this.ndcX(mouseX), this.ndcY(mouseY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.orbiting = false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            this.pan.endDrag();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.orbiting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.camera.turn(dragX, dragY);
            return true;
        }
        if (this.pan.dragging() && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (this.panAllowed() && this.pan.dragTo(this.viewProjection(), this.ndcX(mouseX), this.ndcY(mouseY))) {
                this.followCenter();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        RegionPan.Key key = this.panKey(keyCode, scanCode);
        if (key != null && this.panAllowed()) {
            this.pan.press(key, Util.getMillis());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        RegionPan.Key key = this.panKey(keyCode, scanCode);
        if (key != null) {
            this.pan.release(key);
        }
        return super.keyReleased(keyCode, scanCode, modifiers) || key != null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.camera.zoom(scrollY);
        return true;
    }

}
