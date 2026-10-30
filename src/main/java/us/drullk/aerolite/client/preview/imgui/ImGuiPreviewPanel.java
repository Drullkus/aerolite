package us.drullk.aerolite.client.preview.imgui;

import foundry.imgui.api.ImGuiMCEvents;
import foundry.veil.api.client.render.VeilRenderSystem;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.ImVec4;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.AeroliteClientConfig;
import us.drullk.aerolite.client.PresetSliders;
import us.drullk.aerolite.client.PresetTools;
import us.drullk.aerolite.client.preview.PreviewBuilder;
import us.drullk.aerolite.client.preview.PreviewDimension;
import us.drullk.aerolite.client.preview.PreviewPanel;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftVariation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The ImGui panel on the right edge of the preview screen. It edits the settings with the steps and rules of the vanilla screen.
 * Only the bean method of {@link PreviewPanel} creates it, and only when imgui-mc is present.
 * The sections are Preview, Shaft Placement, Shaft Surface, Clouds and Shaft Variations, in this sequence.
 * A radio left of each dimension name and the dimension combo of the Preview section select the previewed dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_85da4419-5789-4ddd-8712-bcdaaca5bcce.md}.
 */
public final class ImGuiPreviewPanel implements PreviewPanel {

    private static final String KEY = "createWorld.customize.aerolite.";
    private static final List<String> BLOCK_LABELS = List.of("interior_block", "wall_block", "path_block");
    private static final float SPLITTER_WIDTH = 6;
    private static final int SEED_CAPACITY = 256;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking;

    private final Map<String, ImGuiBlockField> blockFields = new HashMap<>();
    private final int[] windowWidth = new int[1];
    private final int[] windowHeight = new int[1];
    private final int[] framebufferWidth = new int[1];
    private final int[] framebufferHeight = new int[1];
    private final int[] slider = new int[1];
    private final ImGuiViewGizmo gizmo = new ImGuiViewGizmo();
    private final ImString seedField = new ImString(SEED_CAPACITY);
    /** The seed text that the seed field last took from the screen or gave to it. */
    @Nullable
    private String seedShown;
    /** The dimension that the add combo shows. */
    @Nullable
    private ResourceKey<Level> addChoice;
    @Nullable
    private PreviewScreen drawn;
    private float width;
    private boolean dragging;

    /** The components of the drawn screen. */
    @Nullable
    private PresetTools tools;

    private ImGuiPreviewPanel() {
    }

    private PresetTools tools() {
        return Objects.requireNonNull(this.tools, "The panel draws only for an open preview screen");
    }

    /** Creates the panel and adds its draw hooks to the imgui-mc events. */
    public static ImGuiPreviewPanel create() {
        ImGuiPreviewPanel panel = new ImGuiPreviewPanel();
        ImGuiMCEvents.INSTANCE.preRenderImGuiEvent(panel::drawPre);
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(panel::fixFramebufferScale);
        return panel;
    }

    @Override
    public boolean available() {
        return VeilRenderSystem.hasImGui();
    }

