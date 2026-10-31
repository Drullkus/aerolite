package us.drullk.aerolite.client.preview.imgui;

import com.mojang.serialization.DataResult;
import foundry.imgui.api.ImGuiMCEvents;
import foundry.veil.api.client.render.VeilRenderSystem;
import imgui.ImGui;
import imgui.ImGuiStyle;
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
import us.drullk.aerolite.client.PresetEdits;
import us.drullk.aerolite.client.PresetSliders;
import us.drullk.aerolite.client.PresetTools;
import us.drullk.aerolite.client.preview.PreviewBuilder;
import us.drullk.aerolite.client.preview.PreviewDimension;
import us.drullk.aerolite.client.preview.PreviewPanel;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.WeightedVariation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The ImGui panel on the right edge of the preview screen. It edits the settings with the steps and rules of the vanilla screen.
 * Only the bean method of {@link PreviewPanel} creates it, and only when imgui-mc is present.
 * The Preview section comes first. One collapsing header per configured dimension follows, then the add row.
 * Each dimension header holds the groups Shaft Placement and Shaft Variations.
 * A radio left of each dimension name and the dimension combo of the Preview section select the previewed dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_bf29e15f-47bb-42fe-94f0-29ae22f43f5a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_21-56_ad766c27-abc7-4f8b-ae6a-53b92f85435c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 */
public final class ImGuiPreviewPanel implements PreviewPanel {

    private static final String KEY = "createWorld.customize.aerolite.";
    private static final List<String> BLOCK_LABELS = List.of("wall_block", "path_block", "interior_block");
    private static final float SPLITTER_WIDTH = 6;
    private static final int SEED_CAPACITY = 256;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking;
    /** The font scale of the panel, its popups and the view gizmo. This is the one place to dial the panel scale. */
    private static final float FONT_SCALE = 1.5F;
    /** The size of the panel text before the font scale, in ImGui units. */
    private static final float FONT_SIZE = 13F;

    private final Map<String, ImGuiBlockField> blockFields = new HashMap<>();
    private final int[] windowWidth = new int[1];
    private final int[] windowHeight = new int[1];
    private final int[] framebufferWidth = new int[1];
    private final int[] framebufferHeight = new int[1];
    private final int[] slider = new int[1];
    private final ImGuiViewGizmo gizmo = new ImGuiViewGizmo();
    private final ImGuiPanelFont font = new ImGuiPanelFont(FONT_SIZE * FONT_SCALE);
    /** The ImGui default style. It exists after the first drawn frame, when the ImGui natives are loaded. */
    @Nullable
    private ImGuiStyle defaults;
    private final ImString seedField = new ImString(SEED_CAPACITY);
    /** The seed text that the seed field last took from the screen or gave to it. */
    @Nullable
    private String seedShown;
    /** The dimension that the add combo shows. */
    @Nullable
    private ResourceKey<Level> addChoice;
    /** The variation list that the list combo shows. Empty is No prefab. */
    private Optional<ResourceKey<VariationList>> addList = Optional.empty();
    /** The dimension that {@link #addList} took its preselection from. */
    @Nullable
    private ResourceKey<Level> addListFor;
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

    /** Creates the panel and adds its font and draw hooks to the imgui-mc events. */
    public static ImGuiPreviewPanel create() {
        ImGuiPreviewPanel panel = new ImGuiPreviewPanel();
        ImGuiMCEvents.INSTANCE.onRegisterImGuiFonts((atlas, defaultFont, fontScale) -> panel.font.register(atlas));
        ImGuiMCEvents.INSTANCE.preRenderImGuiEvent(panel::drawPre);
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(panel::fixFramebufferScale);
        return panel;
    }

    @Override
    public boolean available() {
        return VeilRenderSystem.hasImGui();
    }

