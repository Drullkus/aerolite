package us.drullk.aerolite.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.lwjgl.glfw.GLFW;
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
 * An add or a remove of a variation rebuilds the list entries. A slider or a block field edits its value in place.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_00-41_1fb7a266-bfdf-4ab5-8b4a-1652dc840cb2.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public class AerolitePresetScreen extends Screen {

    private static final int MIN_SCALE_EXPONENT = Mth.log2(AeroliteSettings.MIN_DECAY_SCALE);
    private static final int MAX_SCALE_EXPONENT = Mth.log2(AeroliteSettings.MAX_DECAY_SCALE);
    /** The dimensions that the variation section edits, in screen sequence. */
    private static final List<ResourceKey<Level>> DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

    private final CreateWorldScreen parent;
    private final PresetSliders sliders;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private SettingsList list;
    private AeroliteSettings settings;
    private final List<BlockField> blockFields = new ArrayList<>();
    /** True when an add or a remove waits for the list rebuild of the next tick. */
    private boolean rebuildPending;

    public AerolitePresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetSliders sliders) {
        super(Component.translatable("createWorld.customize.aerolite.title"));
        this.parent = parent;
        this.sliders = sliders;
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
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    /** Adds every section of the settings to the list. */
    private void populate(SettingsList list) {
        this.blockFields.clear();
        list.addHeader(Component.translatable("createWorld.customize.aerolite.shaft_placement"));
        list.addRow(new StepSlider(PresetSliders.FRACTION_STEPS, Math.round(this.settings.columnChance() * PresetSliders.FRACTION_STEPS),
                step -> Component.translatable("createWorld.customize.aerolite.column_chance", this.sliders.percent(step)),
                step -> this.settings = this.settings.withColumnChance(step / (float) PresetSliders.FRACTION_STEPS)));

        StepSlider[] spacing = new StepSlider[2];
        spacing[0] = new StepSlider(PresetSliders.SPACING_STEPS, this.sliders.spacingStep(this.settings.minShaftSpacing()),
                step -> Component.translatable("createWorld.customize.aerolite.min_shaft_spacing", this.sliders.spacing(step)),
                step -> {
                    int max = Math.max(this.sliders.spacing(step), this.settings.maxShaftSpacing());
                    this.settings = this.settings.withShaftSpacing(this.sliders.spacing(step), max);
                    spacing[1].setStep(this.sliders.spacingStep(max));
                });
        spacing[1] = new StepSlider(PresetSliders.SPACING_STEPS, this.sliders.spacingStep(this.settings.maxShaftSpacing()),
                step -> Component.translatable("createWorld.customize.aerolite.max_shaft_spacing", this.sliders.spacing(step)),
                step -> {
                    int min = Math.min(this.sliders.spacing(step), this.settings.minShaftSpacing());
                    this.settings = this.settings.withShaftSpacing(min, this.sliders.spacing(step));
                    spacing[0].setStep(this.sliders.spacingStep(min));
                });
        list.addRow(spacing);

        list.addHeader(Component.translatable("createWorld.customize.aerolite.shaft_surface"));
        list.addRow(new StepSlider(MAX_SCALE_EXPONENT - MIN_SCALE_EXPONENT, Mth.log2(this.settings.decayScale()) - MIN_SCALE_EXPONENT,
                step -> Component.translatable("createWorld.customize.aerolite.decay_scale", 1 << (MIN_SCALE_EXPONENT + step)),
                step -> this.settings = this.settings.withDecayScale(1 << (MIN_SCALE_EXPONENT + step))));
        // The erosion threshold stays at least one step above the decay threshold.
        StepSlider[] thresholds = new StepSlider[2];
        thresholds[0] = new StepSlider(PresetSliders.FRACTION_STEPS, Math.round(this.settings.decayThreshold() * PresetSliders.FRACTION_STEPS),
                step -> Component.translatable("createWorld.customize.aerolite.decay_threshold", this.sliders.percent(step)),
                step -> {
                    int decay = Math.min(step, PresetSliders.FRACTION_STEPS - 1);
                    int erosion = Math.max(decay + 1, Math.round(this.settings.erosionThreshold() * PresetSliders.FRACTION_STEPS));
                    this.settings = this.settings.withDecayThreshold(decay / (float) PresetSliders.FRACTION_STEPS).withErosionThreshold(erosion / (float) PresetSliders.FRACTION_STEPS);
                    thresholds[0].setStep(decay);
                    thresholds[1].setStep(erosion - 1);
                });
        thresholds[1] = new StepSlider(PresetSliders.FRACTION_STEPS - 1, Math.round(this.settings.erosionThreshold() * PresetSliders.FRACTION_STEPS) - 1,
                step -> Component.translatable("createWorld.customize.aerolite.erosion_threshold", this.sliders.percent(step + 1)),
                step -> {
                    int erosion = step + 1;
                    int decay = Math.min(erosion - 1, Math.round(this.settings.decayThreshold() * PresetSliders.FRACTION_STEPS));
                    this.settings = this.settings.withDecayThreshold(decay / (float) PresetSliders.FRACTION_STEPS).withErosionThreshold(erosion / (float) PresetSliders.FRACTION_STEPS);
                    thresholds[0].setStep(decay);
                });
        list.addRow(thresholds);

        list.addHeader(Component.translatable("createWorld.customize.aerolite.clouds"));
        list.addRow(new StepSlider(PresetSliders.CLOUD_HEIGHT_STEPS, this.sliders.cloudHeightStep(this.settings.cloudHeight()),
                step -> Component.translatable("createWorld.customize.aerolite.cloud_height", this.sliders.cloudHeight(step)),
                step -> this.settings = this.settings.withCloudHeight(this.sliders.cloudHeight(step))));

        list.addHeader(Component.translatable("createWorld.customize.aerolite.shaft_variations"));
        list.addRow(CycleButton.onOffBuilder(this.settings.shaftsInUnlistedDimensions()).create(0, 0, SettingsList.ROW_WIDTH, 20,
                Component.translatable("createWorld.customize.aerolite.shafts_in_unlisted_dimensions"),
                (button, value) -> this.settings = this.settings.withShaftsInUnlistedDimensions(value)));
        for (ResourceKey<Level> dimension : DIMENSIONS) {
            this.addDimension(list, dimension);
        }
    }

    /** Adds the header, the variation rows and the Add button of one dimension. */
    private void addDimension(SettingsList list, ResourceKey<Level> dimension) {
        list.addHeader(Component.translatable("createWorld.customize.aerolite.dimension." + dimension.location().getPath()));
        List<ShaftVariation> variations = this.settings.variations().getOrDefault(dimension, List.of());
        for (int index = 0; index < variations.size(); index++) {
            this.addVariation(list, dimension, index, variations.get(index), variations.size());
        }
        list.addRow(Button.builder(Component.translatable("createWorld.customize.aerolite.add_variation"), button -> {
            List<ShaftVariation> current = this.settings.variations().getOrDefault(dimension, List.of());
            ShaftVariation copy = current.isEmpty() ? this.defaultVariation(dimension) : current.getFirst();
            this.settings = this.settings.withAddedVariation(dimension, copy);
            this.rebuildPending = true;
        }).build());
    }

    private ShaftVariation defaultVariation(ResourceKey<Level> dimension) {
        List<ShaftVariation> defaults = AeroliteSettings.DEFAULT.variations().get(dimension);
        return defaults == null || defaults.isEmpty() ? ShaftVariation.STONE : defaults.getFirst();
    }

    /** Adds the slider row, the block row and the Remove row of one variation. */
    private void addVariation(SettingsList list, ResourceKey<Level> dimension, int index, ShaftVariation variation, int count) {
        StepSlider[] weight = new StepSlider[1];
        weight[0] = new StepSlider(ShaftVariation.MAX_WEIGHT, variation.weight(),
                step -> Component.translatable("createWorld.customize.aerolite.weight", step),
                step -> {
                    // A dimension keeps at least one variation with a positive weight.
                    int chosen = step == 0 && !this.otherWeightIsPositive(dimension, index) ? 1 : step;
                    this.editVariation(dimension, index, current -> current.withWeight(chosen));
                    weight[0].setStep(chosen);
                });
        StepSlider bias = new StepSlider(PresetSliders.BIAS_STEPS, this.sliders.biasStep(variation.bias()),
                step -> Component.translatable("createWorld.customize.aerolite.maze_bias", this.biasLabel(step)),
                step -> this.editVariation(dimension, index, current -> current.withBias(this.sliders.bias(step))));
        list.addRow(weight[0], bias);

        list.addRow(
                this.blockField(variation.interior(), "createWorld.customize.aerolite.interior_block",
                        state -> this.editVariation(dimension, index, current -> current.withInterior(state))),
                this.blockField(variation.wall(), "createWorld.customize.aerolite.wall_block",
                        state -> this.editVariation(dimension, index, current -> current.withWall(state))),
                this.blockField(variation.path(), "createWorld.customize.aerolite.path_block",
                        state -> this.editVariation(dimension, index, current -> current.withPath(state)))
        );

        Button remove = Button.builder(Component.translatable("createWorld.customize.aerolite.remove_variation"), button -> {
            this.settings = this.settings.withoutVariation(dimension, index);
            List<ShaftVariation> rest = this.settings.variations().get(dimension);
            if (rest.stream().noneMatch(other -> other.weight() > 0)) {
                this.settings = this.settings.withVariation(dimension, 0, rest.getFirst().withWeight(1));
            }
            this.rebuildPending = true;
        }).build();
        remove.active = count > 1;
        list.addRow(remove);
    }

    private boolean otherWeightIsPositive(ResourceKey<Level> dimension, int index) {
        List<ShaftVariation> variations = this.settings.variations().get(dimension);
        for (int other = 0; other < variations.size(); other++) {
            if (other != index && variations.get(other).weight() > 0) {
                return true;
            }
        }
        return false;
    }

    private Component biasLabel(int step) {
        String suffix = step < PresetSliders.UNIFORM_BIAS_STEP ? "ringwise" : step == PresetSliders.UNIFORM_BIAS_STEP ? "uniform" : "lengthwise";
        return Component.translatable("createWorld.customize.aerolite.maze_bias." + suffix, this.sliders.biasPercent(step));
    }

    private BlockField blockField(BlockState state, String key, Consumer<BlockState> onChange) {
        Component name = Component.translatable(key);
        BlockField field = new BlockField(this.font, state, name, onChange);
        field.setTooltip(Tooltip.create(name));
        this.blockFields.add(field);
        return field;
    }

    private void editVariation(ResourceKey<Level> dimension, int index, UnaryOperator<ShaftVariation> edit) {
        ShaftVariation current = this.settings.variations().get(dimension).get(index);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BlockField open = this.openDropdown();
        if (open != null) {
            open.renderDropdown(graphics, this.font, this.width, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BlockField open = this.openDropdown();
        if (open != null && open.dropdownClicked(mouseX, mouseY, this.width)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
