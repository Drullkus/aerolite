package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Permutation 1: inline expansion.
 * The block rows keep four fields of a quarter width under a label row. Each cell holds a fold button left of the field.
 * The fold button or the summary button opens the editor in new rows under the block rows, inside the variation box.
 * Several editors can be open at once, in the sequence of the fields. Each editor starts with a title row and a Close button.
 * The fold button or the Close button closes the editor. Done and Cancel stay in the footer.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class InlineScreen extends PermutationScreen {

    private static final int COLUMNS = 4;
    private static final int NO_FIELD = -1;

    private final TreeSet<Integer> openFields = new TreeSet<>();
    /** The field whose editor the next rebuild scrolls into view, or {@link #NO_FIELD}. */
    private int revealField = NO_FIELD;
    /** The row of the title of each open editor, by field index. */
    private final int[] editorRows;

    public InlineScreen(Screen parent, ProviderHub hub, VariationSample sample, Consumer<VariationSample> onDone) {
        super(Permutation.INLINE, parent, hub, sample, onDone);
        this.editorRows = new int[sample.fields.size()];
    }

    @Override
    public void openField(int index) {
        if (this.openFields.add(index)) {
            this.revealField = index;
            this.requestRebuild();
        }
    }

    private void closeField(int index) {
        if (this.openFields.remove(index)) {
            this.requestRebuild();
        }
    }

    @Override
    protected void populateBlocks(ProviderList list) {
        int count = this.sample.fields.size();
        for (int start = 0; start < count; start += COLUMNS) {
            int end = Math.min(count, start + COLUMNS);
            Component[] labels = new Component[COLUMNS];
            List<AbstractWidget[]> cells = new ArrayList<>();
            for (int index = start; index < start + COLUMNS; index++) {
                labels[index - start] = index < end ? Component.literal(VariationSample.label(index)) : Component.empty();
                if (index < end) {
                    int field = index;
                    cells.add(new AbstractWidget[] {this.foldButton(field), this.closedField(list, field, "", () -> this.openField(field))});
                }
            }
            list.addCellLabels(start == 0 ? Component.literal("Blocks") : null, COLUMNS, labels);
            list.addCells(COLUMNS, cells);
        }
        for (int index : this.openFields) {
            ProviderSample field = this.sample.fields.get(index);
            this.editorRows[index] = list.rowCount();
            Button close = Button.builder(Component.literal("x"), button -> this.closeField(index))
                    .tooltip(Tooltip.create(Component.literal("Close the editor of " + VariationSample.label(index))))
                    .build();
            list.addTrailingRow(Component.literal(VariationSample.label(index) + ": " + field.type.displayName()), close);
            this.editor.addRows(list, ProviderList.INDENT, field, this::requestRebuild);
        }
    }

    /** A 20x20 button that shows the open state of the editor and toggles it. */
    private Button foldButton(int index) {
        boolean open = this.openFields.contains(index);
        String label = VariationSample.label(index);
        return Button.builder(Component.literal(open ? "v" : ">"), button -> {
                    if (this.openFields.contains(index)) {
                        this.closeField(index);
                    } else {
                        this.openField(index);
                    }
                })
                .tooltip(Tooltip.create(Component.literal((open ? "Close the editor of " : "Open the editor of ") + label)))
                .build();
    }

    /** Rebuilds the variation list. An editor that just opened below the view scrolls to the top of the view. */
    @Override
    protected void rebuildLists() {
        super.rebuildLists();
        if (this.revealField == NO_FIELD || this.variationsTab == null) {
            return;
        }
        ProviderList list = this.variationsTab.list;
        int row = this.editorRows[this.revealField];
        this.revealField = NO_FIELD;
        if (list.rowTop(row) + 20 > list.getBottom()) {
            list.scrollToRow(row);
        }
    }

}