    @Override
    public boolean wantsTextInput() {
        // The ImGui context is current only inside the imgui-mc frame hooks.
        try (foundry.imgui.api.ImGuiMC.ActiveContext context = foundry.imgui.api.ImGuiMC.withImGui()) {
            return context.io().getWantTextInput();
        }
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
        this.font.matchScale(ImGui.getIO().getDisplayFramebufferScaleX());
        ImGuiStyle style = ImGui.getStyle();
        this.resetStyle(style);
        // imgui-mc sets the main font scale from the GUI scale before each frame.
        float sharedScale = style.getFontScaleMain();
        style.setFontScaleMain(FONT_SCALE);
        // The push applies the new scale.
        ImGui.pushFont(this.font.get(), FONT_SIZE);
        try {
            this.drawScreen(screen);
        } finally {
            ImGui.popFont();
            style.setFontScaleMain(sharedScale);
        }
    }

    /**
     * Gives the style the ImGui default colors and sizes. Veil writes its own style in its pre-render and post-render hooks,
     * so the panel resets the style each frame. ImGui bakes the colors at submission, so the Veil windows keep the Veil style.
     */
    private void resetStyle(ImGuiStyle style) {
        if (this.defaults == null) {
            this.defaults = new ImGuiStyle();
        }
        ImGuiStyle defaults = this.defaults;
        ImGui.styleColorsDark(style);
        style.setWindowPadding(defaults.getWindowPaddingX(), defaults.getWindowPaddingY());
        style.setFramePadding(defaults.getFramePaddingX(), defaults.getFramePaddingY());
        style.setCellPadding(defaults.getCellPaddingX(), defaults.getCellPaddingY());
        style.setItemSpacing(defaults.getItemSpacingX(), defaults.getItemSpacingY());
        style.setItemInnerSpacing(defaults.getItemInnerSpacingX(), defaults.getItemInnerSpacingY());
        style.setTouchExtraPadding(defaults.getTouchExtraPaddingX(), defaults.getTouchExtraPaddingY());
        style.setIndentSpacing(defaults.getIndentSpacing());
        style.setScrollbarSize(defaults.getScrollbarSize());
        style.setGrabMinSize(defaults.getGrabMinSize());
        style.setWindowBorderSize(defaults.getWindowBorderSize());
        style.setChildBorderSize(defaults.getChildBorderSize());
        style.setPopupBorderSize(defaults.getPopupBorderSize());
        style.setFrameBorderSize(defaults.getFrameBorderSize());
        style.setTabBorderSize(defaults.getTabBorderSize());
        style.setWindowRounding(defaults.getWindowRounding());
        style.setChildRounding(defaults.getChildRounding());
        style.setFrameRounding(defaults.getFrameRounding());
        style.setPopupRounding(defaults.getPopupRounding());
        style.setScrollbarRounding(defaults.getScrollbarRounding());
        style.setGrabRounding(defaults.getGrabRounding());
        style.setLogSliderDeadzone(defaults.getLogSliderDeadzone());
        style.setTabRounding(defaults.getTabRounding());
        style.setWindowMenuButtonPosition(defaults.getWindowMenuButtonPosition());
    }

