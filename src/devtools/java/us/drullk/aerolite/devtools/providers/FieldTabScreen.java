package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import us.drullk.aerolite.client.ArrowButton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Permutation 3: a tab per open field, option D of the mock.
 * Each field has one row: the label, an arrow button and the summary button. A simple state keeps its block field.
 * The arrow or the summary button opens a tab of the field right of Pillar Variations. When the tab is open, they select it.
 * The vanilla tab button has no cross, so the first row of a field tab holds a Close Tab button.
 * A closed tab gives the selection to the tab on its left. Done and Cancel stay in the footer.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class FieldTabScreen extends PermutationScreen {

    private static final int BUTTON_PADDING = 8;

    /** The open field tabs in the sequence of opening. */
    private final List<FieldTab> fieldTabs = new ArrayList<>();

    public FieldTabScreen(Screen parent, ProviderHub hub, VariationSample sample, Consumer<VariationSample> onDone) {
        super(Permutation.FIELD_TAB, parent, hub, sample, onDone);
    }

    @Override
    protected List<Tab> extraTabs() {
        return List.copyOf(this.fieldTabs);
    }

    @Override
    public void openField(int index) {
        for (FieldTab tab : this.fieldTabs) {
            if (tab.index == index) {
                this.tabManager.setCurrentTab(tab, true);
                return;
            }
        }
        FieldTab tab = new FieldTab(index, this.newList());
        this.populateTab(tab);
        this.fieldTabs.add(tab);
        this.rebuildTabBar(tab);
    }

    private void closeTab(FieldTab tab) {
        int position = this.fieldTabs.indexOf(tab);
        if (position < 0) {
            return;
        }
        this.fieldTabs.remove(position);
        this.overlays.forget(tab.list);
        Tab left = position > 0 ? this.fieldTabs.get(position - 1) : this.variationsTab;
        if (left != null) {
            this.rebuildTabBar(this.tabManager.getCurrentTab() == tab ? left : this.tabManager.getCurrentTab());
        }
    }

    private void populateTab(FieldTab tab) {
        Component closeText = Component.literal("Close Tab");
        Button close = Button.builder(closeText, button -> this.closeTab(tab))
                .width(this.font.width(closeText) + 2 * BUTTON_PADDING)
                .build();
        tab.list.addLine(0, ProviderList.NO_FLEX, close);
        this.editor.addRows(tab.list, 0, this.sample.fields.get(tab.index), this::requestRebuild);
    }

    @Override
    protected void populateBlocks(ProviderList list) {
        list.addText(0, Component.literal("Blocks"));
        for (int index = 0; index < this.sample.fields.size(); index++) {
            int field = index;
            ArrowButton arrow = new ArrowButton(Component.literal("Open the tab of " + VariationSample.label(index)), button -> this.openField(field));
            AbstractWidget closed = this.closedField(list, index, "", () -> this.openField(field));
            list.addLine(0, 2, list.label(Component.literal(VariationSample.label(index))), arrow, closed);
        }
    }

    /** Rebuilds the variation list and the list of each field tab. */
    @Override
    protected void rebuildLists() {
        super.rebuildLists();
        for (FieldTab tab : this.fieldTabs) {
            this.rebuild(tab.list, list -> this.populateTab(tab));
        }
    }

    /** The tab of one field. */
    private static final class FieldTab extends ListTab {

        private final int index;

        FieldTab(int index, ProviderList list) {
            super(Component.literal(VariationSample.label(index)), list);
            this.index = index;
        }
    }

}
