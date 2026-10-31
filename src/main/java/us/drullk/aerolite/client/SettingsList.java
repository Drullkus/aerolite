package us.drullk.aerolite.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A scrollable list of section headers, label rows and widget rows for the preset screens.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 */
public class SettingsList extends ContainerObjectSelectionList<SettingsList.Entry> {

    public static final int ROW_WIDTH = 310;
    public static final int ROW_SPACING = 8;
    public static final int HALF_WIDTH = (ROW_WIDTH - ROW_SPACING) / 2;
    /** The width of the widget at the right end of a header row. */
    public static final int TRAILING_WIDTH = 20;
    /** The size of each square button at the right end of a trailing row. */
    public static final int TRAILING_SIZE = 20;
    private static final int TRAILING_SPACING = 4;
    /** The width of the title of a value row. */
    private static final int VALUE_TITLE_WIDTH = 80;
    /** The most widgets of one row. */
    private static final int MAX_COLUMNS = 4;
    private static final int ITEM_HEIGHT = 24;
    private static final int LABEL_COLOR = 0xFFA0A0A0;

    public SettingsList(Minecraft minecraft, int width, HeaderAndFooterLayout layout) {
        this(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight());
    }

    public SettingsList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, ITEM_HEIGHT);
        this.centerListVertically = false;
    }

    /** Adds a section header with the title. */
    public void addHeader(Component title) {
        this.addEntry(new HeaderEntry(new StringWidget(ROW_WIDTH, this.minecraft.font.lineHeight, title, this.minecraft.font), null));
    }

    /** Adds a section header with the title and a widget at the right end of the row. The row sets the widget size. */
    public void addHeader(Component title, AbstractWidget trailing) {
        int margin = TRAILING_WIDTH + ROW_SPACING;
        trailing.setWidth(TRAILING_WIDTH);
        trailing.setHeight(20);
        this.addEntry(new HeaderEntry(new StringWidget(ROW_WIDTH - 2 * margin, this.minecraft.font.lineHeight, title, this.minecraft.font), trailing));
    }

    /**
     * Adds a row with a title on the left and square buttons at the right end. The row sets the button size.
     */
    public void addTrailingRow(Component title, AbstractWidget... trailing) {
        int buttons = trailing.length * (TRAILING_SIZE + TRAILING_SPACING);
        for (AbstractWidget widget : trailing) {
            widget.setWidth(TRAILING_SIZE);
            widget.setHeight(TRAILING_SIZE);
        }
        StringWidget text = new StringWidget(ROW_WIDTH - buttons - TRAILING_SPACING, this.minecraft.font.lineHeight, title, this.minecraft.font).alignLeft();
        this.addEntry(new TrailingEntry(List.of(trailing), text));
    }

    /**
     * Adds a row with a title on the left, a value text right of it and buttons at the right end.
     * The buttons keep their widths. The row sets their height.
     */
    public void addValueRow(Component title, Component value, AbstractWidget... trailing) {
        int buttons = 0;
        for (AbstractWidget widget : trailing) {
            widget.setHeight(TRAILING_SIZE);
            buttons += widget.getWidth() + TRAILING_SPACING;
        }
        StringWidget titleText = new StringWidget(VALUE_TITLE_WIDTH, this.minecraft.font.lineHeight, title, this.minecraft.font).alignLeft();
        int valueWidth = ROW_WIDTH - VALUE_TITLE_WIDTH - ROW_SPACING - buttons;
        StringWidget valueText = new StringWidget(valueWidth, this.minecraft.font.lineHeight, value, this.minecraft.font).alignLeft().setColor(LABEL_COLOR);
        this.addEntry(new ValueEntry(titleText, valueText, List.of(trailing)));
    }

    /** Adds a row of one to four labels at the column widths of {@link #addRow} with as many widgets. */
    public void addLabels(Component... labels) {
        int width = columnWidth(labels.length);
        List<StringWidget> widgets = new ArrayList<>();
        for (Component label : labels) {
            widgets.add(new StringWidget(width, this.minecraft.font.lineHeight, label, this.minecraft.font).alignLeft().setColor(LABEL_COLOR));
        }
        this.addEntry(new LabelEntry(widgets));
    }

    /** Adds a row of one to four widgets of equal width. The row sets the widget widths. */
    public void addRow(AbstractWidget... widgets) {
        int width = columnWidth(widgets.length);
        for (AbstractWidget widget : widgets) {
            widget.setWidth(width);
        }
        this.addEntry(new RowEntry(List.of(widgets)));
    }

    private static int columnWidth(int columns) {
        if (columns < 1 || columns > MAX_COLUMNS) {
            throw new IllegalArgumentException("A row holds one to " + MAX_COLUMNS + " columns, not " + columns);
        }
        return (ROW_WIDTH - (columns - 1) * ROW_SPACING) / columns;
    }

    /** Removes every entry. */
    public void clear() {
        this.setFocused(null);
        this.clearEntries();
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    /** The left edge of the row content. */
    private int contentLeft() {
        return this.getX() + (this.width - ROW_WIDTH) / 2;
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    }

    /** A section title. It takes focus only through its trailing widget. */
    public class HeaderEntry extends Entry {

        private final StringWidget title;
        @Nullable
        private final AbstractWidget trailing;

        HeaderEntry(StringWidget title, @Nullable AbstractWidget trailing) {
            this.title = title;
            this.trailing = trailing;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int contentLeft = SettingsList.this.contentLeft();
            this.title.setPosition(contentLeft + (ROW_WIDTH - this.title.getWidth()) / 2, top + height - this.title.getHeight() - 1);
            this.title.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.trailing != null) {
                this.trailing.setPosition(contentLeft + ROW_WIDTH - this.trailing.getWidth(), top);
                this.trailing.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        @Nullable
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return this.trailing == null ? null : super.nextFocusPath(event);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.trailing == null ? List.of() : List.of(this.trailing);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            NarratableEntry title = new NarratableEntry() {
                @Override
                public NarrationPriority narrationPriority() {
                    return NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, HeaderEntry.this.title.getMessage());
                }
            };
            return this.trailing == null ? List.of(title) : List.of(title, this.trailing);
        }
    }

    /** A row with a title on the left and square buttons at the right end. */
    public class TrailingEntry extends Entry {

        private final List<AbstractWidget> trailing;
        private final StringWidget title;

        TrailingEntry(List<AbstractWidget> trailing, StringWidget title) {
            this.trailing = trailing;
            this.title = title;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int contentLeft = SettingsList.this.contentLeft();
            this.title.setPosition(contentLeft, top + (TRAILING_SIZE - this.title.getHeight()) / 2 + 1);
            this.title.render(guiGraphics, mouseX, mouseY, partialTick);
            int x = contentLeft + ROW_WIDTH - this.trailing.size() * (TRAILING_SIZE + TRAILING_SPACING) + TRAILING_SPACING;
            for (AbstractWidget widget : this.trailing) {
                widget.setPosition(x, top);
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
                x += widget.getWidth() + TRAILING_SPACING;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.trailing;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.trailing;
        }
    }

    /** A row with a title, a value text and buttons at the right end. */
    public class ValueEntry extends Entry {

        private final StringWidget title;
        private final StringWidget value;
        private final List<AbstractWidget> trailing;

        ValueEntry(StringWidget title, StringWidget value, List<AbstractWidget> trailing) {
            this.title = title;
            this.value = value;
            this.trailing = trailing;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int contentLeft = SettingsList.this.contentLeft();
            int textTop = top + (TRAILING_SIZE - this.title.getHeight()) / 2 + 1;
            this.title.setPosition(contentLeft, textTop);
            this.title.render(guiGraphics, mouseX, mouseY, partialTick);
            this.value.setPosition(contentLeft + VALUE_TITLE_WIDTH + ROW_SPACING, textTop);
            this.value.render(guiGraphics, mouseX, mouseY, partialTick);
            int x = this.value.getRight() + TRAILING_SPACING;
            for (AbstractWidget widget : this.trailing) {
                widget.setPosition(x, top);
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
                x += widget.getWidth() + TRAILING_SPACING;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.trailing;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.trailing;
        }
    }

    /** A row of labels above a row of widgets. It takes no focus. */
    public class LabelEntry extends Entry {

        private final List<StringWidget> labels;

        LabelEntry(List<StringWidget> labels) {
            this.labels = labels;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = SettingsList.this.contentLeft();
            for (StringWidget label : this.labels) {
                label.setPosition(x, top + height - label.getHeight() - 1);
                label.render(guiGraphics, mouseX, mouseY, partialTick);
                x += label.getWidth() + ROW_SPACING;
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

    /** A row of one to three widgets. */
    public class RowEntry extends Entry {

        private final List<AbstractWidget> widgets;

        RowEntry(List<AbstractWidget> widgets) {
            this.widgets = widgets;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int x = SettingsList.this.contentLeft();
            for (AbstractWidget widget : this.widgets) {
                widget.setPosition(x, top);
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
                x += widget.getWidth() + ROW_SPACING;
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

}