    private void drawScreen(PreviewScreen screen) {
        this.tools = screen.tools();
        if (screen != this.drawn) {
            this.drawn = screen;
            this.blockFields.clear();
            this.addChoice = null;
            this.addListFor = null;
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
        try {
            if (open) {
                this.drawSplitter(screen, viewport.getPosX() + windowWidth, windowHeight);
                ImGui.setCursorPos(SPLITTER_WIDTH, 0);
                boolean child = ImGui.beginChild("##aerolite_preview_content", this.width - SPLITTER_WIDTH, windowHeight, ImGuiChildFlags.AlwaysUseWindowPadding);
                try {
                    if (child) {
                        this.drawContent(screen);
                    }
                } finally {
                    ImGui.endChild();
                }
            }
        } finally {
            ImGui.end();
        }
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

    /** The Preview section, one header per configured dimension, then the add row. */
    private void drawContent(PreviewScreen screen) {
        if (ImGui.collapsingHeader(text("preview.section"), ImGuiTreeNodeFlags.DefaultOpen)) {
            this.drawPreview(screen);
        }

        AeroliteSettings settings = screen.settings();
        AeroliteSettings edited = this.drawVariations(screen, settings);
        if (!edited.equals(settings)) {
            screen.setSettings(edited);
        }
    }

    /** One collapsing header per configured dimension, then the add row. A removal applies after the loop. */
    private AeroliteSettings drawVariations(PreviewScreen screen, AeroliteSettings settings) {
        AeroliteSettings edited = settings;
        ResourceKey<Level> removed = null;
        for (ResourceKey<Level> dimension : this.tools().edits().configuredDimensions(settings)) {
            String id = dimension.location().toString();
            ImGui.pushID(id);
            try {
                float right = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX();
                boolean open = this.drawDimensionNode(screen, dimension);
                if (this.drawRemoveDimension(right)) {
                    removed = dimension;
                } else if (open) {
                    edited = this.drawDimensionRows(edited, dimension, id);
                }
            } finally {
                ImGui.popID();
            }
        }
        if (removed != null) {
            edited = this.tools().edits().withoutDimension(edited, removed);
        }
        return this.drawAddDimension(screen, edited);
    }

    /**
     * Draws the radio and the collapsing header of one dimension. Returns true when the header is open.
     * The header id ignores the name, so the open state follows the dimension through every rebuild.
     */
    private boolean drawDimensionNode(PreviewScreen screen, ResourceKey<Level> dimension) {
        ImGui.beginDisabled(screen.dimensionIndex(dimension) < 0);
        try {
            if (ImGui.radioButton("##preview_dimension", dimension.equals(screen.dimensionKey()))) {
                screen.setDimension(dimension);
            }
        } finally {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(text("preview.show_dimension"));
        }
        ImGui.sameLine();
        return ImGui.collapsingHeader(format(this.tools().edits().dimensionName(dimension)) + "###dimension",
                ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap);
    }

    /** A small Remove button at the right end of the header line. Returns true when it was pressed. */
    private boolean drawRemoveDimension(float right) {
        String remove = text("remove_dimension");
        float removeWidth = ImGui.calcTextSize(remove).x + 2 * ImGui.getStyle().getFramePaddingX();
        ImGui.sameLine();
        ImGui.setCursorPosX(right - removeWidth - ImGui.getStyle().getFramePaddingX());
        return ImGui.smallButton(remove + "##remove_dimension");
    }

    /**
     * The groups Shaft Placement and Shaft Variations of one dimension. Both start open.
     * Each row edit keeps the variation count. A removal applies after the loop, so every row reads its own index.
     */
    private AeroliteSettings drawDimensionRows(AeroliteSettings settings, ResourceKey<Level> dimension, String id) {
        AeroliteSettings edited = settings;
        if (ImGui.treeNodeEx("##shaft_placement", ImGuiTreeNodeFlags.DefaultOpen, text("shaft_placement"))) {
            try {
                edited = this.drawPlacement(edited, dimension);
            } finally {
                ImGui.treePop();
            }
        }
        if (ImGui.treeNodeEx("##shaft_variations", ImGuiTreeNodeFlags.DefaultOpen, text("shaft_variations"))) {
            try {
                int count = this.tools().edits().variations(edited, dimension).size();
                int removed = -1;
                for (int index = 0; index < count; index++) {
                    ImGui.pushID(index);
                    try {
                        edited = this.drawVariation(edited, dimension, index, id + "/" + index);
                        ImGui.sameLine();
                        if (this.drawRemoveVariation(edited, dimension)) {
                            removed = index;
                        }
                    } finally {
                        ImGui.popID();
                    }
                    ImGui.spacing();
                }
                if (removed >= 0) {
                    edited = this.tools().edits().withoutVariation(edited, dimension, removed);
                }
                if (ImGui.button(text("add_variation") + "##add_variation")) {
                    edited = this.tools().edits().withAddedVariation(edited, dimension);
                }
            } finally {
                ImGui.treePop();
            }
        }
        return edited;
    }

    /** The Remove button of one variation. It is off for the last variation. Returns true when it was pressed. */
    private boolean drawRemoveVariation(AeroliteSettings settings, ResourceKey<Level> dimension) {
        ImGui.beginDisabled(!this.tools().edits().canRemove(settings, dimension));
        boolean pressed = ImGui.button(text("remove_variation") + "##remove");
        ImGui.endDisabled();
        return pressed;
    }

    /** The column chance and the two spacing sliders. */
    private AeroliteSettings drawPlacement(AeroliteSettings settings, ResourceKey<Level> dimension) {
        PresetSliders sliders = this.tools().sliders();
        ShaftPlacement placement = settings.placement(dimension);
        int chanceStep = sliders.fractionStep(placement.columnChance());
        int column = this.stepSlider("##column_chance", chanceStep, PresetSliders.FRACTION_STEPS, text("column_chance", sliders.percent(chanceStep)));
        if (column >= 0) {
            placement = placement.withColumnChance(sliders.fraction(column));
        }
        int min = this.stepSlider("##min_spacing", sliders.spacingStep(placement.minShaftSpacing()), PresetSliders.SPACING_STEPS,
                text("min_shaft_spacing", placement.minShaftSpacing()));
        if (min >= 0) {
            placement = this.tools().edits().withMinSpacingStep(placement, min);
        }
        int max = this.stepSlider("##max_spacing", sliders.spacingStep(placement.maxShaftSpacing()), PresetSliders.SPACING_STEPS,
                text("max_shaft_spacing", placement.maxShaftSpacing()));
        if (max >= 0) {
            placement = this.tools().edits().withMaxSpacingStep(placement, max);
        }
        return settings.withPlacement(dimension, placement);
    }

    /**
     * A combo of the dimensions without settings, a combo of No prefab and the variation lists, and an Add button.
     * The list combo preselects the own list of the chosen dimension. All three are off when every dimension has settings.
     * The tooltip of the list combo names the chosen dimension.
     */
    private AeroliteSettings drawAddDimension(PreviewScreen screen, AeroliteSettings settings) {
        PresetEdits edits = this.tools().edits();
        List<ResourceKey<Level>> candidates = edits.addableDimensions(settings, screen.levels());
        if (this.addChoice == null || !candidates.contains(this.addChoice)) {
            this.addChoice = candidates.isEmpty() ? null : candidates.getFirst();
        }
        if (this.addChoice != null && !this.addChoice.equals(this.addListFor)) {
            this.addListFor = this.addChoice;
            this.addList = edits.ownList(this.addChoice);
        }
        ImGui.separator();
        String add = text("add_dimension");
        float addWidth = ImGui.calcTextSize(add).x + 2 * ImGui.getStyle().getFramePaddingX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float comboWidth = Math.max(1, (ImGui.getContentRegionAvailX() - addWidth - 2 * spacing) / 2);
        ImGui.beginDisabled(this.addChoice == null);
        AeroliteSettings edited = settings;
        try {
            ImGui.setNextItemWidth(comboWidth);
            String shown = this.addChoice == null ? text("all_dimensions_configured") : edits.dimensionName(this.addChoice).getString();
            if (ImGui.beginCombo("##add_dimension_choice", shown)) {
                try {
                    for (ResourceKey<Level> candidate : candidates) {
                        if (ImGui.selectable(edits.dimensionName(candidate).getString() + "##" + candidate.location(), candidate.equals(this.addChoice))) {
                            this.addChoice = candidate;
                        }
                    }
                } finally {
                    ImGui.endCombo();
                }
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(comboWidth);
            if (ImGui.beginCombo("##add_list_choice", edits.listName(this.addList).getString())) {
                try {
                    for (Optional<ResourceKey<VariationList>> choice : edits.listChoices()) {
                        String id = choice.map(key -> key.location().toString()).orElse("no_prefab");
                        if (ImGui.selectable(edits.listName(choice).getString() + "##" + id, choice.equals(this.addList))) {
                            this.addList = choice;
                        }
                    }
                } finally {
                    ImGui.endCombo();
                }
            }
            if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(edits.listTooltip(this.addChoice).getString());
            }
            ImGui.sameLine();
            if (ImGui.button(add + "##add_dimension") && this.addChoice != null) {
                edited = edits.withAddedDimension(settings, this.addChoice, this.addList);
                this.addChoice = null;
                this.addListFor = null;
            }
        } finally {
            ImGui.endDisabled();
        }
        return edited;
    }

    /** The sliders in the sequence of the menu, then the block fields and the buttons Export and Import. */
    private AeroliteSettings drawVariation(AeroliteSettings settings, ResourceKey<Level> dimension, int index, String fieldKey) {
        PresetSliders sliders = this.tools().sliders();
        AeroliteSettings edited = settings;
        ShaftVariation variation = this.tools().edits().variations(edited, dimension).get(index);
        int currentWeight = this.tools().edits().weight(edited, dimension, index);
        int weight = this.stepSlider("##weight", currentWeight, WeightedVariation.MAX_WEIGHT, text("weight", currentWeight));
        if (weight >= 0) {
            edited = this.tools().edits().withWeight(edited, dimension, index, weight);
        }
        int biasStep = sliders.biasStep(variation.bias());
        int bias = this.stepSlider("##bias", biasStep, PresetSliders.BIAS_STEPS, text("maze_bias", sliders.biasLabel(biasStep).getString()));
        if (bias >= 0) {
            float chosen = sliders.bias(bias);
            edited = this.editVariation(edited, dimension, index, current -> current.withBias(chosen));
        }
        int scale = this.stepSlider("##decay_scale", sliders.decayScaleStep(variation.decayScale()), PresetSliders.DECAY_SCALE_STEPS,
                text("decay_scale", variation.decayScale()));
        if (scale >= 0) {
            int chosen = sliders.decayScale(scale);
            edited = this.editVariation(edited, dimension, index, current -> current.withDecayScale(chosen));
        }
        int decayStep = this.tools().edits().decayStep(variation);
        int decay = this.stepSlider("##decay_threshold", decayStep, PresetSliders.FRACTION_STEPS, text("decay_threshold", sliders.percent(decayStep)));
        if (decay >= 0) {
            edited = this.editVariation(edited, dimension, index, current -> this.tools().edits().withDecayStep(current, decay));
        }
        int erosionStep = this.tools().edits().erosionStep(this.tools().edits().variations(edited, dimension).get(index));
        int erosion = this.stepSlider("##erosion_threshold", erosionStep - 1, PresetSliders.EROSION_STEPS, text("erosion_threshold", sliders.percent(erosionStep)));
        if (erosion >= 0) {
            edited = this.editVariation(edited, dimension, index, current -> this.tools().edits().withErosionStep(current, erosion + 1));
        }
        float labelWidth = 0;
        for (String key : BLOCK_LABELS) {
            labelWidth = Math.max(labelWidth, ImGui.calcTextSize(text(key)).x);
        }
        float fieldStart = labelWidth + ImGui.getStyle().getItemSpacingX();
        edited = this.blockField(edited, dimension, index, fieldKey + "/wall", BLOCK_LABELS.get(0), fieldStart, variation.wall(), ShaftVariation::withWall);
        edited = this.blockField(edited, dimension, index, fieldKey + "/path", BLOCK_LABELS.get(1), fieldStart, variation.path(), ShaftVariation::withPath);
        edited = this.blockField(edited, dimension, index, fieldKey + "/interior", BLOCK_LABELS.get(2), fieldStart, variation.interior(), ShaftVariation::withInterior);
        return this.drawVariationClipboard(edited, dimension, index);
    }

    /** Export and Import of one variation. A good import replaces the variation and keeps its weight. */
    private AeroliteSettings drawVariationClipboard(AeroliteSettings settings, ResourceKey<Level> dimension, int index) {
        ShaftVariation current = this.tools().edits().variations(settings, dimension).get(index);
        if (ImGui.button(text("copy_variation") + "##export")) {
            this.tools().json().copyVariation(current);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text("copy_variation_tooltip"));
        }
        ImGui.sameLine();
        AeroliteSettings edited = settings;
        if (ImGui.button(text("paste_variation") + "##import")) {
            DataResult<ShaftVariation> imported = this.tools().json().pasteVariation();
            Optional<ShaftVariation> parsed = imported.result();
            if (parsed.isPresent()) {
                edited = settings.withVariation(dimension, index, parsed.get());
            } else {
                imported.ifError(error -> this.tools().json().showImportFailed(error.message()));
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text("paste_variation_tooltip"));
        }
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
                try {
                    for (int index = 0; index < dimensions.size(); index++) {
                        if (ImGui.selectable(dimensions.get(index).name().getString(), index == screen.dimensionIndex())) {
                            screen.setDimensionIndex(index);
                        }
                    }
                } finally {
                    ImGui.endCombo();
                }
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

        if (ImGui.button(text("copy") + "##copy")) {
            this.tools().json().copy(screen.settings(), this.tools().edits().registries());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text("copy_tooltip"));
        }
        ImGui.sameLine();
        if (ImGui.button(text("paste") + "##paste")) {
            this.tools().json().paste(this.tools().edits().registries())
                    .ifSuccess(screen::setSettings)
                    .ifError(error -> this.tools().json().showPasteFailed(error.message()));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text("paste_tooltip"));
        }
        ImGui.sameLine();
        if (ImGui.button(text("defaults") + "##defaults")) {
            PresetEdits edits = this.tools().edits();
            screen.setSettings(edits.withDefaults(screen.settings(), screen.levels()));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text("defaults_tooltip"));
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

