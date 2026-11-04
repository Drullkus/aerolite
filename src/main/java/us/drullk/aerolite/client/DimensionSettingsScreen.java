package us.drullk.aerolite.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.provider.ProviderScreen;
import us.drullk.aerolite.client.provider.VariationBlock;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.StateProviders;
import us.drullk.aerolite.world.WeightedVariation;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.pattern.PatternType;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The settings screen of one dimension. It edits a copy of the settings.
 * The tab Shaft Placement holds the placement sliders.
 * The tab Shaft Variations holds the variation rows and the Add Variation button.
 * The pattern widgets follow the five sliders of a variation. A change of the pattern, the maze type, the tile size or the shape rebuilds the rows on the next tick.
 * Each block field has one row with its label, its value and an Edit button. Edit pushes a {@link ProviderScreen} for the field.
 * The return from that screen keeps the selected tab and the scroll position.
 * Export and Import move one variation without its weight through the system clipboard.
 * Done sends the copy back to the shafts screen. Cancel and Escape drop it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class DimensionSettingsScreen extends Screen {

    private static final String KEY = "createWorld.customize.aerolite.";
    /** The band between the tab bar and the list that holds the title. */
    private static final int TITLE_HEIGHT = 20;
    /** The pattern rows hold two widgets each. */
    private static final int PATTERN_COLUMNS = 2;
    /** The space on each side of the text of an Edit button. */
    private static final int BUTTON_PADDING = 8;
    /** The opacity of a checkbox that is off. A vanilla checkbox draws no other off state. */
    private static final float OFF_ALPHA = 0.5F;

    private final Screen parent;
    private final PresetTools tools;
    private final PresetSliders sliders;
    private final PresetEdits edits;
    private final ResourceKey<Level> dimension;
    private final Consumer<AeroliteSettings> onDone;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final List<BlockField> blockFields = new ArrayList<>();
    /** The combos of the variation rows. The screen draws their open lists over the rows below. */
    private final List<ChoiceDropdown<?>> dropdowns = new ArrayList<>();
    private AeroliteSettings settings;
    @Nullable
    private TabNavigationBar tabNavigationBar;
    @Nullable
    private ListTab variationsTab;
    /** True when an add or a remove of a variation waits for the list rebuild of the next tick. The variation rows then ignore input. */
    private boolean rebuildPending;

    /**
     * @param settings the settings of every dimension; the screen edits the entry of {@code dimension}
     * @param onDone   takes the edited settings when the user clicks Done
     */
    public DimensionSettingsScreen(Screen parent, PresetTools tools, ResourceKey<Level> dimension, AeroliteSettings settings, Consumer<AeroliteSettings> onDone) {
        super(Component.translatable(KEY + "dimension_title", tools.edits().dimensionName(dimension)));
        this.parent = parent;
        this.tools = tools;
        this.sliders = tools.sliders();
        this.edits = tools.edits();
        this.dimension = dimension;
        this.settings = settings;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        ListTab placement = new ListTab(Component.translatable(KEY + "tab.shaft_placement"), new SettingsList(this.minecraft, this.width, 0, 0));
        ListTab variations = new ListTab(Component.translatable(KEY + "tab.shaft_variations"), new SettingsList(this.minecraft, this.width, 0, 0));
        this.variationsTab = variations;
        this.populatePlacement(placement.list);
        this.populateVariations(variations.list);

        TabNavigationBar bar = TabNavigationBar.builder(this.tabManager, this.width).addTabs(placement, variations).build();
        this.tabNavigationBar = bar;
        this.addRenderableWidget(bar);
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.onDone.accept(this.settings);
            this.minecraft.setScreen(this.parent);
        }).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            this.addRenderableWidget(widget);
        });
        bar.selectTab(0, false);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.tabNavigationBar == null) {
            return;
        }
        this.tabNavigationBar.setWidth(this.width);
        this.tabNavigationBar.arrangeElements();
        int top = this.tabNavigationBar.getRectangle().bottom() + TITLE_HEIGHT;
        this.layout.setHeaderHeight(top);
        this.layout.arrangeElements();
        this.tabManager.setTabArea(new ScreenRectangle(0, top, this.width, this.height - this.layout.getFooterHeight() - top));
    }

    private ShaftPlacement placement() {
        return this.settings.placement(this.dimension);
    }

    /** The column chance and the two spacing sliders. */
    private void populatePlacement(SettingsList list) {
        list.addRow(new StepSlider(PresetSliders.FRACTION_STEPS, this.sliders.fractionStep(this.placement().columnChance()),
                step -> Component.translatable(KEY + "column_chance", this.sliders.percent(step)),
                step -> this.editPlacement(current -> current.withColumnChance(this.sliders.fraction(step)))));

        StepSlider[] spacing = new StepSlider[2];
        spacing[0] = new StepSlider(PresetSliders.SPACING_STEPS, this.sliders.spacingStep(this.placement().minShaftSpacing()),
                step -> Component.translatable(KEY + "min_shaft_spacing", this.sliders.spacing(step)),
                step -> {
                    this.editPlacement(current -> this.edits.withMinSpacingStep(current, step));
                    spacing[1].setStep(this.sliders.spacingStep(this.placement().maxShaftSpacing()));
                });
        spacing[1] = new StepSlider(PresetSliders.SPACING_STEPS, this.sliders.spacingStep(this.placement().maxShaftSpacing()),
                step -> Component.translatable(KEY + "max_shaft_spacing", this.sliders.spacing(step)),
                step -> {
                    this.editPlacement(current -> this.edits.withMaxSpacingStep(current, step));
                    spacing[0].setStep(this.sliders.spacingStep(this.placement().minShaftSpacing()));
                });
        list.addRow(spacing);
    }

    private void editPlacement(UnaryOperator<ShaftPlacement> edit) {
        this.settings = this.settings.withPlacement(this.dimension, edit.apply(this.placement()));
    }

    /** The rows of each variation, then the Add Variation button. */
    private void populateVariations(SettingsList list) {
        this.blockFields.clear();
        this.dropdowns.clear();
        List<ShaftVariation> variations = this.edits.variations(this.settings, this.dimension);
        for (int index = 0; index < variations.size(); index++) {
            list.beginGroup();
            this.addVariation(list, index, variations.get(index));
            list.endGroup();
            list.addSpacer();
        }
        list.addRow(Button.builder(Component.translatable(KEY + "add_variation"), button -> {
            this.settings = this.edits.withAddedVariation(this.settings, this.dimension);
            this.rebuildPending = true;
        }).build());
    }

    private ShaftVariation variation(int index) {
        return this.edits.variations(this.settings, this.dimension).get(index);
    }

    /**
     * Adds the slider rows, the pattern rows, the shape row, the block rows and the row of Export, Import and Remove of one variation.
     * A Lines maze has no maze bias slider.
     */
    private void addVariation(SettingsList list, int index, ShaftVariation variation) {
        StepSlider[] weight = new StepSlider[1];
        weight[0] = new StepSlider(WeightedVariation.MAX_WEIGHT, this.edits.weight(this.settings, this.dimension, index),
                step -> Component.translatable(KEY + "weight", step),
                step -> {
                    if (this.rebuildPending) {
                        return;
                    }
                    this.settings = this.edits.withWeight(this.settings, this.dimension, index, step);
                    weight[0].setStep(this.edits.weight(this.settings, this.dimension, index));
                });
        if (PresetEdits.showsBias(variation)) {
            StepSlider bias = new StepSlider(PresetSliders.BIAS_STEPS, this.sliders.biasStep(variation.bias()),
                    step -> Component.translatable(KEY + "maze_bias", this.sliders.biasLabel(step)),
                    step -> this.editVariation(index, current -> current.withBias(this.sliders.bias(step))));
            list.addRow(weight[0], bias);
        } else {
            list.addColumns(PATTERN_COLUMNS, weight[0]);
        }

        list.addRow(new StepSlider(PresetSliders.DECAY_SCALE_STEPS, this.sliders.decayScaleStep(variation.decayScale()),
                step -> Component.translatable(KEY + "decay_scale", this.sliders.decayScale(step)),
                step -> this.editVariation(index, current -> current.withDecayScale(this.sliders.decayScale(step)))));
        StepSlider[] thresholds = new StepSlider[2];
        thresholds[0] = new StepSlider(PresetSliders.FRACTION_STEPS, this.edits.decayStep(variation),
                step -> Component.translatable(KEY + "decay_threshold", this.sliders.percent(step)),
                step -> {
                    if (this.rebuildPending) {
                        return;
                    }
                    this.editVariation(index, current -> this.edits.withDecayStep(current, step));
                    thresholds[0].setStep(this.edits.decayStep(this.variation(index)));
                    thresholds[1].setStep(this.edits.erosionStep(this.variation(index)) - 1);
                });
        thresholds[1] = new StepSlider(PresetSliders.EROSION_STEPS, this.edits.erosionStep(variation) - 1,
                step -> Component.translatable(KEY + "erosion_threshold", this.sliders.percent(step + 1)),
                step -> {
                    if (this.rebuildPending) {
                        return;
                    }
                    this.editVariation(index, current -> this.edits.withErosionStep(current, step + 1));
                    thresholds[0].setStep(this.edits.decayStep(this.variation(index)));
                });
        list.addRow(thresholds);

        this.addPattern(list, index, variation);
        list.addColumns(PATTERN_COLUMNS, this.dropdown(List.of(PillarShape.values()), variation.shape(), shape -> this.edits.choiceName("shape", shape), "shape",
                shape -> this.editVariationRebuild(index, current -> current.withShape(shape))));

        list.addText(Component.translatable(KEY + "blocks"));
        for (VariationBlock block : VariationBlock.shown(variation)) {
            this.addBlockRow(list, index, variation, block);
        }

        Button remove = Button.builder(Component.translatable(KEY + "remove_variation"), button -> {
            if (this.rebuildPending) {
                return;
            }
            this.settings = this.edits.withoutVariation(this.settings, this.dimension, index);
            this.rebuildPending = true;
        }).build();
        remove.active = this.edits.canRemove(this.settings, this.dimension);
        list.addRow(
                this.clipboardButton("copy_variation", button -> {
                    if (!this.rebuildPending) {
                        this.tools.json().copyVariation(this.variation(index));
                    }
                }),
                this.clipboardButton("paste_variation", button -> this.importVariation(index)),
                remove
        );
    }

    /** The pattern combo and the widgets of the pattern, two per row. */
    private void addPattern(SettingsList list, int index, ShaftVariation variation) {
        ChoiceDropdown<PatternType> pattern = this.dropdown(List.of(PatternType.values()), variation.pattern().patternType(), this.edits::patternName, "pattern",
                type -> this.editVariationRebuild(index, current -> this.edits.withPatternType(current, type)));
        switch (variation.pattern()) {
            case MazeSettings maze -> this.addMaze(list, index, pattern, maze);
            case BricksSettings bricks -> {
                list.addRow(pattern, this.dropdown(List.of(BricksSettings.Size.values()), bricks.size(), size -> this.edits.choiceName("size", size), "size",
                        size -> this.editPattern(index, BricksSettings.class, current -> current.withSize(size))));
                this.addPaths(list, index, bricks.paths(), null);
            }
            case TilesSettings tiles -> {
                list.addRow(pattern, this.dropdown(List.of(TilesSettings.Size.values()), tiles.size(), size -> this.edits.choiceName("size", size), "size",
                        size -> this.editVariationRebuild(index, current -> this.edits.withPattern(current, TilesSettings.class, edited -> edited.withSize(size)))));
                ChoiceDropdown<TilesSettings.Stagger> stagger = this.dropdown(PresetEdits.staggerChoices(tiles.size()), tiles.stagger(),
                        choice -> this.edits.choiceName("stagger", choice), "stagger",
                        choice -> this.editPattern(index, TilesSettings.class, current -> current.withStagger(choice)));
                stagger.enabled(choice -> PresetEdits.staggerAllowed(this.tiles(index), choice));
                stagger.entryTooltips(choice -> PresetEdits.staggerAllowed(this.tiles(index), choice) ? null : Component.translatable(KEY + "stagger.x_encased_tooltip"));
                Checkbox encased = this.checkbox("encased", tiles.encased(), value -> {
                    this.editPattern(index, TilesSettings.class, current -> current.withEncased(value));
                    stagger.choose(this.tiles(index).stagger());
                });
                list.addRow(stagger, encased);
                this.addPaths(list, index, tiles.paths(), null);
            }
            case ScalesSettings scales -> {
                list.addColumns(PATTERN_COLUMNS, pattern);
                Checkbox overTails = this.checkbox("paths_over_tails", scales.pathsOverTails(),
                        value -> this.editPattern(index, ScalesSettings.class, current -> current.withPathsOverTails(value)));
                this.addPaths(list, index, scales.paths(), overTails);
            }
            case ChaosSettings chaos -> {
                list.addRow(pattern, this.dropdown(List.of(ChaosSettings.Cells.values()), chaos.cells(), cells -> this.edits.choiceName("cells", cells), "cells",
                        cells -> this.editPattern(index, ChaosSettings.class, current -> current.withCells(cells))));
                StepSlider spacing = this.tipped(new StepSlider(PresetSliders.CHAOS_SPACING_STEPS, this.sliders.chaosSpacingStep(chaos.spacing()),
                        step -> Component.translatable(KEY + "node_spacing", this.sliders.chaosSpacing(step)),
                        step -> this.editPattern(index, ChaosSettings.class, current -> current.withSpacing(this.sliders.chaosSpacing(step)))), "node_spacing");
                StepSlider jitter = this.tipped(new StepSlider(PresetSliders.FRACTION_STEPS, this.sliders.clampedFractionStep(chaos.jitter()),
                        step -> Component.translatable(KEY + "jitter", this.sliders.percent(step)),
                        step -> this.editPattern(index, ChaosSettings.class, current -> current.withJitter(this.sliders.clampedFraction(step)))), "jitter");
                list.addRow(spacing, jitter);
                list.addColumns(PATTERN_COLUMNS, this.tipped(new StepSlider(PresetSliders.CORNER_RADIUS_STEPS, this.sliders.cornerRadiusStep(chaos.cornerRadius()),
                        step -> Component.translatable(KEY + "corner_radius", this.sliders.cornerRadius(step)),
                        step -> this.editPattern(index, ChaosSettings.class, current -> current.withCornerRadius(this.sliders.cornerRadius(step)))), "corner_radius"));
                this.addPaths(list, index, chaos.paths(), null);
            }
        }
    }

    /** The pattern combo and the maze type combo, then the pattern strength, the seam of a ribs maze or the spacing of a lines maze. */
    private void addMaze(SettingsList list, int index, ChoiceDropdown<PatternType> pattern, MazeSettings maze) {
        list.addRow(pattern, this.dropdown(List.of(MazeSettings.Type.values()), maze.type(), type -> this.edits.choiceName("maze_type", type), "maze_type",
                type -> this.editVariationRebuild(index, current -> this.edits.withPattern(current, MazeSettings.class, edited -> edited.withType(type)))));
        List<AbstractWidget> options = new ArrayList<>();
        if (PresetEdits.showsStrength(maze.type())) {
            options.add(this.tipped(new StepSlider(PresetSliders.FRACTION_STEPS, this.sliders.clampedFractionStep(maze.strength()),
                    step -> Component.translatable(KEY + "pattern_strength", this.sliders.percent(step)),
                    step -> this.editPattern(index, MazeSettings.class, current -> current.withStrength(this.sliders.clampedFraction(step)))), "pattern_strength"));
        }
        if (maze.type() == MazeSettings.Type.RIBS) {
            ChoiceDropdown<MazeSettings.Seam> seam = this.dropdown(List.of(MazeSettings.Seam.values()), maze.seam(), choice -> this.edits.choiceName("seam", choice), "seam",
                    choice -> this.editPattern(index, MazeSettings.class, current -> current.withSeam(choice)));
            seam.entryTooltips(DimensionSettingsScreen::seamTooltip);
            seam.setTooltip(Tooltip.create(seamTooltip(maze.seam())));
            seam.onPick(choice -> {
                this.editPattern(index, MazeSettings.class, current -> current.withSeam(choice));
                seam.setTooltip(Tooltip.create(seamTooltip(choice)));
            });
            options.add(seam);
        }
        if (maze.type() == MazeSettings.Type.LINES) {
            List<Integer> spacings = new ArrayList<>();
            for (int spacing = MazeSettings.MIN_SPACING; spacing <= MazeSettings.MAX_SPACING; spacing++) {
                spacings.add(spacing);
            }
            ChoiceDropdown<Integer> spacing = this.dropdown(spacings, maze.spacing(), value -> Component.literal(Integer.toString(value)), "line_spacing",
                    value -> this.editPattern(index, MazeSettings.class, current -> current.withSpacing(value)));
            spacing.setTooltip(Tooltip.create(Component.translatable(KEY + "line_spacing_tooltip")));
            options.add(spacing);
        }
        if (!options.isEmpty()) {
            list.addColumns(PATTERN_COLUMNS, options.toArray(AbstractWidget[]::new));
        }
    }

    /** The full name of a seam. The combo shows the short name. */
    private static Component seamTooltip(MazeSettings.Seam seam) {
        return Component.translatable(KEY + "seam." + seam.getSerializedName() + "_tooltip");
    }

    /**
     * The loop and surface path rows. Loop Share is off while Allow loops is off.
     * Cutting, Path Chance and the extra checkbox are off while Surface paths is off.
     *
     * @param extra the checkbox right of Path Chance, or null
     */
    private void addPaths(SettingsList list, int index, SurfacePathSettings paths, @Nullable Checkbox extra) {
        StepSlider loopShare = this.tipped(new StepSlider(PresetSliders.FRACTION_STEPS, this.sliders.clampedFractionStep(paths.loopShare()),
                step -> Component.translatable(KEY + "loop_share", this.sliders.percent(step)),
                step -> this.editPaths(index, current -> this.edits.withLoopShareStep(current, step))), "loop_share");
        Checkbox allowLoops = this.checkbox("allow_loops", paths.allowLoops(), value -> {
            this.editPaths(index, current -> current.withAllowLoops(value));
            setOff(loopShare, !value);
        });
        setOff(loopShare, !paths.allowLoops());
        list.addRow(allowLoops, loopShare);

        Checkbox cutting = this.checkbox("cutting", paths.cutting(), value -> this.editPaths(index, current -> current.withCutting(value)));
        StepSlider pathChance = this.tipped(new StepSlider(PresetSliders.FRACTION_STEPS, this.sliders.clampedFractionStep(paths.pathChance()),
                step -> Component.translatable(KEY + "path_chance", this.sliders.percent(step)),
                step -> this.editPaths(index, current -> this.edits.withPathChanceStep(current, step))), "path_chance");
        Checkbox surfacePaths = this.checkbox("surface_paths", paths.surfacePaths(), value -> {
            this.editPaths(index, current -> current.withSurfacePaths(value));
            setOff(cutting, !value);
            setOff(pathChance, !value);
            if (extra != null) {
                setOff(extra, !value);
            }
        });
        setOff(cutting, !paths.surfacePaths());
        setOff(pathChance, !paths.surfacePaths());
        list.addRow(surfacePaths, cutting);
        if (extra != null) {
            setOff(extra, !paths.surfacePaths());
            list.addRow(pathChance, extra);
        } else {
            list.addColumns(PATTERN_COLUMNS, pathChance);
        }
    }

    /** Turns a widget on or off. A checkbox that is off draws translucent. */
    private static void setOff(AbstractWidget widget, boolean off) {
        widget.active = !off;
        if (widget instanceof Checkbox) {
            widget.setAlpha(off ? OFF_ALPHA : 1);
        }
    }

    /** A combo whose button shows the text of {@code key} with the chosen name. A pick during a rebuild changes nothing. */
    private <T> ChoiceDropdown<T> dropdown(List<T> choices, T chosen, Function<T, Component> names, String key, Consumer<T> onPick) {
        ChoiceDropdown<T> dropdown = new ChoiceDropdown<>(choices, chosen, names, CommonComponents.EMPTY)
                .buttonNames(choice -> Component.translatable(KEY + key, names.apply(choice)))
                .onPick(choice -> {
                    if (!this.rebuildPending) {
                        onPick.accept(choice);
                    }
                });
        this.dropdowns.add(dropdown);
        return dropdown;
    }

    /** A vanilla checkbox with the text of {@code key} and the tooltip of {@code key} plus {@code _tooltip}. */
    private Checkbox checkbox(String key, boolean selected, Consumer<Boolean> onChange) {
        return Checkbox.builder(Component.translatable(KEY + key), this.font)
                .selected(selected)
                .tooltip(Tooltip.create(Component.translatable(KEY + key + "_tooltip")))
                .onValueChange((checkbox, value) -> onChange.accept(value))
                .build();
    }

    /** Gives the slider the tooltip of {@code key} plus {@code _tooltip}. */
    private StepSlider tipped(StepSlider slider, String key) {
        slider.setTooltip(Tooltip.create(Component.translatable(KEY + key + "_tooltip")));
        return slider;
    }

    /** The tile options of a variation, or the defaults when its pattern is not tiles. */
    private TilesSettings tiles(int index) {
        return this.variation(index).pattern() instanceof TilesSettings tiles ? tiles : TilesSettings.DEFAULT;
    }

    private <T extends PatternSettings> void editPattern(int index, Class<T> type, UnaryOperator<T> edit) {
        this.editVariation(index, current -> this.edits.withPattern(current, type, edit));
    }

    private void editPaths(int index, UnaryOperator<SurfacePathSettings> edit) {
        this.editVariation(index, current -> this.edits.withPaths(current, edit));
    }

    /** Edits a variation and rebuilds the variation rows on the next tick. */
    private void editVariationRebuild(int index, UnaryOperator<ShaftVariation> edit) {
        if (this.rebuildPending) {
            return;
        }
        this.editVariation(index, edit);
        this.rebuildPending = true;
    }

    /** A button whose tooltip has the key of the button plus {@code _tooltip}. */
    private Button clipboardButton(String key, Button.OnPress onPress) {
        return Button.builder(Component.translatable(KEY + key), onPress)
                .tooltip(Tooltip.create(Component.translatable(KEY + key + "_tooltip")))
                .build();
    }

    /** A good import replaces the variation and keeps its weight. A bad import shows a toast and keeps the variation. */
    private void importVariation(int index) {
        if (this.rebuildPending) {
            return;
        }
        this.tools.json().pasteVariation()
                .ifSuccess(imported -> {
                    this.settings = this.settings.withVariation(this.dimension, index, imported);
                    this.rebuildPending = true;
                })
                .ifError(error -> this.tools.json().showImportFailed(error.message()));
    }

    /**
     * Adds the row of one block field: the label, the value and the Edit button.
     * A simple provider shows its block field as the value. Any other provider shows its summary in gray.
     * A cut summary shows the full text as its tooltip.
     */
    private void addBlockRow(SettingsList list, int index, ShaftVariation variation, VariationBlock block) {
        BlockStateProvider provider = block.get(variation);
        Component name = Component.translatable(block.key());
        BlockState state = StateProviders.simpleState(provider);
        AbstractWidget value;
        Component summary = null;
        if (state != null) {
            BlockField field = new BlockField(this.font, this.tools.blocks(), state, name,
                    edited -> this.editVariation(index, current -> block.with(current, BlockStateProvider.simple(edited))));
            this.blockFields.add(field);
            value = field;
        } else {
            summary = this.tools.providerText().typedSummary(provider);
            value = new StringWidget(SettingsList.ROW_WIDTH, this.font.lineHeight, summary, this.font).alignLeft().setColor(SettingsList.LABEL_COLOR);
        }
        Component editText = Component.translatable(KEY + "provider.edit");
        Button edit = Button.builder(editText, button -> this.openProvider(index, block))
                .size(this.font.width(editText) + 2 * BUTTON_PADDING, SettingsList.TRAILING_SIZE)
                .createNarration(message -> CommonComponents.joinForNarration(message.get(), name))
                .build();
        list.addLine(1, list.label(name), value, edit);
        if (summary != null && this.font.width(summary) > value.getWidth()) {
            value.setTooltip(Tooltip.create(summary));
        }
    }

    /** Pushes the provider screen of one block field. Its Done writes the provider to the edited settings and rebuilds the rows. */
    private void openProvider(int index, VariationBlock block) {
        if (this.rebuildPending) {
            return;
        }
        Component title = Component.translatable(KEY + "provider.title", Component.translatable(block.key()), this.title);
        this.minecraft.setScreen(new ProviderScreen(this, this.tools.blocks(), this.tools.providerText(), title, block.get(this.variation(index)), provider -> {
            this.editVariation(index, current -> block.with(current, provider));
            this.rebuildPending = true;
        }));
    }

    private void editVariation(int index, UnaryOperator<ShaftVariation> edit) {
        if (this.rebuildPending) {
            return;
        }
        this.settings = this.settings.withVariation(this.dimension, index, edit.apply(this.variation(index)));
    }

    /** Rebuilds the variation rows from the settings and keeps the scroll position. */
    private void rebuildVariations() {
        if (this.variationsTab == null) {
            return;
        }
        SettingsList list = this.variationsTab.list;
        double scroll = list.getScrollAmount();
        list.clear();
        this.populateVariations(list);
        list.setScrollAmount(scroll);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.rebuildVariations();
        }
    }

    /** The block field whose dropdown shows inside the list area of the current tab, or null. */
    @Nullable
    private BlockField openDropdown() {
        ListTab tab = this.variationsTab;
        if (tab == null || this.tabManager.getCurrentTab() != tab) {
            return null;
        }
        for (BlockField field : this.blockFields) {
            if (field.dropdownVisible() && field.getY() >= tab.list.getY() && field.getBottom() <= tab.list.getBottom()) {
                return field;
            }
        }
        return null;
    }

    /** The combo whose list shows while its button lies inside the list area of the current tab, or null. It closes the other lists. */
    @Nullable
    private ChoiceDropdown<?> openChoice() {
        ListTab tab = this.variationsTab;
        boolean current = tab != null && this.tabManager.getCurrentTab() == tab;
        ChoiceDropdown<?> open = null;
        for (ChoiceDropdown<?> dropdown : this.dropdowns) {
            boolean inside = current && dropdown.getY() >= tab.list.getY() && dropdown.getBottom() <= tab.list.getBottom();
            if (open == null && dropdown.listVisible() && inside) {
                open = dropdown;
            } else {
                dropdown.closeList();
            }
        }
        return open;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.tabNavigationBar != null) {
            int top = this.tabNavigationBar.getRectangle().bottom();
            graphics.drawCenteredString(this.font, this.title, this.width / 2, top + (TITLE_HEIGHT - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        }
        BlockField open = this.openDropdown();
        if (open != null) {
            open.renderDropdown(graphics, this.font, this.width, mouseX, mouseY);
        }
        ChoiceDropdown<?> choice = this.openChoice();
        if (choice != null) {
            this.clearTooltipForNextRenderPass();
            choice.renderList(graphics, this.font, this.height, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ChoiceDropdown<?> choice = this.openChoice();
        if (choice != null) {
            choice.listClicked(mouseX, mouseY, this.height);
            choice.closeList();
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
        ChoiceDropdown<?> choice = this.openChoice();
        if (choice != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            choice.closeList();
            return true;
        }
        BlockField open = this.openDropdown();
        if (open != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open.closeDropdown();
            return true;
        }
        if (this.tabNavigationBar != null && this.tabNavigationBar.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Cancel and Escape return without the edits. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /** A tab that shows one settings list over the whole tab area. */
    private static final class ListTab implements Tab {

        private final Component title;
        private final SettingsList list;

        ListTab(Component title, SettingsList list) {
            this.title = title;
            this.list = list;
        }

        @Override
        public Component getTabTitle() {
            return this.title;
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.list);
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
            this.list.updateSizeAndPosition(rectangle.width(), rectangle.height(), rectangle.top());
        }
    }

}
