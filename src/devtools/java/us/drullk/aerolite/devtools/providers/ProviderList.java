package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import us.drullk.aerolite.client.SettingsList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings list of Aerolite with the rows of the provider editors.
 * A line keeps the width of each widget and gives the rest of the row to one flexible widget.
 * A cell row holds an arrow button and a field per column.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class ProviderList extends SettingsList {

    public static final int GAP = 4;
    public static final int INDENT = 12;
    public static final int LABEL_WIDTH = 64;
    /** The flexible widget index of a line whose widgets all keep their widths. */
    public static final int NO_FLEX = -1;
    public static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int WIDGET_HEIGHT = 20;
    private static final int CELL_GAP = 2;

    public ProviderList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y);
    }

    public ProviderList(Minecraft minecraft, int width, HeaderAndFooterLayout layout) {
        super(minecraft, width, layout);
    }

    /** The left edge of the row content. */
    public int contentLeft() {
        return this.getX() + (this.getWidth() - ROW_WIDTH) / 2;
    }

    /** The count of rows. */
    public int rowCount() {
        return this.children().size();
    }

    /** The screen Y of the top of a row at the current scroll. */
    public int rowTop(int row) {
        return this.getRowTop(row);
    }

    /** The height of every row and the list padding together. */
    public int contentHeight() {
        return this.getItemCount() * this.itemHeight + 8;
    }

    /** Scrolls the list so the row is at the top, as far as the list can scroll. */
    public void scrollToRow(int row) {
        this.setScrollAmount(row * this.itemHeight);
    }

    /** A gray label of the label width. */
    public StringWidget label(Component text) {
        return new StringWidget(LABEL_WIDTH, this.minecraft.font.lineHeight, text, this.minecraft.font).alignLeft().setColor(LABEL_COLOR);
    }

    /**
     * Adds a line of widgets with a left indent. Each widget keeps its width, except the widget at {@code flex}.
     * That widget takes the rest of the row.
     */
    public void addLine(int indent, int flex, AbstractWidget... widgets) {
        if (flex != NO_FLEX) {
            int fixed = GAP * (widgets.length - 1);
            for (int i = 0; i < widgets.length; i++) {
                if (i != flex) {
                    fixed += widgets[i].getWidth();
                }
            }
            widgets[flex].setWidth(ROW_WIDTH - indent - fixed);
        }
        this.addEntry(new LineEntry(indent, List.of(widgets)));
    }

    /** Adds a line of widgets of equal width with a left indent. */
    public void addEqual(int indent, AbstractWidget... widgets) {
        int width = (ROW_WIDTH - indent - GAP * (widgets.length - 1)) / widgets.length;
        for (AbstractWidget widget : widgets) {
            widget.setWidth(width);
        }
        this.addEntry(new LineEntry(indent, List.of(widgets)));
    }

    /** Adds a line of white text. */
    public void addText(int indent, Component text) {
        StringWidget widget = new StringWidget(ROW_WIDTH - indent, this.minecraft.font.lineHeight, text, this.minecraft.font).alignLeft();
        this.addEntry(new LineEntry(indent, List.of(widget)));
    }

    private static int columnWidth(int columns) {
        return (ROW_WIDTH - (columns - 1) * ROW_SPACING) / columns;
    }

    /** Adds a label row at the column widths of a cell row, with an optional heading on the line above. */
    public void addCellLabels(@Nullable Component heading, int columns, Component... labels) {
        List<StringWidget> widgets = new ArrayList<>();
        for (Component label : labels) {
            widgets.add(new StringWidget(columnWidth(columns), this.minecraft.font.lineHeight, label, this.minecraft.font).alignLeft().setColor(LABEL_COLOR));
        }
        StringWidget headingWidget = heading == null ? null
                : new StringWidget(ROW_WIDTH, this.minecraft.font.lineHeight, heading, this.minecraft.font).alignLeft();
        this.addEntry(new CellLabelEntry(headingWidget, columns, widgets));
    }

    /** Adds a cell row. Each cell is an arrow button of 20x20 and a field that takes the rest of the column. */
    public void addCells(int columns, List<AbstractWidget[]> cells) {
        int width = columnWidth(columns);
        List<AbstractWidget> widgets = new ArrayList<>();
        for (AbstractWidget[] cell : cells) {
            cell[0].setWidth(WIDGET_HEIGHT);
            cell[1].setWidth(width - WIDGET_HEIGHT - CELL_GAP);
            widgets.add(cell[0]);
            widgets.add(cell[1]);
        }
        this.addEntry(new CellEntry(columns, widgets));
    }

    /** A line of widgets. A text widget is centered on the row height. */
    public class LineEntry extends Entry {

        private final int indent;
        private final List<AbstractWidget> widgets;

        LineEntry(int indent, List<AbstractWidget> widgets) {
            this.indent = indent;
            this.widgets = widgets;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = ProviderList.this.contentLeft() + this.indent;
            for (AbstractWidget widget : this.widgets) {
                int y = widget instanceof AbstractStringWidget ? top + (WIDGET_HEIGHT - widget.getHeight()) / 2 + 1 : top;
                widget.setPosition(x, y);
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
                x += widget.getWidth() + GAP;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }

    /** A row of arrow buttons and fields at the column widths. */
    public class CellEntry extends Entry {

        private final int columns;
        private final List<AbstractWidget> widgets;

        CellEntry(int columns, List<AbstractWidget> widgets) {
            this.columns = columns;
            this.widgets = widgets;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = ProviderList.this.contentLeft();
            int column = columnWidth(this.columns);
            for (int i = 0; i + 1 < this.widgets.size(); i += 2) {
                AbstractWidget arrow = this.widgets.get(i);
                AbstractWidget field = this.widgets.get(i + 1);
                arrow.setPosition(x, top);
                arrow.render(guiGraphics, mouseX, mouseY, partialTick);
                field.setPosition(x + WIDGET_HEIGHT + CELL_GAP, top);
                field.render(guiGraphics, mouseX, mouseY, partialTick);
                x += column + ROW_SPACING;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }

    /** A row of labels above a cell row, with an optional heading on the line above. It takes no focus. */
    public class CellLabelEntry extends Entry {

        @Nullable
        private final StringWidget heading;
        private final int columns;
        private final List<StringWidget> labels;

        CellLabelEntry(@Nullable StringWidget heading, int columns, List<StringWidget> labels) {
            this.heading = heading;
            this.columns = columns;
            this.labels = labels;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = ProviderList.this.contentLeft();
            if (this.heading != null) {
                this.heading.setPosition(x, top + 1);
                this.heading.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            for (StringWidget label : this.labels) {
                label.setPosition(x + 1, top + height - label.getHeight() + 3);
                label.render(guiGraphics, mouseX, mouseY, partialTick);
                x += columnWidth(this.columns) + ROW_SPACING;
            }
        }

        @Nullable
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return null;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

}
