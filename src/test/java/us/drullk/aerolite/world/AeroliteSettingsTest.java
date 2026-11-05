package us.drullk.aerolite.world;

import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the codec of the preset settings in the current layout and in the 0.1.0 layout.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_18-08_4c0300c8-1a76-4b8e-86b0-0cb9daa7d32a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
class AeroliteSettingsTest {

    private static final ResourceKey<Level> ELSEWHERE = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aerolite", "elsewhere"));
    private static final String STONE_BLOCKS = "\"interior\": {\"Name\": \"minecraft:stone\"}, \"wall\": {\"Name\": \"minecraft:stone\"}, \"path\": {\"Name\": \"minecraft:stone\"}";
    private static final Set<String> CURRENT_KEYS = Set.of("cloud_height", "dimensions");

    private static DataResult<AeroliteSettings> read(String json) {
        return AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    /** Reads with the shipped registries, so settings without dimensions take the default lists. */
    private static DataResult<AeroliteSettings> readWithRegistries(String json) {
        return AeroliteSettings.CODEC.parse(ShippedShaftData.ops(), JsonParser.parseString(json));
    }

    private static AeroliteSettings defaults() {
        return ShippedShaftData.lookup().defaultSettings();
    }

    /** The default variations with the top-level values of a 0.1.0 save without those values. */
    private static AeroliteSettings legacyDefaults() {
        Map<ResourceKey<Level>, DimensionSettings> dimensions = new LinkedHashMap<>();
        defaults().dimensions().forEach((dimension, settings) -> dimensions.put(dimension, new DimensionSettings(ShaftPlacement.DEFAULT, settings.mapVariations(variation -> variation
                .withDecayScale(64)
                .withDecayThreshold(0.35F)
                .withErosionThreshold(0.5F)).variations())));
        return new AeroliteSettings(defaults().cloudHeight(), dimensions);
    }

    private static JsonObject write(AeroliteSettings settings) {
        return AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow().getAsJsonObject();
    }

    /** A save in the current layout whose Overworld holds the variations. */
    private static String overworldList(String... variations) {
        return "{\"dimensions\": {\"minecraft:overworld\": {\"variations\": [" + String.join(", ", variations) + "]}}}";
    }

    /** A save in the 0.1.0 layout whose Overworld holds the variations. */
    private static String oldOverworldList(String... variations) {
        return "{\"variations\": {\"minecraft:overworld\": [" + String.join(", ", variations) + "]}}";
    }

    private static WeightedVariation firstOverworldVariation(String json) {
        return read(json).getOrThrow().variations(Level.OVERWORLD).getFirst();
    }

    @Test
    void settingsRoundTrip() {
        AeroliteSettings settings = TestSettings.SETTINGS
                .withPlacement(Level.OVERWORLD, new ShaftPlacement(64, 96, 0.6F))
                .withPlacement(Level.END, new ShaftPlacement(32, 1024, 0F))
                .withVariation(Level.OVERWORLD, 1, TestSettings.TUFF.withDecayScale(1024).withDecayThreshold(0.1F).withErosionThreshold(0.95F))
                .withCloudHeight(200);
        JsonObject json = write(settings);
        assertEquals(CURRENT_KEYS, json.keySet());
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void missingFieldsTakeTheDefaults() {
        assertEquals(legacyDefaults(), readWithRegistries("{}").getOrThrow());
    }

    @Test
    void missingDimensionsNeedTheRegistries() {
        assertTrue(read("{}").isError());
        assertTrue(read("{\"min_shaft_spacing\": 64}").isError());
    }

    @Test
    void defaultSettingsWriteTheCurrentLayout() {
        JsonObject json = write(defaults());
        assertTrue(json.has("dimensions"));
        assertFalse(json.has("variations"));
        assertEquals(defaults(), AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void defaultsGiveEachVanillaDimensionItsOwnPlacement() {
        assertEquals(new ShaftPlacement(64, 256, 0.3F), defaults().placement(Level.OVERWORLD));
        assertEquals(new ShaftPlacement(96, 192, 0.7F), defaults().placement(Level.NETHER));
        assertEquals(new ShaftPlacement(64, 256, 0.9F), defaults().placement(Level.END));
        assertEquals(-32, defaults().cloudHeight());
    }

    @Test
    void moddedDimensionReadsItsOwnPlacementEntry() {
        assertNotEquals(ShaftPlacement.DEFAULT, ShippedShaftData.lookup().placement(ModdedDimensions.AETHER));
    }

    /** The shipped lists keep the weights and the sequence that existing worlds generated with. */
    @Test
    void defaultListsKeepTheirWeightsAndSequence() {
        assertEquals(List.of(16, 4, 4, 2, 1, 1, 1, 1, 1, 1), defaults().variations(Level.OVERWORLD).stream().map(WeightedVariation::weight).toList());
        assertEquals(List.of(16, 9, 5, 1), defaults().variations(Level.NETHER).stream().map(WeightedVariation::weight).toList());
        assertEquals(List.of(16, 3, 1), defaults().variations(Level.END).stream().map(WeightedVariation::weight).toList());
        assertEquals(ShippedShaftData.variation("stone"), defaults().variations(Level.OVERWORLD).getFirst().variation());
        assertEquals(ShippedShaftData.variation("purpur_pillar"), defaults().variations(Level.END).getLast().variation());
        assertEquals(Blocks.OXIDIZED_COPPER_BULB.defaultBlockState(), TestSettings.state(ShippedShaftData.variation("copper_bridge").path()));
        assertEquals(0.95F, ShippedShaftData.variation("copper_bridge").bias());
    }

    @Test
    void decayScaleIsAPowerOfTwo() {
        assertTrue(read(overworldList("{\"decay_scale\": 48, " + STONE_BLOCKS + "}")).isError());
        assertTrue(read(overworldList("{\"decay_scale\": 2048, " + STONE_BLOCKS + "}")).isError());
        assertTrue(read("{\"decay_scale\": 48}").isError());
        assertTrue(read("{\"decay_scale\": 2048}").isError());
    }

    @Test
    void erosionStaysAboveTheDecayThreshold() {
        ShaftVariation variation = firstOverworldVariation(overworldList("{\"decay_threshold\": 0.9, \"erosion_threshold\": 0.5, " + STONE_BLOCKS + "}")).variation();
        assertEquals(0.95F, variation.erosionThreshold(), 1e-6);
        AeroliteSettings old = readWithRegistries("{\"decay_threshold\": 0.9, \"erosion_threshold\": 0.5}").getOrThrow();
        for (DimensionSettings dimension : old.dimensions().values()) {
            for (WeightedVariation each : dimension.variations()) {
                assertEquals(0.95F, each.variation().erosionThreshold(), 1e-6);
            }
        }
    }

    @Test
    void minimumSpacingDoesNotExceedMaximumSpacing() {
        assertTrue(read("{\"min_shaft_spacing\": 512, \"max_shaft_spacing\": 256}").isError());
        assertTrue(read("{\"dimensions\": {\"minecraft:overworld\": {\"placement\": {\"min_shaft_spacing\": 512, \"max_shaft_spacing\": 256}, \"variations\": [{"
                + STONE_BLOCKS + "}]}}}").isError());
        assertTrue(ShaftPlacement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min_shaft_spacing\": 512, \"max_shaft_spacing\": 256}")).isError());
    }

    @Test
    void cloudHeightRoundTrip() {
        AeroliteSettings settings = TestSettings.SETTINGS.withCloudHeight(136);
        JsonObject json = write(settings);
        assertEquals(136, json.get("cloud_height").getAsInt());
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void cloudHeightStaysInsideTheBuildHeight() {
        assertEquals(-64, readWithRegistries("{\"cloud_height\": -64}").getOrThrow().cloudHeight());
        assertEquals(320, readWithRegistries("{\"cloud_height\": 320}").getOrThrow().cloudHeight());
        assertTrue(readWithRegistries("{\"cloud_height\": -65}").isError());
        assertTrue(readWithRegistries("{\"cloud_height\": 321}").isError());
    }

    @Test
    void oldLayoutFoldsIntoEveryDimension() {
        String json = "{\"min_shaft_spacing\": 64, \"max_shaft_spacing\": 96, \"column_chance\": 0.6,"
                + " \"decay_scale\": 128, \"decay_threshold\": 0.2, \"erosion_threshold\": 0.6, \"cloud_height\": 100,"
                + " \"variations\": {"
                + "\"minecraft:overworld\": [{\"weight\": 2, " + STONE_BLOCKS + "}, {\"decay_scale\": 512, \"decay_threshold\": 0.4, \"erosion_threshold\": 0.9, " + STONE_BLOCKS + "}],"
                + " \"minecraft:the_end\": [{" + STONE_BLOCKS + "}]}}";
        AeroliteSettings settings = read(json).getOrThrow();
        assertEquals(100, settings.cloudHeight());
        assertEquals(Set.of(Level.OVERWORLD, Level.END), settings.dimensions().keySet());
        ShaftPlacement placement = new ShaftPlacement(64, 96, 0.6F);
        assertEquals(placement, settings.placement(Level.OVERWORLD));
        assertEquals(placement, settings.placement(Level.END));

        assertEquals(2, settings.variations(Level.OVERWORLD).get(0).weight());
        ShaftVariation folded = settings.variations(Level.OVERWORLD).get(0).variation();
        assertEquals(128, folded.decayScale());
        assertEquals(0.2F, folded.decayThreshold(), 1e-6);
        assertEquals(0.6F, folded.erosionThreshold(), 1e-6);
        ShaftVariation own = settings.variations(Level.OVERWORLD).get(1).variation();
        assertEquals(512, own.decayScale());
        assertEquals(0.4F, own.decayThreshold(), 1e-6);
        assertEquals(0.9F, own.erosionThreshold(), 1e-6);
        assertEquals(128, settings.variations(Level.END).getFirst().variation().decayScale());

        JsonObject written = write(settings);
        assertEquals(CURRENT_KEYS, written.keySet());
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow());
    }

    @Test
    void oldLayoutWithoutVariationsKeepsTheDefaultDimensions() {
        AeroliteSettings settings = readWithRegistries("{\"min_shaft_spacing\": 64, \"decay_scale\": 256}").getOrThrow();
        assertEquals(defaults().dimensions().keySet(), settings.dimensions().keySet());
        for (DimensionSettings dimension : settings.dimensions().values()) {
            assertEquals(64, dimension.placement().minShaftSpacing());
            assertEquals(ShaftPlacement.DEFAULT.maxShaftSpacing(), dimension.placement().maxShaftSpacing());
            for (WeightedVariation variation : dimension.variations()) {
                assertEquals(256, variation.variation().decayScale());
                assertEquals(ShaftVariation.DEFAULT_DECAY_THRESHOLD, variation.variation().decayThreshold());
            }
        }
    }

    @Test
    void currentLayoutIgnoresTheOldTopLevelFields() {
        AeroliteSettings settings = read("{\"column_chance\": 0.9, \"decay_scale\": 512, \"dimensions\": {\"minecraft:the_end\": {\"variations\": [{"
                + STONE_BLOCKS + "}]}}}").getOrThrow();
        assertEquals(ShaftPlacement.DEFAULT, settings.placement(Level.END));
        assertEquals(ShaftVariation.DEFAULT_DECAY_SCALE, settings.variations(Level.END).getFirst().variation().decayScale());
    }

    @Test
    void placementAndVariationCodecsStandAlone() {
        ShaftPlacement placement = new ShaftPlacement(96, 160, 0.45F);
        assertEquals(placement, ShaftPlacement.CODEC.parse(JsonOps.INSTANCE, ShaftPlacement.CODEC.encodeStart(JsonOps.INSTANCE, placement).getOrThrow()).getOrThrow());
        ShaftVariation variation = TestSettings.TUFF.withDecayScale(32).withDecayThreshold(0.05F).withErosionThreshold(0.2F);
        JsonElement json = ShaftVariation.CODEC.encodeStart(JsonOps.INSTANCE, variation).getOrThrow();
        assertEquals(variation, ShaftVariation.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertTrue(DimensionSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).isError());
    }

    @Test
    void defaultListOfAnotherDimensionIsStoneWithWeightOne() {
        assertEquals(new DimensionSettings(ShaftPlacement.DEFAULT, List.of(TestSettings.one(ShippedShaftData.variation("stone")))),
                ShippedShaftData.lookup().defaults(ELSEWHERE));
    }

    @Test
    void dimensionMapRoundTrip() {
        WeightedVariation deepslate = new WeightedVariation(4, new ShaftVariation(0.85F, 128, 0.25F, 0.4F,
                BlockStateProvider.simple(Blocks.DEEPSLATE), BlockStateProvider.simple(Blocks.CHISELED_DEEPSLATE), BlockStateProvider.simple(Blocks.DEEPSLATE_TILES), BlockStateProvider.simple(Blocks.POLISHED_DEEPSLATE)));
        AeroliteSettings settings = TestSettings.SETTINGS
                .withAddedVariation(Level.NETHER, deepslate)
                .withDimension(ELSEWHERE, List.of(deepslate.withWeight(0), TestSettings.one(TestSettings.END)));
        JsonObject json = write(settings);
        assertFalse(json.has("shafts_in_unlisted_dimensions"));
        assertTrue(json.getAsJsonObject("dimensions").has("aerolite:elsewhere"));
        AeroliteSettings parsed = AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(settings, parsed);
        assertEquals(List.of(TestSettings.one(TestSettings.NETHER), deepslate), parsed.variations(Level.NETHER));
        assertEquals(ShaftPlacement.DEFAULT, parsed.placement(ELSEWHERE));
    }

    @Test
    void variationReadsBlockStateProperties() {
        String oakLog = "{\"interior\": {\"Name\": \"minecraft:oak_log\", \"Properties\": {\"axis\": \"x\"}}, \"wall\": {\"Name\": \"minecraft:stone_bricks\"}, \"path\": {\"Name\": \"minecraft:oxidized_copper\"}}";
        for (String json : List.of(overworldList(oakLog), oldOverworldList(oakLog))) {
            WeightedVariation variation = firstOverworldVariation(json);
            assertEquals(1, variation.weight());
            assertEquals(ShaftVariation.UNIFORM_BIAS, variation.variation().bias());
            assertEquals(Direction.Axis.X, TestSettings.state(variation.variation().interior()).getValue(RotatedPillarBlock.AXIS));
        }
    }

    @Test
    void biasClampsToTheSliderRange() {
        assertEquals(ShaftVariation.MIN_BIAS, firstOverworldVariation(overworldList("{\"bias\": 0, " + STONE_BLOCKS + "}")).variation().bias());
        assertEquals(ShaftVariation.MAX_BIAS, firstOverworldVariation(overworldList("{\"bias\": 1, " + STONE_BLOCKS + "}")).variation().bias());
        assertEquals(0.3F, firstOverworldVariation(overworldList("{\"bias\": 0.3, " + STONE_BLOCKS + "}")).variation().bias(), 1e-6);
        assertEquals(ShaftVariation.MAX_BIAS, firstOverworldVariation(oldOverworldList("{\"bias\": 1, " + STONE_BLOCKS + "}")).variation().bias());
    }

    @Test
    void variationListNeedsAPositiveWeight() {
        for (boolean old : List.of(false, true)) {
            assertTrue(read(old ? oldOverworldList() : overworldList()).isError());
            assertTrue(read(old ? oldOverworldList("{\"weight\": 0, " + STONE_BLOCKS + "}") : overworldList("{\"weight\": 0, " + STONE_BLOCKS + "}")).isError());
            assertTrue(read(old ? oldOverworldList("{\"weight\": -1, " + STONE_BLOCKS + "}") : overworldList("{\"weight\": -1, " + STONE_BLOCKS + "}")).isError());
            String[] mixed = {"{\"weight\": 0, " + STONE_BLOCKS + "}", "{\"weight\": 2, " + STONE_BLOCKS + "}"};
            assertFalse(read(old ? oldOverworldList(mixed) : overworldList(mixed)).isError());
        }
    }

    @Test
    void unlistedDimensionGetsAnEmptyList() {
        assertTrue(TestSettings.SETTINGS.variations(ELSEWHERE).isEmpty());
        AeroliteSettings endOnly = read("{\"dimensions\": {\"minecraft:the_end\": {\"variations\": [{" + STONE_BLOCKS + "}]}}}").getOrThrow();
        assertTrue(endOnly.variations(Level.OVERWORLD).isEmpty());
        assertTrue(endOnly.variations(Level.NETHER).isEmpty());
        assertEquals(1, endOnly.variations(Level.END).size());
    }

    @Test
    void oldUnlistedDimensionsFieldIsIgnored() {
        AeroliteSettings settings = read("{\"shafts_in_unlisted_dimensions\": true, \"variations\": {\"minecraft:the_end\": [{" + STONE_BLOCKS + "}]}}").getOrThrow();
        assertEquals(List.of(ELSEWHERE, Level.OVERWORLD, Level.NETHER).stream().map(settings::variations).toList(), List.of(List.of(), List.of(), List.of()));
        assertEquals(legacyDefaults(), readWithRegistries("{\"shafts_in_unlisted_dimensions\": false}").getOrThrow());
        assertFalse(write(settings).has("shafts_in_unlisted_dimensions"));
    }

    @Test
    void withoutDimensionRemovesTheEntry() {
        AeroliteSettings settings = TestSettings.SETTINGS.withoutDimension(Level.NETHER);
        assertFalse(settings.dimensions().containsKey(Level.NETHER));
        assertTrue(settings.variations(Level.NETHER).isEmpty());
        assertEquals(TestSettings.SETTINGS.variations(Level.OVERWORLD), settings.variations(Level.OVERWORLD));
        assertEquals(3, TestSettings.SETTINGS.dimensions().size());
        assertSame(settings, settings.withoutDimension(ELSEWHERE));
    }

    @Test
    void withDimensionAddsOrReplacesTheList() {
        AeroliteSettings added = TestSettings.SETTINGS.withDimension(ELSEWHERE, List.of(TestSettings.one(TestSettings.END)));
        assertEquals(List.of(TestSettings.one(TestSettings.END)), added.variations(ELSEWHERE));
        assertEquals(ShaftPlacement.DEFAULT, added.placement(ELSEWHERE));
        assertEquals(4, added.dimensions().size());
        AeroliteSettings replaced = added.withDimension(Level.OVERWORLD, List.of(TestSettings.one(TestSettings.NETHER)));
        assertEquals(List.of(TestSettings.one(TestSettings.NETHER)), replaced.variations(Level.OVERWORLD));
        assertEquals(TestSettings.PLACEMENT, replaced.placement(Level.OVERWORLD));
        assertEquals(4, replaced.dimensions().size());
    }

    @Test
    void withPlacementChangesOnlyAListedDimension() {
        ShaftPlacement placement = new ShaftPlacement(32, 64, 1F);
        AeroliteSettings edited = TestSettings.SETTINGS.withPlacement(Level.NETHER, placement);
        assertEquals(placement, edited.placement(Level.NETHER));
        assertEquals(TestSettings.PLACEMENT, edited.placement(Level.OVERWORLD));
        assertEquals(TestSettings.SETTINGS.variations(Level.NETHER), edited.variations(Level.NETHER));
        assertSame(TestSettings.SETTINGS, TestSettings.SETTINGS.withPlacement(ELSEWHERE, placement));
    }

    @Test
    void settingsWithoutDimensionsRoundTrip() {
        AeroliteSettings settings = TestSettings.SETTINGS.withoutDimension(Level.OVERWORLD).withoutDimension(Level.NETHER).withoutDimension(Level.END);
        assertTrue(settings.dimensions().isEmpty());
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, write(settings)).getOrThrow());
    }

