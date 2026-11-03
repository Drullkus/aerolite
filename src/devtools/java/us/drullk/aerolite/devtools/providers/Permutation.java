package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/**
 * The four confirmed layouts of the provider editor in the vanilla dimension screen.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public enum Permutation {
    INLINE("1 Inline Expansion", "The editor opens in rows under the block rows. It tests the fixed rows and the scroll after a rebuild."),
    SUB_SCREEN("2 Pushed Sub-Screen", "The editor is a pushed screen with its own Done and Cancel. It tests the navigation depth."),
    FIELD_TAB("3 Tab per Open Field", "The editor is a tab of the field in the tab bar. It tests the tab bar with a changing tab count."),
    POPUP("4 Popup over the List", "The editor is a popup over the rows. It tests a dropdown over a scrolling list.");

    private final String title;
    private final String description;

    Permutation(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return this.title;
    }

    public String description() {
        return this.description;
    }

    /** The screen of this permutation. It edits the sample and gives it to the done callback. */
    public PermutationScreen create(Screen parent, ProviderHub hub, VariationSample sample, Consumer<VariationSample> onDone) {
        return switch (this) {
            case INLINE -> new InlineScreen(parent, hub, sample, onDone);
            case SUB_SCREEN -> new SubScreenPermutationScreen(parent, hub, sample, onDone);
            case FIELD_TAB -> new FieldTabScreen(parent, hub, sample, onDone);
            case POPUP -> new PopupScreen(parent, hub, sample, onDone);
        };
    }

}
