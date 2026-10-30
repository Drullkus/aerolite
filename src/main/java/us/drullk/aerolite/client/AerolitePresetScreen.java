package us.drullk.aerolite.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftVariation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

/**
 * The customize screen of the preset in the world creation menu. It edits the settings of every Aerolite dimension.
 * An add or a remove of a variation or a dimension rebuilds the list entries. A slider or a block field edits its value in place.
 * Each configured dimension has a header with a Remove button. The add row after the last dimension adds one more.
 * The Preview button shows only when ImGui loaded. The preview edits a copy of the settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_00-41_1fb7a266-bfdf-4ab5-8b4a-1652dc840cb2.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_85da4419-5789-4ddd-8712-bcdaaca5bcce.md}.
 */
public class AerolitePresetScreen extends Screen {

    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private final PresetTools tools;
    private final PresetSliders sliders;
    private final PresetEdits edits;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private SettingsList list;
    private AeroliteSettings settings;
    private final List<BlockField> blockFields = new ArrayList<>();
    /** True when an add or a remove waits for the list rebuild of the next tick. */
    private boolean rebuildPending;
    @Nullable
    private DimensionDropdown addDropdown;
    /** The candidate that the add dropdown shows after a rebuild. */
    @Nullable
    private ResourceKey<Level> addChoice;