    @Test
    void variationEditsReturnNewSettings() {
        AeroliteSettings edited = TestSettings.SETTINGS
                .withVariation(Level.OVERWORLD, 1, TestSettings.TUFF.withBias(0.75F))
                .withoutVariation(Level.OVERWORLD, 0);
        assertEquals(List.of(TestSettings.one(TestSettings.TUFF.withBias(0.75F))), edited.variations(Level.OVERWORLD));
        assertEquals(TestSettings.OVERWORLD.variations(), TestSettings.SETTINGS.variations(Level.OVERWORLD));
        assertEquals(5, TestSettings.SETTINGS.withWeight(Level.OVERWORLD, 1, 5).variations(Level.OVERWORLD).get(1).weight());
        assertEquals(TestSettings.TUFF, TestSettings.SETTINGS.withWeight(Level.OVERWORLD, 1, 5).variations(Level.OVERWORLD).get(1).variation());
    }

    @Test
    void oldBlockFieldsAreIgnored() {
        assertEquals(legacyDefaults(), readWithRegistries("{\"interior_block\": {\"Name\": \"minecraft:dirt\"}}").getOrThrow());
    }

    @Test
    void variationEntryWritesEveryField() {
        JsonObject json = ShaftVariation.CODEC.encodeStart(JsonOps.INSTANCE, ShippedShaftData.variation("stone")).getOrThrow().getAsJsonObject();
        assertEquals(List.of("bias", "decay_scale", "decay_threshold", "erosion_threshold", "pattern", "shape",
                "interior", "junction", "wall", "path", "surface", "trusses"), List.copyOf(json.keySet()));
        assertFalse(json.has("weight"));
        JsonObject placement = ShaftPlacement.CODEC.encodeStart(JsonOps.INSTANCE, ShaftPlacement.DEFAULT).getOrThrow().getAsJsonObject();
        assertEquals(Set.of("min_shaft_spacing", "max_shaft_spacing", "column_chance"), placement.keySet());
    }

