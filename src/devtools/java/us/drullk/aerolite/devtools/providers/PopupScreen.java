package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.ArrowButton;
import us.drullk.aerolite.client.SettingsList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Permutation 4: a popup over the list.
 * Each field has one full-width row: an arrow button and a button with the label and the summary. A simple state keeps its block field.
 * The arrow or the summary button opens a popup with the editor under the field, over the rows below, like the dropdown of a block field.
 * The popup goes above the field when the space below is smaller. It scrolls when it is taller than the space.
 * While the popup is open, it is the only child of the screen, so clicks, keys and the Tab sequence stay inside it.
 * Escape, a click outside the popup or its Close button closes it. Each edit goes to the copy of the screen at once.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class PopupScreen extends PermutationScreen {

    /** The row width plus the scroll bar and its padding on both sides. */
    private static final int POPUP_WIDTH = SettingsList.ROW_WIDTH + 32;
    private static final int MIN_ROWS_BELOW = 3;
    private static final int ROW_HEIGHT = 24;
    private static final int Z = 100;
    private static final int BACKGROUND_COLOR = 0xFF101010;
    private static final int OUTLINE_COLOR = 0xFFA0A0A0;
    private static final int NO_FIELD = -1;

    /** The closed widget of each field in the current variation list. */
    private final AbstractWidget[] closedWidgets;
    @Nullable
    private ProviderList popup;
    private int popupField = NO_FIELD;
    private int anchorTop;
    private int anchorBottom;

    public PopupScreen(Screen parent, ProviderHub hub, VariationSample sample, Consumer<VariationSample> onDone) {
        super(Permutation.POPUP, parent, hub, sample, onDone);
        this.closedWidgets = new AbstractWidget[sample.fields.size()];
    }

    @Override
    public void openField(int index) {
        this.closePopup();
        AbstractWidget anchor = this.closedWidgets[index];
        if (anchor == null || this.variationsTab == null) {
            return;
        }
        this.popupField = index;
        this.anchorTop = anchor.getY();
        this.anchorBottom = anchor.getBottom();
        ProviderList popup = new ProviderList(this.minecraft, POPUP_WIDTH, 0, 0);
        this.popup = popup;
        this.populatePopup(popup);
        this.placePopup();
        this.setFocused(popup);
    }

    private void closePopup() {
        if (this.popup != null) {
            this.overlays.forget(this.popup);
            this.popup = null;
            this.setFocused(null);
        }
        this.popupField = NO_FIELD;
    }

    private void populatePopup(ProviderList popup) {
        ProviderSample field = this.sample.fields.get(this.popupField);
        StringWidget title = new StringWidget(0, this.font.lineHeight, Component.literal(VariationSample.label(this.popupField) + ": " + field.type.displayName()), this.font).alignLeft();
        Button close = Button.builder(Component.literal("x"), button -> this.closePopup())
                .size(20, 20)
                .tooltip(Tooltip.create(Component.literal("Close the editor")))
                .build();
        popup.addLine(0, 0, title, close);
        this.editor.addRows(popup, 0, field, this::requestRebuild);
    }

    /** Puts the popup under the field, or above it when the space below is smaller. */
    private void placePopup() {
        ProviderList popup = this.popup;
        if (popup == null || this.variationsTab == null) {
            return;
        }
        ProviderList list = this.variationsTab.list;
        int below = list.getBottom() - this.anchorBottom;
        int above = this.anchorTop - list.getY();
        int content = popup.contentHeight();
        int height;
        int top;
        if (content <= below || below >= MIN_ROWS_BELOW * ROW_HEIGHT || below >= above) {
            height = Math.min(content, below);
            top = this.anchorBottom;
        } else {
            height = Math.min(content, above);
            top = this.anchorTop - height;
        }
        popup.updateSizeAndPosition(POPUP_WIDTH, height, top);
        popup.setX(list.contentLeft() - (POPUP_WIDTH - SettingsList.ROW_WIDTH) / 2);
    }

    @Override
    protected void populateBlocks(ProviderList list) {
        list.addText(0, Component.literal("Blocks"));
        for (int index = 0; index < this.sample.fields.size(); index++) {
            int field = index;
            String label = VariationSample.label(index);
            ArrowButton arrow = new ArrowButton(Component.literal("Open the editor of " + label), button -> this.openField(field));
            AbstractWidget closed = this.closedField(list, index, label + ": ", () -> this.openField(field));
            this.closedWidgets[index] = closed;
            list.addLine(0, 1, arrow, closed);
        }
    }

    /** Rebuilds the variation list and the open popup. The popup keeps its anchor. */
    @Override
    protected void rebuildLists() {
        super.rebuildLists();
        ProviderList popup = this.popup;
        if (popup != null) {
            this.rebuild(popup, this::populatePopup);
            this.placePopup();
        }
    }

    /** While the popup is open, it is the only child. */
    @Override
    public List<? extends GuiEventListener> children() {
        return this.popup != null ? List.of(this.popup) : super.children();
    }

    @Override
    protected boolean shown(ProviderList list) {
        return list == this.popup || (this.popup == null && super.shown(list));
    }

    private boolean insidePopup(double mouseX, double mouseY) {
        return this.popup != null && this.popup.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean coversWidgets() {
        return this.popup != null;
    }

    @Override
    protected void renderLayer(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ProviderList popup = this.popup;
        if (popup == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, Z);
        graphics.fill(popup.getX() - 1, popup.getY() - 3, popup.getRight() + 1, popup.getBottom() + 3, BACKGROUND_COLOR);
        graphics.renderOutline(popup.getX() - 1, popup.getY() - 3, popup.getWidth() + 2, popup.getHeight() + 6, OUTLINE_COLOR);
        popup.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.overlays.mouseClicked(mouseX, mouseY, this.width, this.height, this::shown)) {
            return true;
        }
        if (this.popup != null && !this.insidePopup(mouseX, mouseY)) {
            this.closePopup();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.popup != null) {
            if (!this.overlays.escape(this::shown)) {
                this.closePopup();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** A resize moves the field, so the popup closes. */
    @Override
    protected void repositionElements() {
        this.closePopup();
        super.repositionElements();
    }

}
