package us.drullk.aerolite.client.preview.imgui;

import foundry.imgui.api.ImGuiMCEvents;
import foundry.veil.api.client.render.VeilRenderSystem;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.ImVec4;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.AeroliteClientConfig;
import us.drullk.aerolite.client.PresetEdits;
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
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public final class ImGuiPreviewPanel implements PreviewPanel {

    private static final String KEY = "createWorld.customize.aerolite.";
    private static final float SPLITTER_WIDTH = 6;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking;

    private final Map<String, ImGuiBlockField> blockFields = new HashMap<>();
    private final int[] windowWidth = new int[1];
    private final int[] windowHeight = new int[1];
    private final int[] framebufferWidth = new int[1];
    private final int[] framebufferHeight = new int[1];
    private final int[] slider = new int[1];
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
            if (ImGui.checkbox(text("shafts_in_unlisted_dimensions") + "##unlisted", edited.shaftsInUnlistedDimensions())) {
                edited = edited.withShaftsInUnlistedDimensions(!edited.shaftsInUnlistedDimensions());
            }
            for (ResourceKey<Level> dimension : PresetEdits.DIMENSIONS) {
                edited = this.drawDimension(edited, dimension);
            }
        }

        if (!edited.equals(settings)) {
            screen.setSettings(edited);
        }

        if (ImGui.collapsingHeader(text("preview.section"), ImGuiTreeNodeFlags.DefaultOpen)) {
            this.drawPreview(screen);
        }
    }

    private AeroliteSettings drawDimension(AeroliteSettings settings, ResourceKey<Level> dimension) {
        String path = dimension.location().getPath();
        ImGui.separatorText(text("dimension." + path));
        AeroliteSettings edited = settings;
        List<ShaftVariation> variations = this.tools().edits().variations(edited, dimension);
        for (int index = 0; index < variations.size(); index++) {
            ImGui.pushID(path + "/" + index);
            edited = this.drawVariation(edited, dimension, index, path + "/" + index);
            ImGui.popID();
            ImGui.spacing();
        }
        if (ImGui.button(text("add_variation") + "##add_" + path)) {
            edited = this.tools().edits().withAddedVariation(edited, dimension);
        }
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
        edited = this.blockField(edited, dimension, index, fieldKey + "/interior", "interior_block", variation.interior(), ShaftVariation::withInterior);
        edited = this.blockField(edited, dimension, index, fieldKey + "/wall", "wall_block", variation.wall(), ShaftVariation::withWall);
        edited = this.blockField(edited, dimension, index, fieldKey + "/path", "path_block", variation.path(), ShaftVariation::withPath);
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

    private AeroliteSettings blockField(AeroliteSettings settings, ResourceKey<Level> dimension, int index, String fieldKey, String labelKey,
                                        BlockState state, BlockEdit edit) {
        ImGuiBlockField field = this.blockFields.computeIfAbsent(fieldKey, key -> new ImGuiBlockField());
        BlockState picked = field.draw("##" + fieldKey, KEY + labelKey, state, this.tools().blocks(), this.tools().icons());
        return picked == null ? settings : this.editVariation(settings, dimension, index, current -> edit.apply(current, picked));
    }

    private AeroliteSettings editVariation(AeroliteSettings settings, ResourceKey<Level> dimension, int index, Function<ShaftVariation, ShaftVariation> edit) {
        ShaftVariation current = this.tools().edits().variations(settings, dimension).get(index);
        return settings.withVariation(dimension, index, edit.apply(current));
    }

    private void drawPreview(PreviewScreen screen) {
        List<PreviewDimension> dimensions = screen.dimensions();
        if (!dimensions.isEmpty()) {
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

        ImGui.alignTextToFramePadding();
        ImGui.text(text("preview.seed", screen.seed()));
        ImGui.sameLine();
        if (ImGui.button(text("preview.reseed"))) {
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
        if (screen.settings().variations(dimensions.isEmpty() ? Level.OVERWORLD : dimensions.get(screen.dimensionIndex()).key()).isEmpty()) {
            ImGui.textWrapped(text("preview.no_shafts"));
        }
    }

}