    @Test
    void variationEntryWithoutJunctionTakesTheInteriorBlock() {
        ShaftVariation variation = ShaftVariation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + STONE_BLOCKS + "}")).getOrThrow();
        assertEquals(TestSettings.state(variation.interior()), TestSettings.state(variation.junction()));
        // A save of 0.2 holds no junction block. Its variation still equals the shipped entry.
        ShaftVariation stone = ShippedShaftData.variation("stone");
        JsonObject old = ShaftVariation.CODEC.encodeStart(JsonOps.INSTANCE, stone).getOrThrow().getAsJsonObject();
        old.remove("junction");
        assertEquals(stone, ShaftVariation.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow());
        ShaftVariation chiseled = ShaftVariation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"junction\": {\"Name\": \"minecraft:chiseled_stone_bricks\"}, " + STONE_BLOCKS + "}")).getOrThrow();
        assertEquals(Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), TestSettings.state(chiseled.junction()));
        assertEquals(Blocks.STONE.defaultBlockState(), TestSettings.state(chiseled.interior()));
    }

    @Test
    void shippedEntriesTakeTheirInteriorAsJunctionBlock() {
        for (String name : ShippedShaftData.VARIATIONS) {
            ShaftVariation variation = ShippedShaftData.variation(name);
            assertEquals(TestSettings.state(variation.interior()), TestSettings.state(variation.junction()), name);
        }
    }

    @Test
    void variationEntryKeepsTheErosionAboveTheDecay() {
        ShaftVariation variation = ShaftVariation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"decay_threshold\": 0.9, \"erosion_threshold\": 0.5, " + STONE_BLOCKS + "}")).getOrThrow();
        assertEquals(0.95F, variation.erosionThreshold(), 1e-6);
        assertEquals(ShaftVariation.MAX_BIAS, ShaftVariation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"bias\": 2, " + STONE_BLOCKS + "}")).getOrThrow().bias());
    }

    /** The JSON sequence of a variation list is its pick sequence. */
    @Test
    void variationListKeepsItsSequence() {
        List<String> names = ShippedShaftData.VARIATIONS.reversed();
        JsonObject json = new JsonObject();
        for (int index = 0; index < names.size(); index++) {
            json.addProperty("aerolite:" + names.get(index), index);
        }
        VariationList list = VariationList.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(names, list.entries().stream().map(entry -> entry.variation().location().getPath()).toList());
        assertEquals(json, VariationList.CODEC.encodeStart(JsonOps.INSTANCE, list).getOrThrow());
    }

    @Test
    void variationListNeedsAPositiveWeightToo() {
        assertTrue(VariationList.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).isError());
        assertTrue(VariationList.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"aerolite:stone\": 0}")).isError());
        assertTrue(VariationList.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"aerolite:stone\": -1}")).isError());
        assertFalse(VariationList.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"aerolite:stone\": 0, \"aerolite:tuff\": 1}")).isError());
    }

}
