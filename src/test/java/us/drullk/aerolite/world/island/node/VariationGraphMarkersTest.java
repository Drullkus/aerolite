package us.drullk.aerolite.world.island.node;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.PreviewOverride;
import us.drullk.aerolite.world.island.variation.UnitRange;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Checks the markers that the island function keeps in a variation graph, and the lookup and override of a rebound function.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class VariationGraphMarkersTest {

    private static final DensityFunction.NoiseHolder NOISE = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-7, 1.0)));
    private static final ResourceKey<IslandVariationList> LIST = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, id("test_islands"));
    private static final ResourceKey<IslandVariation> BASIC = ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id("test_basic"));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerolite", path);
    }

    private static VariationLookup lookup(DensityFunction function, UnitRange fill) {
        IslandVariation basic = IslandVariation.island(Holder.direct(function), new CheckerboardColumnBiomeSource(HolderSet.direct(), 2),
                IslandVariation.DEFAULT_CELL_WEIGHT, fill, 0, 64, 3);
        return VariationLookup.of(Map.of(BASIC, basic), Map.of(LIST, new IslandVariationList(List.of(IslandVariationList.Entry.variation(BASIC, 1)))));
    }

    @Test
    void variationGraphKeepsEveryMarkerExceptFlatCacheAndCache2d() {
        DensityFunction body = IslandValue.Kind.ISLAND_BODY.function();
        DensityFunction graph = DensityFunctions.add(
                DensityFunctions.add(DensityFunctions.flatCache(body), DensityFunctions.cache2d(body)),
                DensityFunctions.add(DensityFunctions.add(DensityFunctions.interpolated(body), DensityFunctions.cacheOnce(body)), DensityFunctions.cacheAllInCell(body)));
        IslandFunction function = new IslandFunction(NOISE, 256, 128, 0, 0.8, LIST, lookup(new DensityFunctions.HolderHolder(Holder.direct(graph)), IslandVariation.DEFAULT_FILL));
        List<String> markers = new ArrayList<>();
        function.mapAll(visited -> {
            if (visited instanceof DensityFunctions.MarkerOrMarked marker) {
                markers.add(((StringRepresentable) marker.type()).getSerializedName());
            }
            return visited;
        });
        assertEquals(List.of("interpolated", "cache_once", "cache_all_in_cell"), markers);
    }

    @Test
    void reboundFunctionResolvesThroughTheNewLookupAndKeepsItInAMappedCopy() {
        DensityFunction body = IslandValue.Kind.ISLAND_BODY.function();
        IslandFunction function = new IslandFunction(NOISE, 256, 128, 0, 0.8, LIST, lookup(body, new UnitRange(0.3, 0.9)));
        VariationLookup other = lookup(body, new UnitRange(1.0, 1.0));
        PreviewOverride override = PreviewOverride.NONE.withSolo(BASIC);
        IslandFunction rebound = (IslandFunction) function.mapAll(IslandFunction.rebind(other, override));
        assertSame(other, rebound.lookup());
        assertEquals(override, rebound.override());
        assertEquals(new UnitRange(1.0, 1.0), rebound.picks().variations().getFirst().fill());
        IslandFunction mapped = (IslandFunction) rebound.mapAll(visited -> visited);
        assertSame(other, mapped.lookup());
        assertEquals(override, mapped.override());
    }

}
