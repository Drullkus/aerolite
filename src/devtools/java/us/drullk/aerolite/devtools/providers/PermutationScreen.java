package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.BlockField;
import us.drullk.aerolite.client.StepSlider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The shape of the dimension screen for one prototype: the tab bar, the title below it, the settings lists and a footer with Done and Cancel.
 * The tab Pillar Variations holds one variation with the sample fields. Each subclass places the editor of a field in its own way.
 * The screen edits a copy of the sample. Done gives the copy to the hub. Cancel and Escape drop it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public abstract class PermutationScreen extends Screen {

    /** The band between the tab bar and the list that holds the title. */
    protected static final int TITLE_HEIGHT = 20;
    private static final Component NOT_IN_PROTOTYPE = Component.literal("The prototype does not do this.");

    protected final Screen parent;
    protected final ProviderHub hub;
    protected final VariationSample sample;
    protected final Overlays overlays = new Overlays();
    protected final ProviderEditor editor;
    protected final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final Consumer<VariationSample> onDone;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private TabNavigationBar tabNavigationBar;
    @Nullable
    protected ListTab placementTab;
    @Nullable
    protected ListTab variationsTab;
    /** True when a change of the row set waits for the list rebuild of the next tick. */
    private boolean rebuildPending;

    protected PermutationScreen(Permutation permutation, Screen parent, ProviderHub hub, VariationSample sample, Consumer<VariationSample> onDone) {
        super(Component.literal("Amazing Pillars: Overworld (" + permutation.title() + ")"));
        this.parent = parent;
        this.hub = hub;
        this.sample = sample;
        this.onDone = onDone;
        this.editor = new ProviderEditor(Minecraft.getInstance().font, hub.blocks(), this.overlays);
    }

    /** Opens the editor of one field, like a click on the control of the field. */
    public abstract void openField(int index);

    /** The row of the variation list that holds the first block row. Three slider rows come before it. */
    private static final int BLOCKS_ROW = 3;

    /** Shows the Pillar Variations tab, scrolled to the block rows. */
    public void revealBlocks() {
        if (this.variationsTab != null) {
            this.tabManager.setCurrentTab(this.variationsTab, false);
            this.variationsTab.list.scrollToRow(BLOCKS_ROW);
        }
    }

    /** Adds the block fields of the variation. */
    protected abstract void populateBlocks(ProviderList list);

    /** The tabs right of Pillar Variations. */
    protected List<Tab> extraTabs() {
        return List.of();
    }

    @Override
    protected void init() {
        ListTab placement = new ListTab(Component.literal("Pillar Placement"), this.newList());
        ListTab variations = new ListTab(Component.literal("Pillar Variations"), this.newList());
        this.placementTab = placement;
        this.variationsTab = variations;
        this.populatePlacement(placement.list);
        this.populateVariations(variations.list);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.onDone.accept(this.sample);
            this.minecraft.setScreen(this.parent);
        }).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            this.addRenderableWidget(widget);
        });
        this.rebuildTabBar(variations);
    }

    protected ProviderList newList() {
        return new ProviderList(this.minecraft, this.width, 0, 0);
    }

    /** Builds the tab bar again from the current tabs and selects one tab. */
    protected void rebuildTabBar(Tab selected) {
        if (this.placementTab == null || this.variationsTab == null) {
            return;
        }
        List<Tab> tabs = new ArrayList<>(List.of(this.placementTab, this.variationsTab));
        tabs.addAll(this.extraTabs());
        if (this.tabNavigationBar != null) {
            this.removeWidget(this.tabNavigationBar);
        }
        TabNavigationBar bar = TabNavigationBar.builder(this.tabManager, this.width).addTabs(tabs.toArray(Tab[]::new)).build();
        this.tabNavigationBar = bar;
        this.addRenderableWidget(bar);
        this.tabManager.setCurrentTab(selected, false);
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

    /** The column chance slider and the two spacing sliders. They change only the sample. */
    private void populatePlacement(ProviderList list) {
        list.addRow(new StepSlider(20, this.sample.columnChanceStep, step -> Component.literal("Column Chance: " + step * 5 + "%"),
                step -> this.sample.columnChanceStep = step));
        StepSlider[] spacing = new StepSlider[2];
        spacing[0] = new StepSlider(62, this.sample.minSpacingStep, step -> Component.literal("Min Spacing: " + (32 + 16 * step)), step -> {
            this.sample.minSpacingStep = step;
            this.sample.maxSpacingStep = Math.max(this.sample.maxSpacingStep, step);
            spacing[1].setStep(this.sample.maxSpacingStep);
        });
        spacing[1] = new StepSlider(62, this.sample.maxSpacingStep, step -> Component.literal("Max Spacing: " + (32 + 16 * step)), step -> {
            this.sample.maxSpacingStep = step;
            this.sample.minSpacingStep = Math.min(this.sample.minSpacingStep, step);
            spacing[0].setStep(this.sample.minSpacingStep);
        });
        list.addRow(spacing);
    }

    /** The sliders, the block fields and the buttons of the one variation, then the Add Variation button. */
    protected void populateVariations(ProviderList list) {
        VariationSample sample = this.sample;
        list.beginGroup();
        list.addRow(
                new StepSlider(ProviderSample.MAX_WEIGHT, sample.weight, step -> Component.literal("Weight: " + step), step -> sample.weight = step),
                new StepSlider(18, sample.biasStep, step -> Component.literal("Maze Bias: " + biasLabel(step)), step -> sample.biasStep = step));
        list.addRow(new StepSlider(5, sample.decayScaleStep, step -> Component.literal("Decay Scale: " + (32 << step)), step -> sample.decayScaleStep = step));
        StepSlider[] thresholds = new StepSlider[2];
        thresholds[0] = new StepSlider(19, sample.decayStep, step -> Component.literal("Decay Threshold: " + step * 5 + "%"), step -> {
            sample.decayStep = step;
            sample.erosionStep = Math.max(sample.erosionStep, step + 1);
            thresholds[1].setStep(sample.erosionStep - 1);
        });
        thresholds[1] = new StepSlider(19, sample.erosionStep - 1, step -> Component.literal("Erosion Threshold: " + (step + 1) * 5 + "%"), step -> {
            sample.erosionStep = step + 1;
            sample.decayStep = Math.min(sample.decayStep, step);
            thresholds[0].setStep(sample.decayStep);
        });
        list.addRow(thresholds);
        this.populateBlocks(list);
        list.addRow(this.inactive("Copy"), this.inactive("Paste"), this.inactive("Remove"));
        list.endGroup();
        list.addSpacer();
        list.addRow(this.inactive("Add Variation"));
    }

    private static String biasLabel(int step) {
        int percent = 5 + 5 * step;
        return percent + "% " + (percent < 50 ? "ringwise" : percent == 50 ? "uniform" : "lengthwise");
    }

    private Button inactive(String text) {
        Button button = Button.builder(Component.literal(text), pressed -> {
        }).tooltip(Tooltip.create(NOT_IN_PROTOTYPE)).build();
        button.active = false;
        return button;
    }

    /**
     * The closed form of a field. A simple state is a block field. Any other type is a summary button that opens the editor.
     *
     * @param prefix the text before the summary, such as the label of the field
     */
    protected AbstractWidget closedField(ProviderList list, int index, String prefix, Runnable open) {
        ProviderSample field = this.sample.fields.get(index);
        if (field.type == ProviderType.SIMPLE) {
            BlockField blockField = new BlockField(this.font, this.hub.blocks(), field.simpleState, Component.literal(VariationSample.label(index)),
                    state -> field.simpleState = state);
            blockField.setTooltip(Tooltip.create(Component.literal(VariationSample.label(index) + ": " + ProviderType.SIMPLE.displayName())));
            return this.overlays.add(list, blockField);
        }
        return new SummaryButton(() -> prefix + field.typedSummary(this.hub.blocks()),
                () -> field.type.id() + "\n" + field.typedSummary(this.hub.blocks()),
                button -> open.run());
    }

    /** Asks for a rebuild of every list on the next tick. */
    protected void requestRebuild() {
        this.rebuildPending = true;
    }

    /** Rebuilds the lists from the sample. The default rebuilds the variation list. */
    protected void rebuildLists() {
        if (this.variationsTab != null) {
            this.rebuild(this.variationsTab.list, this::populateVariations);
        }
    }

    /** Rebuilds one list and keeps its scroll position. */
    protected void rebuild(ProviderList list, Consumer<ProviderList> populate) {
        double scroll = list.getScrollAmount();
        this.overlays.forget(list);
        list.clear();
        populate.accept(list);
        list.setScrollAmount(scroll);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.rebuildLists();
        }
    }

    /** True when the screen shows the list. */
    protected boolean shown(ProviderList list) {
        return this.tabManager.getCurrentTab() instanceof ListTab tab && tab.list == list;
    }

    /** True while a layer covers the widgets, so they show no hover. */
    protected boolean coversWidgets() {
        return false;
    }

    /** Draws a layer over the lists and under the open dropdown lists. */
    protected void renderLayer(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean covered = this.coversWidgets();
        super.render(graphics, covered ? -1 : mouseX, covered ? -1 : mouseY, partialTick);
        if (this.tabNavigationBar != null) {
            int top = this.tabNavigationBar.getRectangle().bottom();
            graphics.drawCenteredString(this.font, this.title, this.width / 2, top + (TITLE_HEIGHT - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        }
        this.renderLayer(graphics, mouseX, mouseY, partialTick);
        this.overlays.render(graphics, this.font, this.width, this.height, mouseX, mouseY, this::shown);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.overlays.mouseClicked(mouseX, mouseY, this.width, this.height, this::shown)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.overlays.escape(this::shown)) {
            return true;
        }
        if (this.tabNavigationBar != null && !this.coversWidgets() && this.tabNavigationBar.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Cancel and Escape return without the edits. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /** A tab that shows one list over the whole tab area. */
    protected static class ListTab implements Tab {

        private final Component title;
        protected final ProviderList list;

        public ListTab(Component title, ProviderList list) {
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