    /**
     * imgui-mc divides the main render target width by the window width. The preview narrows that target,
     * so the panel sets the scale from the window framebuffer.
     */
    private void fixFramebufferScale() {
        if (!(Minecraft.getInstance().screen instanceof PreviewScreen)) {
            return;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwGetWindowSize(window, this.windowWidth, this.windowHeight);
        GLFW.glfwGetFramebufferSize(window, this.framebufferWidth, this.framebufferHeight);
        if (this.windowWidth[0] > 0 && this.windowHeight[0] > 0) {
            ImGui.getIO().setDisplayFramebufferScale(this.framebufferWidth[0] / (float) this.windowWidth[0], this.framebufferHeight[0] / (float) this.windowHeight[0]);
        }
    }

    private void drawPre() {
        if (!(Minecraft.getInstance().screen instanceof PreviewScreen screen)) {
            this.drawn = null;
            return;
        }
        this.fixFramebufferScale();
        this.tools = screen.tools();
        if (screen != this.drawn) {
            this.drawn = screen;
            this.blockFields.clear();
            this.addChoice = null;
            this.width = screen.panelUnits();
            this.dragging = false;
        }
        if (!this.dragging) {
            this.width = screen.panelUnits();
        }
        // With multiple viewports, ImGui positions are desktop coordinates. The main viewport is the game window.
        ImGuiViewport viewport = ImGui.getMainViewport();
        float windowWidth = viewport.getSizeX();
        float windowHeight = viewport.getSizeY();

        ImGui.setNextWindowViewport(viewport.getID());
        ImGui.setNextWindowPos(viewport.getPosX() + windowWidth - this.width, viewport.getPosY());
        ImGui.setNextWindowSize(this.width, windowHeight);
        ImVec4 background = ImGui.getStyle().getColor(ImGuiCol.WindowBg);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, background.x, background.y, background.z, 1);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0);
        boolean open = ImGui.begin("##aerolite_preview", WINDOW_FLAGS);
        ImGui.popStyleVar(3);
        ImGui.popStyleColor();
        if (open) {
            this.drawSplitter(screen, viewport.getPosX() + windowWidth, windowHeight);
            ImGui.setCursorPos(SPLITTER_WIDTH, 0);
            if (ImGui.beginChild("##aerolite_preview_content", this.width - SPLITTER_WIDTH, windowHeight, ImGuiChildFlags.AlwaysUseWindowPadding)) {
                this.drawContent(screen);
            }
            ImGui.endChild();
        }
        ImGui.end();
        this.gizmo.draw(screen.camera(), this.width);
    }

    /** An invisible button on the left edge. A drag moves the edge. The release saves the width and resizes the game view. */
    private void drawSplitter(PreviewScreen screen, float rightEdge, float windowHeight) {
        ImGui.setCursorPos(0, 0);
        ImGui.invisibleButton("##aerolite_splitter", SPLITTER_WIDTH, windowHeight);
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        if (hovered || active) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        if (active) {
            this.dragging = true;
            this.width = Math.clamp(rightEdge - ImGui.getIO().getMousePosX(), AeroliteClientConfig.MIN_PANEL_WIDTH, AeroliteClientConfig.MAX_PANEL_WIDTH);
        }
        float left = ImGui.getWindowPosX();
        float top = ImGui.getWindowPosY();
        ImVec4 color = ImGui.getStyle().getColor(hovered || active ? ImGuiCol.SeparatorActive : ImGuiCol.Separator);
        ImGui.getWindowDrawList().addRectFilled(left, top, left + 2, top + windowHeight, ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w));
        if (ImGui.isItemDeactivated()) {
            this.dragging = false;
            int units = Math.round(this.width);
            this.tools().config().setPanelWidth(units);
            Minecraft.getInstance().tell(() -> screen.applyPanelUnits(units));
        }
    }

    private static String text(String key, Object... args) {
        return I18n.get(KEY + key, args);
    }

    /** An ImGui format string prints a percent sign only when it is doubled. */
    private static String format(Component label) {
        return label.getString().replace("%", "%%");
    }

    private static String format(String label) {
        return label.replace("%", "%%");
    }

    /** A full width slider over the steps from 0 to {@code steps}. Returns the new step, or -1 when the step stays. */
    private int stepSlider(String id, int step, int steps, String label) {
        this.slider[0] = step;
        ImGui.setNextItemWidth(-1);
        if (ImGui.sliderInt(id, this.slider, 0, steps, format(label)) && this.slider[0] != step) {
            return this.slider[0];
        }
        return -1;
    }

    private void drawContent(PreviewScreen screen) {
        if (ImGui.collapsingHeader(text("preview.section"), ImGuiTreeNodeFlags.DefaultOpen)) {
            this.drawPreview(screen);
        }

        AeroliteSettings settings = screen.settings();
        AeroliteSettings edited = settings;

        if (ImGui.collapsingHeader(text("shaft_placement"), ImGuiTreeNodeFlags.DefaultOpen)) {
            int column = this.stepSlider("##column_chance", this.tools().sliders().fractionStep(edited.columnChance()), PresetSliders.FRACTION_STEPS,
                    text("column_chance", this.tools().sliders().percent(this.tools().sliders().fractionStep(edited.columnChance()))));
            if (column >= 0) {
                edited = edited.withColumnChance(this.tools().sliders().fraction(column));
            }
            int min = this.stepSlider("##min_spacing", this.tools().sliders().spacingStep(edited.minShaftSpacing()), PresetSliders.SPACING_STEPS,
                    text("min_shaft_spacing", edited.minShaftSpacing()));
            if (min >= 0) {
                edited = this.tools().edits().withMinSpacingStep(edited, min);
            }
            int max = this.stepSlider("##max_spacing", this.tools().sliders().spacingStep(edited.maxShaftSpacing()), PresetSliders.SPACING_STEPS,
                    text("max_shaft_spacing", edited.maxShaftSpacing()));
            if (max >= 0) {
                edited = this.tools().edits().withMaxSpacingStep(edited, max);
            }
        }

        if (ImGui.collapsingHeader(text("shaft_surface"), ImGuiTreeNodeFlags.DefaultOpen)) {
            int scale = this.stepSlider("##decay_scale", this.tools().sliders().decayScaleStep(edited.decayScale()), PresetSliders.DECAY_SCALE_STEPS,
                    text("decay_scale", edited.decayScale()));
            if (scale >= 0) {
                edited = edited.withDecayScale(this.tools().sliders().decayScale(scale));
            }
            int decayStep = this.tools().edits().decayStep(edited);
            int decay = this.stepSlider("##decay_threshold", decayStep, PresetSliders.FRACTION_STEPS,
                    text("decay_threshold", this.tools().sliders().percent(decayStep)));
            if (decay >= 0) {
                edited = this.tools().edits().withDecayStep(edited, decay);
            }
            int erosionStep = this.tools().edits().erosionStep(edited);
            int erosion = this.stepSlider("##erosion_threshold", erosionStep - 1, PresetSliders.EROSION_STEPS,
                    text("erosion_threshold", this.tools().sliders().percent(erosionStep)));
            if (erosion >= 0) {
                edited = this.tools().edits().withErosionStep(edited, erosion + 1);
            }
        }

        if (ImGui.collapsingHeader(text("clouds"))) {
            int cloud = this.stepSlider("##cloud_height", this.tools().sliders().cloudHeightStep(edited.cloudHeight()), PresetSliders.CLOUD_HEIGHT_STEPS,
                    text("cloud_height", edited.cloudHeight()));
            if (cloud >= 0) {
                edited = edited.withCloudHeight(this.tools().sliders().cloudHeight(cloud));
            }
        }

        if (ImGui.collapsingHeader(text("shaft_variations"))) {
            edited = this.drawVariations(screen, edited);
        }

        if (!edited.equals(settings)) {
            screen.setSettings(edited);
        }
    }

    /** One collapsible node per configured dimension, then the add row. A removal applies after the loop. */
    private AeroliteSettings drawVariations(PreviewScreen screen, AeroliteSettings settings) {
        AeroliteSettings edited = settings;
        ResourceKey<Level> removed = null;
        for (ResourceKey<Level> dimension : this.tools().edits().configuredDimensions(settings)) {
            String id = dimension.location().toString();
            ImGui.pushID(id);
            boolean open = this.drawDimensionNode(screen, dimension);
            if (this.drawRemoveDimension()) {
                removed = dimension;
            } else if (open) {
                edited = this.drawDimensionRows(edited, dimension, id);
            }
            if (open) {
                ImGui.treePop();
            }
            ImGui.popID();
        }
        if (removed != null) {
            edited = this.tools().edits().withoutDimension(edited, removed);
        }
        return this.drawAddDimension(screen, edited);
    }

    /** Draws the radio and the node of one dimension. Returns true when the node is open. */
    private boolean drawDimensionNode(PreviewScreen screen, ResourceKey<Level> dimension) {
        ImGui.beginDisabled(screen.dimensionIndex(dimension) < 0);
        if (ImGui.radioButton("##preview_dimension", dimension.equals(screen.dimensionKey()))) {
            screen.setDimension(dimension);
        }
        ImGui.endDisabled();
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(text("preview.show_dimension"));
        }
        ImGui.sameLine();
        return ImGui.treeNodeEx("##dimension", ImGuiTreeNodeFlags.DefaultOpen, format(this.tools().edits().dimensionName(dimension)));
    }

    /** A small Remove button at the right end of the node line. Returns true when it was pressed. */
    private boolean drawRemoveDimension() {
        String remove = text("remove_dimension");
        float removeWidth = ImGui.calcTextSize(remove).x + 2 * ImGui.getStyle().getFramePaddingX();
        ImGui.sameLine();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + Math.max(0, ImGui.getContentRegionAvailX() - removeWidth));
        return ImGui.smallButton(remove + "##remove_dimension");
    }

    private AeroliteSettings drawDimensionRows(AeroliteSettings settings, ResourceKey<Level> dimension, String id) {
        AeroliteSettings edited = settings;
        List<ShaftVariation> variations = this.tools().edits().variations(edited, dimension);
        for (int index = 0; index < variations.size(); index++) {
            ImGui.pushID(index);
            edited = this.drawVariation(edited, dimension, index, id + "/" + index);
            ImGui.popID();
            ImGui.spacing();
        }
        if (ImGui.button(text("add_variation") + "##add_variation")) {
            edited = this.tools().edits().withAddedVariation(edited, dimension);
        }
        return edited;
    }

    /** A combo of the levels without variations and an Add button. Both are off when every level has variations. */
    private AeroliteSettings drawAddDimension(PreviewScreen screen, AeroliteSettings settings) {
        List<ResourceKey<Level>> candidates = this.tools().edits().addableDimensions(settings, screen.levels());
        if (this.addChoice == null || !candidates.contains(this.addChoice)) {
            this.addChoice = candidates.isEmpty() ? null : candidates.getFirst();
        }
        ImGui.separator();
        String add = text("add_dimension");
        float addWidth = ImGui.calcTextSize(add).x + 2 * ImGui.getStyle().getFramePaddingX();
        ImGui.beginDisabled(this.addChoice == null);
        ImGui.setNextItemWidth(Math.max(1, ImGui.getContentRegionAvailX() - addWidth - ImGui.getStyle().getItemSpacingX()));
        String shown = this.addChoice == null ? text("all_dimensions_configured") : this.tools().edits().dimensionName(this.addChoice).getString();
        if (ImGui.beginCombo("##add_dimension_choice", shown)) {
            for (ResourceKey<Level> candidate : candidates) {
                if (ImGui.selectable(this.tools().edits().dimensionName(candidate).getString() + "##" + candidate.location(), candidate.equals(this.addChoice))) {
                    this.addChoice = candidate;
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        AeroliteSettings edited = settings;
        if (ImGui.button(add + "##add_dimension") && this.addChoice != null) {
            edited = this.tools().edits().withAddedDimension(settings, this.addChoice);
            this.addChoice = null;
        }
        ImGui.endDisabled();
        return edited;
    }

    private AeroliteSettings drawVariation(AeroliteSettings settings, ResourceKey<Level> dimension, int index, String fieldKey) {
        AeroliteSettings edited = settings;
        ShaftVariation variation = this.tools().edits().variations(edited, dimension).get(index);
        int weight = this.stepSlider("##weight", variation.weight(), ShaftVariation.MAX_WEIGHT, text("weight", variation.weight()));
        if (weight >= 0) {
            edited = this.tools().edits().withWeight(edited, dimension, index, weight);
        }
        int biasStep = this.tools().sliders().biasStep(variation.bias());
        int bias = this.stepSlider("##bias", biasStep, PresetSliders.BIAS_STEPS,
                text("maze_bias", this.tools().sliders().biasLabel(biasStep).getString()));
        if (bias >= 0) {
            float chosen = this.tools().sliders().bias(bias);
            edited = this.editVariation(edited, dimension, index, current -> current.withBias(chosen));
        }
        float labelWidth = 0;
        for (String key : BLOCK_LABELS) {
            labelWidth = Math.max(labelWidth, ImGui.calcTextSize(text(key)).x);
        }
        float fieldStart = labelWidth + ImGui.getStyle().getItemSpacingX();
        edited = this.blockField(edited, dimension, index, fieldKey + "/interior", BLOCK_LABELS.get(0), fieldStart, variation.interior(), ShaftVariation::withInterior);
        edited = this.blockField(edited, dimension, index, fieldKey + "/wall", BLOCK_LABELS.get(1), fieldStart, variation.wall(), ShaftVariation::withWall);
        edited = this.blockField(edited, dimension, index, fieldKey + "/path", BLOCK_LABELS.get(2), fieldStart, variation.path(), ShaftVariation::withPath);
        boolean removable = this.tools().edits().canRemove(edited, dimension);
        ImGui.beginDisabled(!removable);
        if (ImGui.button(text("remove_variation") + "##remove")) {
            edited = this.tools().edits().withoutVariation(edited, dimension, index);
        }
        ImGui.endDisabled();
        return edited;
    }

    private interface BlockEdit {
        ShaftVariation apply(ShaftVariation variation, BlockState state);
    }

    /** The label sits left of the field. The fields of one variation start at one column and take the rest of the line. */
    private AeroliteSettings blockField(AeroliteSettings settings, ResourceKey<Level> dimension, int index, String fieldKey, String labelKey,
                                        float fieldStart, BlockState state, BlockEdit edit) {
        ImGuiBlockField field = this.blockFields.computeIfAbsent(fieldKey, key -> new ImGuiBlockField());
        float lineStart = ImGui.getCursorPosX();
        ImGui.alignTextToFramePadding();
        ImGui.text(text(labelKey));
        ImGui.sameLine();
        ImGui.setCursorPosX(lineStart + fieldStart);
        BlockState picked = field.draw("##" + fieldKey, KEY + labelKey, state, this.tools().blocks(), this.tools().icons());
        return picked == null ? settings : this.editVariation(settings, dimension, index, current -> edit.apply(current, picked));
    }

    private AeroliteSettings editVariation(AeroliteSettings settings, ResourceKey<Level> dimension, int index, Function<ShaftVariation, ShaftVariation> edit) {
        ShaftVariation current = this.tools().edits().variations(settings, dimension).get(index);
        return settings.withVariation(dimension, index, edit.apply(current));
    }

    /** The dimension combo and the radios of the variation section share the dimension index of the screen. */
    private void drawPreview(PreviewScreen screen) {
        List<PreviewDimension> dimensions = screen.dimensions();
        if (!dimensions.isEmpty()) {
            ImGui.alignTextToFramePadding();
            ImGui.text(text("preview.dimension"));
            ImGui.sameLine();
            ImGui.setNextItemWidth(-1);
            if (ImGui.beginCombo("##dimension", dimensions.get(screen.dimensionIndex()).name().getString())) {
                for (int index = 0; index < dimensions.size(); index++) {
                    if (ImGui.selectable(dimensions.get(index).name().getString(), index == screen.dimensionIndex())) {
                        screen.setDimensionIndex(index);
                    }
                }
                ImGui.endCombo();
            }
        }

        String reseed = text("preview.reseed");
        if (!screen.seedText().equals(this.seedShown)) {
            this.seedField.set(screen.seedText());
            this.seedShown = screen.seedText();
        }
        ImGui.alignTextToFramePadding();
        ImGui.text(text("preview.seed"));
        ImGui.sameLine();
        ImGui.setNextItemWidth(-(ImGui.calcTextSizeX(reseed) + 2 * ImGui.getStyle().getFramePaddingX() + ImGui.getStyle().getItemSpacingX()));
        if (ImGui.inputText("##seed", this.seedField)) {
            this.seedShown = this.seedField.get();
            screen.setSeedText(this.seedShown);
        }
        ImGui.sameLine();
        if (ImGui.button(reseed)) {
            screen.reseed();
        }

        int radius = this.stepSlider("##radius", screen.radius() - PreviewScreen.MIN_RADIUS, PreviewScreen.MAX_RADIUS - PreviewScreen.MIN_RADIUS,
                text("preview.radius", screen.radius()));
        if (radius >= 0) {
            screen.setRadius(radius + PreviewScreen.MIN_RADIUS);
        }

        PreviewBuilder.Progress progress = screen.progress();
        String label = switch (progress.phase()) {
            case WAITING -> text("preview.waiting");
            case PAINTING -> text("preview.painting", progress.done(), progress.total());
            case BAKING -> text("preview.baking", progress.done(), progress.total());
            case DONE -> text("preview.done", progress.total());
        };
        float fraction = progress.phase() == PreviewBuilder.Phase.DONE ? 1 : progress.fraction();
        ImGui.progressBar(fraction, -1, 0, label);
        ResourceKey<Level> previewed = screen.dimensionKey();
        if (previewed != null && screen.settings().variations(previewed).isEmpty()) {
            ImGui.textWrapped(text("preview.no_shafts"));
        }
    }

}
