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
import java.util.List;

/**
 * A scrollable list of section headers and widget rows for the preset screen.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public class SettingsList extends ContainerObjectSelectionList<SettingsList.Entry> {

    public static final int ROW_WIDTH = 310;
    public static final int ROW_SPACING = 8;
    public static final int HALF_WIDTH = (ROW_WIDTH - ROW_SPACING) / 2;
    private static final int ITEM_HEIGHT = 24;

    public SettingsList(Minecraft minecraft, int width, HeaderAndFooterLayout layout) {
        super(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight(), ITEM_HEIGHT);
        this.centerListVertically = false;
    }

    /** Adds a section header with the title. */
    public void addHeader(Component title) {
        this.addEntry(new HeaderEntry(new StringWidget(ROW_WIDTH, this.minecraft.font.lineHeight, title, this.minecraft.font)));
    }

    /** Adds a row of one full width widget or two half width widgets. The row sets the widget widths. */
    public void addRow(AbstractWidget... widgets) {
        if (widgets.length != 1 && widgets.length != 2) {
            throw new IllegalArgumentException("A row holds one or two widgets, not " + widgets.length);
        }
        int width = widgets.length == 1 ? ROW_WIDTH : HALF_WIDTH;
        for (AbstractWidget widget : widgets) {
            widget.setWidth(width);
        }
        this.addEntry(new RowEntry(List.of(widgets)));
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

    /** A section title. It takes no focus. */
    public class HeaderEntry extends Entry {

        private final StringWidget title;

        HeaderEntry(StringWidget title) {
            this.title = title;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            this.title.setPosition(SettingsList.this.contentLeft(), top + height - this.title.getHeight() - 1);
            this.title.render(guiGraphics, mouseX, mouseY, partialTick);
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
            return List.of(new NarratableEntry() {
                @Override
                public NarrationPriority narrationPriority() {
                    return NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, HeaderEntry.this.title.getMessage());
                }
            });
        }
    }

    /** A row of one or two widgets. */
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
