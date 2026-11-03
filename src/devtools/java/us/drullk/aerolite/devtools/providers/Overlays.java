package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import us.drullk.aerolite.client.BlockField;
import us.drullk.aerolite.client.ChoiceDropdown;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The block fields and the choice dropdowns of the lists of one screen.
 * The screen draws their open lists over the rows and sends clicks to them first, like the dimension screen.
 * An open list shows only while its widget is inside the visible area of a list that the screen shows.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class Overlays {

    private final List<Entry<BlockField>> fields = new ArrayList<>();
    private final List<Entry<ChoiceDropdown<?>>> dropdowns = new ArrayList<>();

    public BlockField add(ProviderList list, BlockField field) {
        this.fields.add(new Entry<>(list, field));
        return field;
    }

    public <T> ChoiceDropdown<T> add(ProviderList list, ChoiceDropdown<T> dropdown) {
        this.dropdowns.add(new Entry<>(list, dropdown));
        return dropdown;
    }

    /** Drops the widgets of one list before the list rebuilds. */
    public void forget(ProviderList list) {
        this.fields.removeIf(entry -> entry.list == list);
        this.dropdowns.removeIf(entry -> entry.list == list);
    }

    @Nullable
    private BlockField openField(Predicate<ProviderList> shown) {
        for (Entry<BlockField> entry : this.fields) {
            if (entry.inside(shown) && entry.widget.dropdownVisible()) {
                return entry.widget;
            }
        }
        return null;
    }

    @Nullable
    private ChoiceDropdown<?> openDropdown(Predicate<ProviderList> shown) {
        for (Entry<ChoiceDropdown<?>> entry : this.dropdowns) {
            if (entry.inside(shown) && entry.widget.listVisible()) {
                return entry.widget;
            }
        }
        return null;
    }

    /** True when an open list shows. */
    public boolean anyOpen(Predicate<ProviderList> shown) {
        return this.openField(shown) != null || this.openDropdown(shown) != null;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY, Predicate<ProviderList> shown) {
        BlockField field = this.openField(shown);
        if (field != null) {
            field.renderDropdown(graphics, font, screenWidth, mouseX, mouseY);
        }
        ChoiceDropdown<?> dropdown = this.openDropdown(shown);
        if (dropdown != null) {
            dropdown.renderList(graphics, font, screenHeight, mouseX, mouseY);
        }
    }

    /** Sends a click to the open list. Returns true when the click hits it. */
    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight, Predicate<ProviderList> shown) {
        BlockField field = this.openField(shown);
        if (field != null && field.dropdownClicked(mouseX, mouseY, screenWidth)) {
            return true;
        }
        ChoiceDropdown<?> dropdown = this.openDropdown(shown);
        return dropdown != null && dropdown.listClicked(mouseX, mouseY, screenHeight);
    }

    /** Closes the open list. Returns true when a list was open. */
    public boolean escape(Predicate<ProviderList> shown) {
        BlockField field = this.openField(shown);
        if (field != null) {
            field.closeDropdown();
            return true;
        }
        ChoiceDropdown<?> dropdown = this.openDropdown(shown);
        if (dropdown != null) {
            dropdown.closeList();
            return true;
        }
        return false;
    }

    private record Entry<W extends AbstractWidget>(ProviderList list, W widget) {

        boolean inside(Predicate<ProviderList> shown) {
            return shown.test(this.list) && this.widget.getY() >= this.list.getY() && this.widget.getBottom() <= this.list.getBottom();
        }
    }

}
