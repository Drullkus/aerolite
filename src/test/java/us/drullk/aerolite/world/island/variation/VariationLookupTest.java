package us.drullk.aerolite.world.island.variation;

import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the overlay form of the variation lookup: the maps win, and the fallback answers each other key.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class VariationLookupTest {

    private static final ResourceKey<IslandVariation> EMPTY = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id("empty"));
    private static final ResourceKey<IslandVariation> OTHER = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id("other"));
    private static final ResourceKey<IslandVariationList> LIST = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, id("islands"));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerolite", path);
    }

    @Test
    void overlayWinsOverTheFallback() {
        IslandVariation registry = IslandVariation.empty(new CheckerboardColumnBiomeSource(HolderSet.direct(), 2), IslandVariation.DEFAULT_CELL_WEIGHT);
        IslandVariation edited = IslandVariation.empty(new CheckerboardColumnBiomeSource(HolderSet.direct(), 3), new UnitRange(0.1, 0.2));
        IslandVariationList registryList = new IslandVariationList(List.of(IslandVariationList.Entry.variation(EMPTY, 1)));
        VariationLookup fallback = VariationLookup.of(Map.of(EMPTY, registry, OTHER, registry), Map.of(LIST, registryList));
        VariationLookup overlay = VariationLookup.overlay(Map.of(EMPTY, edited), Map.of(), fallback);
        assertEquals(Optional.of(edited), overlay.variation(EMPTY));
        assertEquals(Optional.of(registry), overlay.variation(OTHER));
        assertEquals(Optional.of(registryList), overlay.list(LIST));
        assertEquals(Optional.empty(), overlay.variation(ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id("gone"))));
    }

}
