package us.drullk.aerolite.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A button that shows one chosen candidate. A click opens a list of the candidates below the button.
 * A click on an entry picks it and closes the list. A pick tells the pick listener.
 * The screen draws the list and sends clicks to it, so the list covers the rows below.
 * The list shows only while the button has focus.
 * With no candidate, the button is off and shows the empty text.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param <T> the type of the candidates
 */
public class ChoiceDropdown<T> extends AbstractButton {

    private static final int ENTRY_HEIGHT = 14;
    private static final int ARROW_WIDTH = 7;
    private static final int BACKGROUND_COLOR = 0xF0101010;
    private static final int HIGHLIGHT_COLOR = 0xFF404060;
    private static final int OUTLINE_COLOR = 0xFFA0A0A0;

    private final List<T> candidates;
    private final Function<T, Component> names;
    private Consumer<T> onPick = picked -> {
    };
    private int chosen;
    private int highlighted;
    private boolean open;

    /**
     * @param preferred the candidate to show first; the first candidate when it is absent
     * @param empty     the text when no candidate exists
     */
    public ChoiceDropdown(List<T> candidates, @Nullable T preferred, Function<T, Component> names, Component empty) {
        super(0, 0, SettingsList.HALF_WIDTH, 20, empty);
        this.candidates = List.copyOf(candidates);
        this.names = names;
        this.chosen = preferred == null ? 0 : Math.max(0, this.candidates.indexOf(preferred));
        this.active = !this.candidates.isEmpty();
        if (this.active) {
            this.setMessage(names.apply(this.candidates.get(this.chosen)));
        }
    }

    /** The chosen candidate, or null when no candidate exists. */
    @Nullable
    public T chosen() {
        return this.candidates.isEmpty() ? null : this.candidates.get(this.chosen);
    }

    /** Sets the listener that a pick from the list calls. */
    public ChoiceDropdown<T> onPick(Consumer<T> onPick) {
        this.onPick = onPick;
        return this;
    }

    /** Shows the candidate. A value outside the candidates changes nothing. It calls no pick listener. */
    public void choose(T candidate) {
        int index = this.candidates.indexOf(candidate);
        if (index >= 0) {
            this.chosen = index;
            this.setMessage(this.names.apply(candidate));
        }
    }

    /** True when the list shows. A refocus passes through unfocused, so focus is read here and not in {@code setFocused}. */
    public boolean listVisible() {
        return this.open && this.active && this.visible && this.isFocused();
    }

    public void closeList() {
        this.open = false;
    }

    @Override
    public void onPress() {
        if (this.active) {
            this.open = !this.open;
            this.highlighted = this.chosen;
        }
    }

    private void pick(int index) {
        this.chosen = index;
        this.setMessage(this.names.apply(this.candidates.get(index)));
        this.open = false;
        this.onPick.accept(this.candidates.get(index));
    }

    private int listHeight() {
        return this.candidates.size() * ENTRY_HEIGHT;
    }

    /** The list opens below the button, or above it when the screen ends first. */
    private int listY(int screenHeight) {
        int below = this.getY() + this.getHeight();
        return below + this.listHeight() <= screenHeight ? below : this.getY() - this.listHeight();
    }

    /** Draws the open list over the rows around the button. */
    public void renderList(GuiGraphics graphics, Font font, int screenHeight, int mouseX, int mouseY) {
        int x = this.getX();
        int y = this.listY(screenHeight);
        int width = this.getWidth();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(x, y, x + width, y + this.listHeight(), BACKGROUND_COLOR);
        for (int k = 0; k < this.candidates.size(); k++) {
            int top = y + k * ENTRY_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ENTRY_HEIGHT;
            if (hovered || k == this.highlighted) {
                graphics.fill(x, top, x + width, top + ENTRY_HEIGHT, HIGHLIGHT_COLOR);
            }
            String name = font.plainSubstrByWidth(this.names.apply(this.candidates.get(k)).getString(), width - 8);
            graphics.drawString(font, name, x + 4, top + (ENTRY_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        }
        graphics.renderOutline(x, y, width, this.listHeight(), OUTLINE_COLOR);
        graphics.pose().popPose();
    }

    /** Picks the list entry under the mouse. Returns true when the click hits the list. */
    public boolean listClicked(double mouseX, double mouseY, int screenHeight) {
        int y = this.listY(screenHeight);
        if (mouseX < this.getX() || mouseX >= this.getX() + this.getWidth() || mouseY < y || mouseY >= y + this.listHeight()) {
            return false;
        }
        this.pick((int) ((mouseY - y) / ENTRY_HEIGHT));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.listVisible()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_DOWN -> {
                    this.highlighted = (this.highlighted + 1) % this.candidates.size();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    this.highlighted = (this.highlighted + this.candidates.size() - 1) % this.candidates.size();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                    this.pick(this.highlighted);
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        int right = this.getX() + this.getWidth() - TEXT_MARGIN;
        renderScrollingString(graphics, font, this.getMessage(), this.getX() + TEXT_MARGIN, this.getY(), right - ARROW_WIDTH - TEXT_MARGIN, this.getY() + this.getHeight(), color);
        if (this.active) {
            int top = this.getY() + (this.getHeight() - 4) / 2;
            int left = right - ARROW_WIDTH - 1;
            for (int row = 0; row < 4; row++) {
                graphics.fill(left + row, top + row, left + ARROW_WIDTH - row, top + row + 1, color);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

}