    public AerolitePresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetTools tools) {
        super(Component.translatable("createWorld.customize.aerolite.title"));
        this.parent = parent;
        this.context = context;
        this.tools = tools;
        this.sliders = tools.sliders();
        this.edits = tools.edits();
        this.settings = context.selectedDimensions().dimensions().values().stream()
                .map(LevelStem::generator)
                .filter(AeroliteChunkGenerator.class::isInstance)
                .map(generator -> ((AeroliteChunkGenerator) generator).aeroliteSettings())
                .findFirst()
                .orElse(AeroliteSettings.DEFAULT);
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        SettingsList list = this.layout.addToContents(new SettingsList(this.minecraft, this.width, this.layout));
        this.list = list;
        this.populate(list);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            AeroliteSettings chosen = this.settings;
            this.parent.getUiState().updateDimensions((registries, dimensions) -> this.withSettings(dimensions, chosen));
            this.onClose();
        }).build());
        if (this.tools.panel().available()) {
            footer.addChild(Button.builder(Component.translatable("createWorld.customize.aerolite.preview"), button -> this.openPreview()).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    /** Opens the preview with a copy of the settings. Done in the preview sends the settings and the seed text back. */
    private void openPreview() {
        new PreviewScreen(this, this.context, this.tools, this.settings, this.parent.getUiState().getSeed(), (chosen, seedText) -> {
            this.settings = chosen;
            this.parent.getUiState().setSeed(seedText);
            this.rebuildPending = true;
        }).open();
    }

    /** Adds every section of the settings to the list. */
    private void populate(SettingsList list) {
        this.blockFields.clear();
        list.addHeader(Component.translatable("createWorld.customize.aerolite.shaft_placement"));
        list.addRow(new StepSlider(PresetSliders.FRACTION_STEPS, this.sliders.fractionStep(this.settings.columnChance()),
                step -> Component.translatable("createWorld.customize.aerolite.column_chance", this.sliders.percent(step)),
                step -> this.settings = this.settings.withColumnChance(this.sliders.fraction(step))));

        StepSlider[] spacing = new StepSlider[2];
        spacing[0] = new StepSlider(PresetSliders.SPACING_STEPS, this.sliders.spacingStep(this.settings.minShaftSpacing()),
                step -> Component.translatable("createWorld.customize.aerolite.min_shaft_spacing", this.sliders.spacing(step)),
                step -> {
                    this.settings = this.edits.withMinSpacingStep(this.settings, step);
                    spacing[1].setStep(this.sliders.spacingStep(this.settings.maxShaftSpacing()));
                });
        spacing[1] = new StepSlider(PresetSliders.SPACING_STEPS, this.sliders.spacingStep(this.settings.maxShaftSpacing()),
                step -> Component.translatable("createWorld.customize.aerolite.max_shaft_spacing", this.sliders.spacing(step)),
                step -> {
                    this.settings = this.edits.withMaxSpacingStep(this.settings, step);
                    spacing[0].setStep(this.sliders.spacingStep(this.settings.minShaftSpacing()));
                });
        list.addRow(spacing);

        list.addHeader(Component.translatable("createWorld.customize.aerolite.shaft_surface"));
        list.addRow(new StepSlider(PresetSliders.DECAY_SCALE_STEPS, this.sliders.decayScaleStep(this.settings.decayScale()),
                step -> Component.translatable("createWorld.customize.aerolite.decay_scale", this.sliders.decayScale(step)),
                step -> this.settings = this.settings.withDecayScale(this.sliders.decayScale(step))));
        StepSlider[] thresholds = new StepSlider[2];
        thresholds[0] = new StepSlider(PresetSliders.FRACTION_STEPS, this.edits.decayStep(this.settings),
                step -> Component.translatable("createWorld.customize.aerolite.decay_threshold", this.sliders.percent(step)),
                step -> {
                    this.settings = this.edits.withDecayStep(this.settings, step);
                    thresholds[0].setStep(this.edits.decayStep(this.settings));
                    thresholds[1].setStep(this.edits.erosionStep(this.settings) - 1);
                });
        thresholds[1] = new StepSlider(PresetSliders.EROSION_STEPS, this.edits.erosionStep(this.settings) - 1,
                step -> Component.translatable("createWorld.customize.aerolite.erosion_threshold", this.sliders.percent(step + 1)),
                step -> {
                    this.settings = this.edits.withErosionStep(this.settings, step + 1);
                    thresholds[0].setStep(this.edits.decayStep(this.settings));
                });
        list.addRow(thresholds);

        list.addHeader(Component.translatable("createWorld.customize.aerolite.clouds"));
        list.addRow(new StepSlider(PresetSliders.CLOUD_HEIGHT_STEPS, this.sliders.cloudHeightStep(this.settings.cloudHeight()),
                step -> Component.translatable("createWorld.customize.aerolite.cloud_height", this.sliders.cloudHeight(step)),
                step -> this.settings = this.settings.withCloudHeight(this.sliders.cloudHeight(step))));

        list.addHeader(Component.translatable("createWorld.customize.aerolite.shaft_variations"));
        for (ResourceKey<Level> dimension : this.edits.configuredDimensions(this.settings)) {
            this.addDimension(list, dimension);
        }
        this.addDimensionRow(list);
    }

    /** Adds the header with its Remove button, the variation rows and the Add button of one dimension. */
    private void addDimension(SettingsList list, ResourceKey<Level> dimension) {
        Component name = this.edits.dimensionName(dimension);
        list.addHeader(name, Button.builder(Component.literal("\u2212"), button -> {
            this.settings = this.edits.withoutDimension(this.settings, dimension);
            this.addChoice = dimension;
            this.rebuildPending = true;
        }).createNarration(message -> CommonComponents.joinForNarration(Component.translatable("createWorld.customize.aerolite.remove_dimension"), name)).build());
        List<ShaftVariation> variations = this.edits.variations(this.settings, dimension);
        for (int index = 0; index < variations.size(); index++) {
            this.addVariation(list, dimension, index, variations.get(index));
        }
        list.addRow(Button.builder(Component.translatable("createWorld.customize.aerolite.add_variation"), button -> {
            this.settings = this.edits.withAddedVariation(this.settings, dimension);
            this.rebuildPending = true;
        }).build());
    }

    /** Adds the dropdown of the dimensions that the world holds without variations, and the Add button. */
    private void addDimensionRow(SettingsList list) {
        List<ResourceKey<Level>> candidates = this.edits.addableDimensions(this.settings,
                this.context.selectedDimensions().dimensions().keySet().stream().map(Registries::levelStemToLevel).toList());
        DimensionDropdown dropdown = new DimensionDropdown(candidates, this.addChoice, this.edits::dimensionName,
                Component.translatable("createWorld.customize.aerolite.all_dimensions_configured"));
        this.addDropdown = dropdown;
        Button add = Button.builder(Component.translatable("createWorld.customize.aerolite.add_dimension"), button -> {
            ResourceKey<Level> chosen = dropdown.chosen();
            if (chosen != null) {
                this.settings = this.edits.withAddedDimension(this.settings, chosen);
                this.addChoice = null;
                this.rebuildPending = true;
            }
        }).build();
        add.active = dropdown.chosen() != null;
        list.addRow(dropdown, add);
    }

    /** Adds the slider row, the label row, the block row and the Remove row of one variation. */
    private void addVariation(SettingsList list, ResourceKey<Level> dimension, int index, ShaftVariation variation) {
        StepSlider[] weight = new StepSlider[1];
        weight[0] = new StepSlider(ShaftVariation.MAX_WEIGHT, variation.weight(),
                step -> Component.translatable("createWorld.customize.aerolite.weight", step),
                step -> {
                    this.settings = this.edits.withWeight(this.settings, dimension, index, step);
                    weight[0].setStep(this.edits.variations(this.settings, dimension).get(index).weight());
                });
        StepSlider bias = new StepSlider(PresetSliders.BIAS_STEPS, this.sliders.biasStep(variation.bias()),
                step -> Component.translatable("createWorld.customize.aerolite.maze_bias", this.sliders.biasLabel(step)),
                step -> this.editVariation(dimension, index, current -> current.withBias(this.sliders.bias(step))));
        list.addRow(weight[0], bias);

        list.addLabels(
                Component.translatable("createWorld.customize.aerolite.wall_block"),
                Component.translatable("createWorld.customize.aerolite.path_block"),
                Component.translatable("createWorld.customize.aerolite.interior_block")
        );
        list.addRow(
                this.blockField(variation.wall(), "createWorld.customize.aerolite.wall_block",
                        state -> this.editVariation(dimension, index, current -> current.withWall(state))),
                this.blockField(variation.path(), "createWorld.customize.aerolite.path_block",
                        state -> this.editVariation(dimension, index, current -> current.withPath(state))),
                this.blockField(variation.interior(), "createWorld.customize.aerolite.interior_block",
                        state -> this.editVariation(dimension, index, current -> current.withInterior(state)))
        );

        Button remove = Button.builder(Component.translatable("createWorld.customize.aerolite.remove_variation"), button -> {
            this.settings = this.edits.withoutVariation(this.settings, dimension, index);
            this.rebuildPending = true;
        }).build();
        remove.active = this.edits.canRemove(this.settings, dimension);
        list.addRow(remove);
    }

    private BlockField blockField(BlockState state, String key, Consumer<BlockState> onChange) {
        Component name = Component.translatable(key);
        BlockField field = new BlockField(this.font, this.tools.blocks(), state, name, onChange);
        this.blockFields.add(field);
        return field;
    }

    private void editVariation(ResourceKey<Level> dimension, int index, UnaryOperator<ShaftVariation> edit) {
        ShaftVariation current = this.settings.variations(dimension).get(index);
        this.settings = this.settings.withVariation(dimension, index, edit.apply(current));
    }

    /** Rebuilds the list entries from the settings and keeps the scroll position. */
    private void rebuildList() {
        if (this.list == null) {
            return;
        }
        double scroll = this.list.getScrollAmount();
        this.list.clear();
        this.populate(this.list);
        this.list.setScrollAmount(scroll);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.rebuildList();
        }
    }

    /** The block field whose dropdown shows inside the list area, or null. */
    @Nullable
    private BlockField openDropdown() {
        if (this.list == null) {
            return null;
        }
        for (BlockField field : this.blockFields) {
            if (field.dropdownVisible() && field.getY() >= this.list.getY() && field.getBottom() <= this.list.getBottom()) {
                return field;
            }
        }
        return null;
    }

    /** The add dropdown when its list shows and the button lies inside the list area, or null. */
    @Nullable
    private DimensionDropdown openDimensionList() {
        DimensionDropdown dropdown = this.addDropdown;
        if (this.list == null || dropdown == null) {
            return null;
        }
        if (!dropdown.listVisible()) {
            dropdown.closeList();
            return null;
        }
        if (dropdown.getY() >= this.list.getY() && dropdown.getBottom() <= this.list.getBottom()) {
            return dropdown;
        }
        dropdown.closeList();
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BlockField open = this.openDropdown();
        if (open != null) {
            open.renderDropdown(graphics, this.font, this.width, mouseX, mouseY);
        }
        DimensionDropdown dimensions = this.openDimensionList();
        if (dimensions != null) {
            dimensions.renderList(graphics, this.font, this.height, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DimensionDropdown dimensions = this.openDimensionList();
        if (dimensions != null) {
            dimensions.listClicked(mouseX, mouseY, this.height);
            dimensions.closeList();
            return true;
        }
        BlockField open = this.openDropdown();
        if (open != null && open.dropdownClicked(mouseX, mouseY, this.width)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        DimensionDropdown dimensions = this.openDimensionList();
        if (dimensions != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dimensions.closeList();
            return true;
        }
        BlockField open = this.openDropdown();
        if (open != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open.closeDropdown();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private WorldDimensions withSettings(WorldDimensions dimensions, AeroliteSettings settings) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();
        dimensions.dimensions().forEach((key, stem) -> stems.put(key, stem.generator() instanceof AeroliteChunkGenerator generator
                ? new LevelStem(stem.type(), generator.withAeroliteSettings(settings))
                : stem));
        return new WorldDimensions(stems);
    }

    /** A slider over a fixed count of steps. Each step maps to one setting value. The list row sets its width. */
    private static final class StepSlider extends AbstractSliderButton {

        private final int steps;
        private final IntFunction<Component> message;
        private final IntConsumer onStep;

        StepSlider(int steps, int step, IntFunction<Component> message, IntConsumer onStep) {
            super(0, 0, SettingsList.ROW_WIDTH, 20, CommonComponents.EMPTY, Mth.clamp(step / (double) steps, 0, 1));
            this.steps = steps;
            this.message = message;
            this.onStep = onStep;
            this.updateMessage();
        }

        private int step() {
            return (int) Math.round(this.value * this.steps);
        }

        /** Moves the slider without a change callback. */
        void setStep(int step) {
            this.value = Mth.clamp(step / (double) this.steps, 0, 1);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.message.apply(this.step()));
        }

        @Override
        protected void applyValue() {
            this.value = this.step() / (double) this.steps;
            this.onStep.accept(this.step());
        }
    }

}
