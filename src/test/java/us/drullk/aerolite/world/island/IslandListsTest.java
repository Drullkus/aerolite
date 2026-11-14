package us.drullk.aerolite.world.island;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the rule of Done in the Islands screen. Each island biome source takes the list of the island function of its dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandListsTest {

    private static final ResourceKey<IslandVariationList> SHIPPED = list("overworld_islands");
    private static final ResourceKey<IslandVariationList> EDITED = list("edited_islands");
    private static final Holder<DimensionType> TYPE = Holder.Reference.createStandAlone(new HolderOwner<>() {
    }, BuiltinDimensionTypes.OVERWORLD);
    private static final Holder<NoiseGeneratorSettings> SETTINGS = Holder.direct(NoiseGeneratorSettings.dummy());

    private final GraphSettings graphSettings = new GraphSettings();

    private static ResourceKey<IslandVariationList> list(String path) {
        return ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, ResourceLocation.fromNamespaceAndPath("aerolite", path));
    }

    private static LevelStem islandStem(ResourceKey<IslandVariationList> list) {
        return new LevelStem(TYPE, new NoiseBasedChunkGenerator(new IslandBiomeSource(list, VariationLookup.NONE, Optional.empty()), SETTINGS));
    }

    private static WorldDimensions dimensions() {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();
        stems.put(LevelStem.OVERWORLD, islandStem(SHIPPED));
        stems.put(LevelStem.NETHER, islandStem(SHIPPED));
        stems.put(LevelStem.END, new LevelStem(TYPE, new NoiseBasedChunkGenerator(new CheckerboardColumnBiomeSource(HolderSet.direct(), 2), SETTINGS)));
        return new WorldDimensions(stems);
    }

    private static ResourceKey<IslandVariationList> listOf(WorldDimensions dimensions, ResourceKey<LevelStem> stem) {
        return ((IslandBiomeSource) dimensions.dimensions().get(stem).generator().getBiomeSource()).variations();
    }

    @Test
    void islandBiomeSourceTakesTheListOfItsIslandFunction() {
        WorldDimensions before = dimensions();
        WorldDimensions after = this.graphSettings.withIslandLists(before, Map.of(Level.OVERWORLD, EDITED, Level.END, EDITED));
        assertEquals(EDITED, listOf(after, LevelStem.OVERWORLD));
        assertSame(before.dimensions().get(LevelStem.NETHER), after.dimensions().get(LevelStem.NETHER), "A dimension without a list changed");
        assertSame(before.dimensions().get(LevelStem.END), after.dimensions().get(LevelStem.END), "Another biome source changed");
        assertEquals(List.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END), List.copyOf(after.dimensions().keySet()));
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) after.dimensions().get(LevelStem.OVERWORLD).generator();
        assertSame(SETTINGS, generator.generatorSettings(), "The noise settings changed");
        assertSame(TYPE, after.dimensions().get(LevelStem.OVERWORLD).type(), "The dimension type changed");
    }

    @Test
    void sameListKeepsTheDimension() {
        WorldDimensions before = dimensions();
        WorldDimensions after = this.graphSettings.withIslandLists(before, Map.of(Level.OVERWORLD, SHIPPED));
        assertSame(before.dimensions().get(LevelStem.OVERWORLD), after.dimensions().get(LevelStem.OVERWORLD));
        assertTrue(Registries.levelStemToLevel(LevelStem.OVERWORLD).equals(Level.OVERWORLD));
    }

}
