package us.drullk.aerolite.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The settings screen of one dimension. It edits a copy of the settings.
 * The tab Shaft Placement holds the placement sliders. For the Overworld it also holds the cloud height slider.
 * The tab Shaft Variations holds the variation rows and the Add Variation button.
 * Done sends the copy back to the preset screen. Cancel and Escape drop it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_e43f73bd-d689-4553-8382-7b011c0c142a.md}.
 */
public class DimensionSettingsScreen extends Screen {

    private static final String KEY = "createWorld.customize.aerolite.";
    /** The band between the tab bar and the list that holds the title. */
    private static final int TITLE_HEIGHT = 20;

    private final Screen parent;
    private final PresetTools tools;
    private final PresetSliders sliders;
    private final PresetEdits edits;
    private final ResourceKey<Level> dimension;
    private final Consumer<AeroliteSettings> onDone;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final List<BlockField> blockFields = new ArrayList<>();
    private AeroliteSettings settings;
    @Nullable
    private TabNavigationBar tabNavigationBar;
    @Nullable
    private ListTab variationsTab;
    /** True when an add or a remove of a variation waits for the list rebuild of the next tick. */
    private boolean rebuildPending;

    /**
     * @param settings the settings of every dimension; the screen edits the entry of {@code dimension} and the cloud height
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

    /** The column chance and the two spacing sliders. The Overworld adds the cloud height under the header Clouds. */
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

        if (Level.OVERWORLD.equals(this.dimension)) {
            list.addHeader(Component.translatable(KEY + "clouds"));
            list.addRow(new StepSlider(PresetSliders.CLOUD_HEIGHT_STEPS, this.sliders.cloudHeightStep(this.settings.cloudHeight()),
                    step -> Component.translatable(KEY + "cloud_height", this.sliders.cloudHeight(step)),
                    step -> this.settings = this.settings.withCloudHeight(this.sliders.cloudHeight(step))));
        }
    }

    private void editPlacement(UnaryOperator<ShaftPlacement> edit) {
        this.settings = this.settings.withPlacement(this.dimension, edit.apply(this.placement()));
    }

    /** The rows of each variation, then the Add Variation button. */
    private void populateVariations(SettingsList list) {
        this.blockFields.clear();
        List<ShaftVariation> variations = this.edits.variations(this.settings, this.dimension);
        for (int index = 0; index < variations.size(); index++) {
            this.addVariation(list, index, variations.get(index));
        }
        list.addRow(Button.builder(Component.translatable(KEY + "add_variation"), button -> {
            this.settings = this.edits.withAddedVariation(this.settings, this.dimension);
            this.rebuildPending = true;
        }).build());
    }

    private ShaftVariation variation(int index) {
        return this.edits.variations(this.settings, this.dimension).get(index);
    }

    /** Adds the slider rows, the label row, the block row and the Remove row of one variation. */
    private void addVariation(SettingsList list, int index, ShaftVariation variation) {
        StepSlider[] weight = new StepSlider[1];
        weight[0] = new StepSlider(ShaftVariation.MAX_WEIGHT, variation.weight(),
                step -> Component.translatable(KEY + "weight", step),
                step -> {
                    this.settings = this.edits.withWeight(this.settings, this.dimension, index, step);
                    weight[0].setStep(this.variation(index).weight());
                });
        StepSlider bias = new StepSlider(PresetSliders.BIAS_STEPS, this.sliders.biasStep(variation.bias()),
                step -> Component.translatable(KEY + "maze_bias", this.sliders.biasLabel(step)),
                step -> this.editVariation(index, current -> current.withBias(this.sliders.bias(step))));
        list.addRow(weight[0], bias);

        list.addRow(new StepSlider(PresetSliders.DECAY_SCALE_STEPS, this.sliders.decayScaleStep(variation.decayScale()),
                step -> Component.translatable(KEY + "decay_scale", this.sliders.decayScale(step)),
                step -> this.editVariation(index, current -> current.withDecayScale(this.sliders.decayScale(step)))));
        StepSlider[] thresholds = new StepSlider[2];
        thresholds[0] = new StepSlider(PresetSliders.FRACTION_STEPS, this.edits.decayStep(variation),
                step -> Component.translatable(KEY + "decay_threshold", this.sliders.percent(step)),
                step -> {
                    this.editVariation(index, current -> this.edits.withDecayStep(current, step));
                    thresholds[0].setStep(this.edits.decayStep(this.variation(index)));
                    thresholds[1].setStep(this.edits.erosionStep(this.variation(index)) - 1);
                });
        thresholds[1] = new StepSlider(PresetSliders.EROSION_STEPS, this.edits.erosionStep(variation) - 1,
                step -> Component.translatable(KEY + "erosion_threshold", this.sliders.percent(step + 1)),
                step -> {
                    this.editVariation(index, current -> this.edits.withErosionStep(current, step + 1));
                    thresholds[0].setStep(this.edits.decayStep(this.variation(index)));
                });
        list.addRow(thresholds);

        list.addLabels(
                Component.translatable(KEY + "wall_block"),
                Component.translatable(KEY + "path_block"),
                Component.translatable(KEY + "interior_block")
        );
        list.addRow(
                this.blockField(variation.wall(), KEY + "wall_block", state -> this.editVariation(index, current -> current.withWall(state))),
                this.blockField(variation.path(), KEY + "path_block", state -> this.editVariation(index, current -> current.withPath(state))),
                this.blockField(variation.interior(), KEY + "interior_block", state -> this.editVariation(index, current -> current.withInterior(state)))
        );

        Button remove = Button.builder(Component.translatable(KEY + "remove_variation"), button -> {
            this.settings = this.edits.withoutVariation(this.settings, this.dimension, index);
            this.rebuildPending = true;
        }).build();
        remove.active = this.edits.canRemove(this.settings, this.dimension);
        list.addRow(remove);
    }

    private BlockField blockField(BlockState state, String key, Consumer<BlockState> onChange) {
        BlockField field = new BlockField(this.font, this.tools.blocks(), state, Component.translatable(key), onChange);
        this.blockFields.add(field);
        return field;
    }

    private void editVariation(int index, UnaryOperator<ShaftVariation> edit) {
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
