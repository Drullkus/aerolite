package us.drullk.aerolite.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftLookup;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.ShippedShaftData;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.WeightedVariation;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the names and the list rules of the add row, of Defaults and of Add Variation over the shipped registries.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
class PresetEditsTest {

    private static final ResourceKey<Level> TWILIGHT = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("twilightforest", "twilight_forest"));

    private final PresetEdits edits = edits();
    private final ShaftLookup lookup = ShippedShaftData.lookup();

    private static PresetEdits edits() {
        PresetEdits edits = new PresetEdits(new PresetSliders());
        edits.useRegistries(ShippedShaftData.registries());
        return edits;
    }

    @Test
    void ownListOfTheVanillaDimensions() {
        assertEquals(Optional.of(ShaftRegistries.OVERWORLD_LIST), this.edits.ownList(Level.OVERWORLD));
        assertEquals(Optional.of(ShaftRegistries.NETHER_LIST), this.edits.ownList(Level.NETHER));
        assertEquals(Optional.of(ShaftRegistries.END_LIST), this.edits.ownList(Level.END));
        assertTrue(this.edits.ownList(TWILIGHT).isEmpty());
    }

    @Test
    void listOfOtherDimensionHoldsItsNamespaceAndPath() {
        ResourceKey<Level> ruins = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("mypack", "ruins"));
        assertEquals(ShaftRegistries.id("mypack/ruins_pillars"), ShaftRegistries.listOf(ruins).location());
        assertEquals(ShaftRegistries.TWILIGHT_LIST, ShaftRegistries.listOf(TWILIGHT));
        assertEquals(ShaftRegistries.id("overworld_pillars"), ShaftRegistries.listOf(Level.OVERWORLD).location());
        assertEquals(ShaftRegistries.id("nether_pillars"), ShaftRegistries.listOf(Level.NETHER).location());
        assertEquals(ShaftRegistries.id("end_pillars"), ShaftRegistries.listOf(Level.END).location());
    }

    @Test
    void listNameReadsItsKeyWithTheFullIdAsFallback() {
        ResourceKey<VariationList> ruins = ResourceKey.create(ShaftRegistries.VARIATION_LIST, ResourceLocation.fromNamespaceAndPath("mypack", "ruins"));
        assertEquals(new TranslatableContents("pillar_variations.mypack.ruins", "mypack:ruins", TranslatableContents.NO_ARGS),
                this.edits.listName(Optional.of(ruins)).getContents());
        assertEquals("mypack:ruins", this.edits.listName(Optional.of(ruins)).getString());
        assertEquals(new TranslatableContents("pillar_variations.aerolite.overworld_pillars", "aerolite:overworld_pillars", TranslatableContents.NO_ARGS),
                this.edits.listName(Optional.of(ShaftRegistries.OVERWORLD_LIST)).getContents());
        assertEquals(new TranslatableContents("createWorld.customize.aerolite.no_prefab", null, TranslatableContents.NO_ARGS),
                this.edits.listName(Optional.empty()).getContents());
    }

    @Test
    void dimensionNameReadsTheDimensionKeyWithThePathAsFallback() {
        assertEquals(new TranslatableContents("dimension.twilightforest.twilight_forest", "twilight_forest", TranslatableContents.NO_ARGS),
                this.edits.dimensionName(TWILIGHT).getContents());
        assertEquals("twilight_forest", this.edits.dimensionName(TWILIGHT).getString());
        assertEquals(new TranslatableContents("createWorld.customize.aerolite.dimension.the_nether", null, TranslatableContents.NO_ARGS),
                this.edits.dimensionName(Level.NETHER).getContents());
    }

    @Test
    void listTooltipNamesTheDimensionOrTheWordDimension() {
        assertEquals(Component.translatable("createWorld.customize.aerolite.prefab_tooltip", this.edits.dimensionName(TWILIGHT)),
                this.edits.listTooltip(TWILIGHT));
        assertEquals(Component.translatable("createWorld.customize.aerolite.prefab_tooltip", Component.translatable("createWorld.customize.aerolite.prefab_dimension")),
                this.edits.listTooltip(null));
    }

    @Test
    void listChoicesStartWithNoPrefab() {
        List<Optional<ResourceKey<VariationList>>> choices = this.edits.listChoices();
        assertEquals(Optional.empty(), choices.getFirst());
        assertEquals(List.of(Optional.of(ShaftRegistries.END_LIST), Optional.of(ShaftRegistries.NETHER_LIST), Optional.of(ShaftRegistries.OVERWORLD_LIST)),
                choices.subList(1, choices.size()));
    }

    @Test
    void loadDefaultsAddsTheDimensionsWithOwnLists() {
        AeroliteSettings blank = ShaftSettingsHolder.BLANK.withCloudHeight(100);
        AeroliteSettings loaded = this.edits.withDefaults(blank, List.of(TWILIGHT, Level.END, Level.NETHER, Level.OVERWORLD));
        assertEquals(List.of(Level.OVERWORLD, Level.NETHER, Level.END), this.edits.configuredDimensions(loaded));
        assertEquals(100, loaded.cloudHeight());
        for (ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            assertEquals(this.lookup.defaults(dimension), loaded.dimension(dimension));
        }
    }

    @Test
    void defaultsKeepAConfiguredDimensionAndAddTheMissingOnes() {
        AeroliteSettings configured = this.edits.withAddedDimension(ShaftSettingsHolder.BLANK, Level.NETHER, Optional.of(ShaftRegistries.OVERWORLD_LIST))
                .withCloudHeight(100);
        DimensionSettings nether = configured.dimension(Level.NETHER);
        assertNotEquals(this.lookup.defaults(Level.NETHER), nether);
        AeroliteSettings loaded = this.edits.withDefaults(configured, List.of(TWILIGHT, Level.END, Level.NETHER, Level.OVERWORLD));
        assertEquals(List.of(Level.OVERWORLD, Level.NETHER, Level.END), this.edits.configuredDimensions(loaded));
        assertEquals(nether, loaded.dimension(Level.NETHER));
        assertEquals(this.lookup.defaults(Level.OVERWORLD), loaded.dimension(Level.OVERWORLD));
        assertEquals(this.lookup.defaults(Level.END), loaded.dimension(Level.END));
        assertEquals(100, loaded.cloudHeight());
    }

    @Test
    void noPrefabAddsStoneWithTheDefaultPlacement() {
        AeroliteSettings added = this.edits.withAddedDimension(ShaftSettingsHolder.BLANK, TWILIGHT, Optional.empty());
        DimensionSettings twilight = added.dimension(TWILIGHT);
        assertEquals(this.lookup.placement(TWILIGHT), twilight.placement());
        assertEquals(List.of(new WeightedVariation(1, ShippedShaftData.variation("stone"))), twilight.variations());
    }

    @Test
    void listAddsItsWeightsWithThePlacementOfTheDimension() {
        AeroliteSettings added = this.edits.withAddedDimension(ShaftSettingsHolder.BLANK, Level.NETHER, Optional.of(ShaftRegistries.OVERWORLD_LIST));
        DimensionSettings nether = added.dimension(Level.NETHER);
        assertEquals(this.lookup.placement(Level.NETHER), nether.placement());
        assertEquals(this.lookup.defaults(Level.OVERWORLD).variations(), nether.variations());
    }

    @Test
    void configuredDimensionStays() {
        AeroliteSettings once = this.edits.withAddedDimension(ShaftSettingsHolder.BLANK, Level.END, Optional.empty());
        assertSame(once, this.edits.withAddedDimension(once, Level.END, Optional.of(ShaftRegistries.END_LIST)));
    }

    @Test
    void addVariationWithoutVariationsAppendsStone() {
        AeroliteSettings empty = ShaftSettingsHolder.BLANK.withDimension(TWILIGHT, DimensionSettings.EMPTY);
        AeroliteSettings added = this.edits.withAddedVariation(empty, TWILIGHT);
        assertEquals(List.of(new WeightedVariation(1, ShippedShaftData.variation("stone"))), added.variations(TWILIGHT));
    }

}