        // The screen holds the text of an active center field between frames.
        String resetCenter = text("preview.reset_center");
        ImGui.alignTextToFramePadding();
        ImGui.text(text("preview.center"));
        float resetWidth = ImGui.calcTextSizeX(resetCenter) + 2 * ImGui.getStyle().getFramePaddingX();
        ImGui.sameLine();
        float centerWidth = Math.max(1, (ImGui.getContentRegionAvailX() - resetWidth - 2 * ImGui.getStyle().getItemSpacingX()) / 2);
        for (net.minecraft.core.Direction.Axis axis : List.of(net.minecraft.core.Direction.Axis.X, net.minecraft.core.Direction.Axis.Z)) {
            int shown = (int) Math.floor(axis == net.minecraft.core.Direction.Axis.X ? screen.centerX() : screen.centerZ());
            String edit = screen.centerEdit(axis);
            ImString field = new ImString(edit != null ? edit : Integer.toString(shown), SEED_CAPACITY);
            ImGui.setNextItemWidth(centerWidth);
            boolean entered = ImGui.inputText("##center_" + axis.getName(), field,
                    imgui.flag.ImGuiInputTextFlags.CharsDecimal | imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
            boolean apply = entered || ImGui.isItemDeactivatedAfterEdit();
            screen.setCenterEdit(axis, ImGui.isItemActive() && !entered ? field.get() : null);
            if (apply) {
                try {
                    int value = Integer.parseInt(field.get().trim());
                    if (value != shown) {
                        screen.setCenter(axis == net.minecraft.core.Direction.Axis.X ? value : screen.centerX(),
                                axis == net.minecraft.core.Direction.Axis.Z ? value : screen.centerZ());
                    }
                } catch (NumberFormatException exception) {
                    // A text that is not a whole number keeps the center.
                }
            }
            ImGui.sameLine();
        }
        if (ImGui.button(resetCenter)) {
            screen.resetCenter();
        }
    }

}
