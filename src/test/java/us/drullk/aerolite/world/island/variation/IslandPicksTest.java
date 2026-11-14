package us.drullk.aerolite.world.island.variation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the pick and the rolls: the pick shares over a seeded sample, the sub-list pick, a list that names itself,
 * a missing entry, the cell weight bounds and the preview override.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandPicksTest {

    private static final int SIDE = 160;
    private static final double SHARE_TOLERANCE = 0.015;
    private static final CellRolls ROLLS = new CellRolls(0x5EEDL);
    private static final BiomeSource BIOMES = new CheckerboardColumnBiomeSource(HolderSet.direct(), 2);

    private static ResourceKey<IslandVariation> variation(String path) {
        return ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, ResourceLocation.fromNamespaceAndPath("aerolite", path));
    }

    private static ResourceKey<IslandVariationList> list(String path) {
        return ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, ResourceLocation.fromNamespaceAndPath("aerolite", path));
    }

    private static IslandVariation island(double weightMin, double weightMax) {
        return IslandVariation.island(Holder.direct(DensityFunctions.zero()), BIOMES, new UnitRange(weightMin, weightMax), IslandVariation.DEFAULT_FILL, 0, 64, 3);
    }

    /** The share of each picked index over the sample, with {@link IslandPicks#NONE} under -1. */
    private static Map<Integer, Double> shares(IslandPicks picks) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int ix = 0; ix < SIDE; ix++) {
            for (int iz = 0; iz < SIDE; iz++) {
                counts.merge(picks.pick(ROLLS, ix, iz), 1, Integer::sum);
            }
        }
        Map<Integer, Double> shares = new HashMap<>();
        counts.forEach((index, count) -> shares.put(index, count / (double) (SIDE * SIDE)));
        return shares;
    }

    private static void assertShare(double expected, Map<Integer, Double> shares, int index) {
        double actual = shares.getOrDefault(index, 0.0);
        assertEquals(expected, actual, SHARE_TOLERANCE, () -> "The share of " + index + " is " + actual + ", not " + expected);
    }

    @Test
    void pickFollowsTheWeights() {
        VariationLookup lookup = VariationLookup.of(Map.of(variation("a"), island(0.5, 0.5), variation("b"), island(0.5, 0.5), variation("c"), island(0.5, 0.5)),
                Map.of(list("root"), new IslandVariationList(List.of(
                        IslandVariationList.Entry.variation(variation("a"), 1),
                        IslandVariationList.Entry.variation(variation("b"), 0),
                        IslandVariationList.Entry.variation(variation("c"), 3)))));
        IslandPicks picks = IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE);
        assertEquals(List.of(variation("a"), variation("c")), picks.keys(), "A weight of 0 reached a variation");
        Map<Integer, Double> shares = shares(picks);
        assertShare(0.25, shares, 0);
        assertShare(0.75, shares, 1);
        assertEquals(picks.pick(ROLLS, 7, -3), IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE).pick(ROLLS, 7, -3), "The pick is not pure");
    }

    @Test
    void subListPicksAgain() {
        VariationLookup lookup = VariationLookup.of(Map.of(variation("a"), island(0.5, 0.5), variation("b"), island(0.5, 0.5), variation("c"), island(0.5, 0.5)),
                Map.of(list("root"), new IslandVariationList(List.of(
                                IslandVariationList.Entry.variation(variation("a"), 2),
                                IslandVariationList.Entry.list(list("sub"), 2))),
                        list("sub"), new IslandVariationList(List.of(
                                IslandVariationList.Entry.variation(variation("b"), 1),
                                IslandVariationList.Entry.variation(variation("c"), 3)))));
        IslandPicks picks = IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE);
        Map<Integer, Double> shares = shares(picks);
        assertShare(0.5, shares, 0);
        assertShare(0.125, shares, 1);
        assertShare(0.375, shares, 2);
    }

    @Test
    void listThatNamesItselfGivesNoIslandForThatEntry() {
        VariationLookup lookup = VariationLookup.of(Map.of(variation("a"), island(0.5, 0.5)),
                Map.of(list("root"), new IslandVariationList(List.of(
                        IslandVariationList.Entry.variation(variation("a"), 1),
                        IslandVariationList.Entry.list(list("root"), 1),
                        IslandVariationList.Entry.variation(variation("missing"), 2)))));
        IslandPicks picks = IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE);
        Map<Integer, Double> shares = shares(picks);
        assertShare(0.25, shares, 0);
        assertShare(0.75, shares, IslandPicks.NONE);
        assertEquals(IslandPicks.NO_VARIATION_WEIGHT, picks.weightMin());
    }

    @Test
    void missingListGivesNoIsland() {
        IslandPicks picks = IslandPicks.resolve(list("absent"), VariationLookup.NONE, PreviewOverride.NONE);
        assertTrue(picks.variations().isEmpty());
        assertEquals(IslandPicks.NONE, picks.pick(ROLLS, 0, 0));
        assertEquals(0.5, picks.cellWeight(ROLLS, 0, 0));
    }

    @Test
    void rollsStayInTheRangeOfTheVariation() {
        VariationLookup lookup = VariationLookup.of(Map.of(variation("low"), island(0.1, 0.3), variation("high"), island(0.6, 0.95)),
                Map.of(list("root"), new IslandVariationList(List.of(
                        IslandVariationList.Entry.variation(variation("low"), 1),
                        IslandVariationList.Entry.variation(variation("high"), 1)))));
        IslandPicks picks = IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE);
        assertEquals(0.1, picks.weightMin());
        assertEquals(0.95, picks.weightMax());
        for (int ix = 0; ix < 40; ix++) {
            for (int iz = 0; iz < 40; iz++) {
                int index = picks.pick(ROLLS, ix, iz);
                UnitRange range = picks.variation(index).cellWeight();
                double weight = picks.cellWeight(ROLLS, ix, iz);
                assertTrue(weight >= range.min() && weight <= range.max(), "The cell weight left the range of its variation");
                double fill = picks.fill(ROLLS, index, ix, iz);
                assertTrue(fill >= 0.3 && fill <= 0.9, "The fill left the range of its variation");
            }
        }
    }

    @Test
    void overrideReplacesThePickAndTheRolls() {
        VariationLookup lookup = VariationLookup.of(Map.of(variation("a"), island(0.5, 0.5), variation("b"), island(0.2, 0.8)),
                Map.of(list("root"), new IslandVariationList(List.of(IslandVariationList.Entry.variation(variation("a"), 1))),
                        list("other"), new IslandVariationList(List.of(IslandVariationList.Entry.variation(variation("b"), 1)))));
        IslandPicks solo = IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE.withSolo(variation("b")));
        assertEquals(List.of(variation("b")), solo.keys());
        assertEquals(1.0, shares(solo).get(0));
        IslandPicks soloList = IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE.withSoloList(list("other")));
        assertEquals(List.of(variation("b")), soloList.keys());
        IslandPicks pinned = IslandPicks.resolve(list("root"), lookup, PreviewOverride.NONE.withCellWeight(OptionalDouble.of(0.9)).withFill(OptionalDouble.of(0.4)));
        assertEquals(0.9, pinned.weightMin());
        assertEquals(0.9, pinned.weightMax());
        assertEquals(0.9, pinned.cellWeight(ROLLS, 3, 4));
        assertEquals(0.4, pinned.fill(ROLLS, 0, 3, 4));
    }

    @Test
    void cellRandomChannelsAreIndependent() {
        int same = 0;
        for (int ix = 0; ix < 200; ix++) {
            double first = ROLLS.random(0, ix, 0, 0);
            double second = ROLLS.random(1, ix, 0, 0);
            assertTrue(first >= 0 && first < 1, "A cell random value left [0, 1)");
            if (first == second) {
                same++;
            }
        }
        assertTrue(same < 2, "Two channels gave the same values");
    }

    @Test
    void cellRandomChannelsNeverShareASeed() {
        java.util.Set<Long> seeds = new java.util.HashSet<>();
        for (int channel = 0; channel < 65536; channel++) {
            assertTrue(seeds.add(ROLLS.channelSeed(channel)), "Two channels share a seed");
        }
        // A 32-bit hash of the channel gives the channels 8817 and 10486 one salt.
        int same = 0;
        for (int ix = 0; ix < 200; ix++) {
            if (ROLLS.random(8817, ix, 0, 0) == ROLLS.random(10486, ix, 0, 0)) {
                same++;
            }
        }
        assertTrue(same < 2, "The channels 8817 and 10486 gave the same values");
    }

}
