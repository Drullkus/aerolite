package us.drullk.aerolite.client.preview;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.client.provider.VariationBlock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the open tabs and the selection of the variation boxes of the preview panel.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class ProviderTabsTest {

    @Test
    void newBoxShowsOnlyBlocks() {
        ProviderTabs.FieldTabs tabs = new ProviderTabs().of(Level.OVERWORLD, 0);
        assertTrue(tabs.open().isEmpty());
        assertNull(tabs.selected());
        assertFalse(tabs.selectRequested());
    }

    @Test
    void openAppendsAndSelects() {
        ProviderTabs.FieldTabs tabs = new ProviderTabs().of(Level.OVERWORLD, 0);
        tabs.open(VariationBlock.PATH);
        tabs.open(VariationBlock.WALL);
        assertEquals(List.of(VariationBlock.PATH, VariationBlock.WALL), tabs.open());
        assertSame(VariationBlock.WALL, tabs.selected());
        assertTrue(tabs.selectRequested());
    }

    @Test
    void openOfAnOpenTabSelectsItInPlace() {
        ProviderTabs.FieldTabs tabs = new ProviderTabs().of(Level.OVERWORLD, 0);
        tabs.open(VariationBlock.PATH);
        tabs.open(VariationBlock.WALL);
        tabs.shown(VariationBlock.WALL);
        tabs.open(VariationBlock.PATH);
        assertEquals(List.of(VariationBlock.PATH, VariationBlock.WALL), tabs.open());
        assertSame(VariationBlock.PATH, tabs.selected());
    }

    @Test
    void requestHoldsUntilTheTabBarShowsTheTab() {
        ProviderTabs.FieldTabs tabs = new ProviderTabs().of(Level.OVERWORLD, 0);
        tabs.open(VariationBlock.WALL);
        tabs.shown(null);
        assertSame(VariationBlock.WALL, tabs.selected());
        assertTrue(tabs.selectRequested());
        tabs.shown(VariationBlock.WALL);
        assertFalse(tabs.selectRequested());
        tabs.shown(null);
        assertNull(tabs.selected());
    }

    @Test
    void selectBlocksKeepsTheOpenTabs() {
        ProviderTabs.FieldTabs tabs = new ProviderTabs().of(Level.OVERWORLD, 0);
        tabs.open(VariationBlock.WALL);
        tabs.shown(VariationBlock.WALL);
        tabs.selectBlocks();
        assertEquals(List.of(VariationBlock.WALL), tabs.open());
        assertNull(tabs.selected());
        assertTrue(tabs.selectRequested());
        tabs.shown(null);
        assertFalse(tabs.selectRequested());
    }

    @Test
    void closeGivesTheSelectionToTheLeft() {
        ProviderTabs.FieldTabs tabs = new ProviderTabs().of(Level.OVERWORLD, 0);
        tabs.open(VariationBlock.WALL);
        tabs.open(VariationBlock.PATH);
        tabs.open(VariationBlock.INTERIOR);
        tabs.shown(VariationBlock.INTERIOR);
        tabs.close(VariationBlock.INTERIOR);
        assertSame(VariationBlock.PATH, tabs.selected());
        assertTrue(tabs.selectRequested());
        tabs.shown(VariationBlock.PATH);
        tabs.close(VariationBlock.WALL);
        assertSame(VariationBlock.PATH, tabs.selected());
        assertFalse(tabs.selectRequested());
        tabs.close(VariationBlock.PATH);
        assertNull(tabs.selected());
        assertTrue(tabs.open().isEmpty());
    }

    @Test
    void retainClosesHiddenFields() {
        ProviderTabs.FieldTabs tabs = new ProviderTabs().of(Level.OVERWORLD, 0);
        tabs.open(VariationBlock.WALL);
        tabs.open(VariationBlock.TRUSSES);
        tabs.retain(List.of(VariationBlock.WALL, VariationBlock.PATH));
        assertEquals(List.of(VariationBlock.WALL), tabs.open());
        assertSame(VariationBlock.WALL, tabs.selected());
    }

    @Test
    void removedVariationMovesLaterTabsDown() {
        ProviderTabs all = new ProviderTabs();
        ProviderTabs.FieldTabs first = all.of(Level.OVERWORLD, 0);
        ProviderTabs.FieldTabs second = all.of(Level.OVERWORLD, 1);
        ProviderTabs.FieldTabs third = all.of(Level.OVERWORLD, 2);
        ProviderTabs.FieldTabs nether = all.of(Level.NETHER, 1);
        all.removedVariation(Level.OVERWORLD, 1);
        assertSame(first, all.of(Level.OVERWORLD, 0));
        assertSame(third, all.of(Level.OVERWORLD, 1));
        assertNotSame(second, all.of(Level.OVERWORLD, 2));
        assertSame(nether, all.of(Level.NETHER, 1));
    }

    @Test
    void removedDimensionDropsItsTabs() {
        ProviderTabs all = new ProviderTabs();
        ProviderTabs.FieldTabs overworld = all.of(Level.OVERWORLD, 0);
        ProviderTabs.FieldTabs nether = all.of(Level.NETHER, 0);
        all.removedDimension(Level.OVERWORLD);
        assertNotSame(overworld, all.of(Level.OVERWORLD, 0));
        assertSame(nether, all.of(Level.NETHER, 0));
    }

}
