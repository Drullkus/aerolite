package us.drullk.aerolite.client.preview;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.client.provider.VariationBlock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The open field tabs of each variation box of the preview panel. It holds no widget.
 * A variation box keys its tabs by its dimension and its index in the variation list.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ProviderTabs {

    private final Map<Key, FieldTabs> tabs = new HashMap<>();

    private record Key(ResourceKey<Level> dimension, int index) {
    }

    /** The tabs of one variation box. The first call for a box gives it only the tab Blocks. */
    public FieldTabs of(ResourceKey<Level> dimension, int index) {
        return this.tabs.computeIfAbsent(new Key(dimension, index), key -> new FieldTabs());
    }

    /** Drops the tabs of the removed variation. The tabs of each later variation move down one index. */
    public void removedVariation(ResourceKey<Level> dimension, int index) {
        Map<Key, FieldTabs> moved = new HashMap<>();
        this.tabs.entrySet().removeIf(entry -> {
            Key key = entry.getKey();
            if (!key.dimension().equals(dimension) || key.index() < index) {
                return false;
            }
            if (key.index() > index) {
                moved.put(new Key(dimension, key.index() - 1), entry.getValue());
            }
            return true;
        });
        this.tabs.putAll(moved);
    }

    /** Drops the tabs of every variation of the dimension. */
    public void removedDimension(ResourceKey<Level> dimension) {
        this.tabs.keySet().removeIf(key -> key.dimension().equals(dimension));
    }

    /**
     * The tab bar of one variation box: the tab Blocks, then one tab per open field in the sequence of opening.
     * The selection is a field, or null for the tab Blocks.
     * A change from code requests the selection, until the tab bar reports that tab as selected.
     */
    public static final class FieldTabs {

        private final List<VariationBlock> open = new ArrayList<>();
        @Nullable
        private VariationBlock selected;
        private boolean selectRequested;

        /** The open fields, in the sequence of their tabs after the tab Blocks. */
        public List<VariationBlock> open() {
            return Collections.unmodifiableList(this.open);
        }

        /** The selected field, or null when the tab Blocks is selected. */
        @Nullable
        public VariationBlock selected() {
            return this.selected;
        }

        /** True while the tab bar must select {@link #selected()}. */
        public boolean selectRequested() {
            return this.selectRequested;
        }

        /** Opens the tab of the field at the right end, or keeps its place when it is open. Then it selects the tab. */
        public void open(VariationBlock field) {
            if (!this.open.contains(field)) {
                this.open.add(field);
            }
            this.selected = field;
            this.selectRequested = true;
        }

        /** Selects the tab Blocks. */
        public void selectBlocks() {
            this.selected = null;
            this.selectRequested = true;
        }

        /** Closes the tab of the field. A closed selected tab gives the selection to the tab on its left. */
        public void close(VariationBlock field) {
            int index = this.open.indexOf(field);
            if (index < 0) {
                return;
            }
            this.open.remove(index);
            if (field == this.selected) {
                this.selected = index > 0 ? this.open.get(index - 1) : null;
                this.selectRequested = true;
            }
        }

        /** Closes each tab whose field the variation does not show. */
        public void retain(Collection<VariationBlock> shown) {
            for (VariationBlock field : List.copyOf(this.open)) {
                if (!shown.contains(field)) {
                    this.close(field);
                }
            }
        }

        /**
         * Takes the tab that the tab bar shows as selected, a field or null for the tab Blocks.
         * While a selection is requested, only the requested tab ends the request. A click on a tab changes the selection.
         */
        public void shown(@Nullable VariationBlock field) {
            if (this.selectRequested) {
                if (field == this.selected) {
                    this.selectRequested = false;
                }
                return;
            }
            this.selected = field;
        }

    }

}
