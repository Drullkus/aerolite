package us.drullk.aerolite.client.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import us.drullk.aerolite.client.PresetTools;
import us.drullk.aerolite.world.AeroliteSettings;

import javax.annotation.Nullable;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BiConsumer;

/**
 * The preview of the preset settings. It shows the shafts of a region around the origin.
 * The ImGui panel on the right edits a copy of the settings. Done sends the copy back. Cancel drops it.
 * A previewed dimension without variations shows the empty region: sky, grid and outline.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_85da4419-5789-4ddd-8712-bcdaaca5bcce.md}.
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
        this.dimensions = PreviewDimension.of(context, tools.edits());
        this.levels = context.selectedDimensions().dimensions().keySet().stream().map(Registries::levelStemToLevel).toList();
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

    /** Every level of the world being created. */
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
        this.builder.request(new PreviewBuilder.Request(this.settings, this.seed, dimension, this.radius, constantAmbientLight, this.biomes, this.noise));
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
            this.renderer.drain(this.builder);
            this.renderer.render(this.camera);
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
        if (button == 0) {
            this.orbiting = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.orbiting = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.orbiting && button == 0) {
            this.camera.turn(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.camera.zoom(scrollY);
        return true;
    }

}
