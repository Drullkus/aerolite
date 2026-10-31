package us.drullk.aerolite.world;

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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 */
class AeroliteSettingsTest {

    private static final ResourceKey<Level> ELSEWHERE = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aerolite", "elsewhere"));
    private static final String STONE_BLOCKS = "\"interior\": {\"Name\": \"minecraft:stone\"}, \"wall\": {\"Name\": \"minecraft:stone\"}, \"path\": {\"Name\": \"minecraft:stone\"}";
    private static final Set<String> CURRENT_KEYS = Set.of("cloud_height", "dimensions");

    private static DataResult<AeroliteSettings> read(String json) {
        return AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
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

    private static ShaftVariation firstOverworldVariation(String json) {
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
        assertEquals(AeroliteSettings.DEFAULT, read("{}").getOrThrow());
    }

    @Test
    void defaultSettingsWriteTheCurrentLayout() {
        JsonObject json = write(AeroliteSettings.DEFAULT);
        assertTrue(json.has("dimensions"));
        assertFalse(json.has("variations"));
        assertEquals(AeroliteSettings.DEFAULT, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void defaultsGiveEveryDimensionTheDefaultPlacementAndDecay() {
        for (ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            assertEquals(new ShaftPlacement(48, 80, 0.3F), AeroliteSettings.DEFAULT.placement(dimension));
            assertEquals(ShaftVariation.defaultList(dimension), AeroliteSettings.DEFAULT.variations(dimension));
            for (ShaftVariation variation : AeroliteSettings.DEFAULT.variations(dimension)) {
                assertEquals(64, variation.decayScale());
                assertEquals(0.35F, variation.decayThreshold());
                assertEquals(0.5F, variation.erosionThreshold());
            }
        }
        assertEquals(-32, AeroliteSettings.DEFAULT.cloudHeight());
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
        ShaftVariation variation = firstOverworldVariation(overworldList("{\"decay_threshold\": 0.9, \"erosion_threshold\": 0.5, " + STONE_BLOCKS + "}"));
        assertEquals(0.95F, variation.erosionThreshold(), 1e-6);
        AeroliteSettings old = read("{\"decay_threshold\": 0.9, \"erosion_threshold\": 0.5}").getOrThrow();
        for (DimensionSettings dimension : old.dimensions().values()) {
            for (ShaftVariation each : dimension.variations()) {
                assertEquals(0.95F, each.erosionThreshold(), 1e-6);
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
        assertEquals(-64, read("{\"cloud_height\": -64}").getOrThrow().cloudHeight());
        assertEquals(320, read("{\"cloud_height\": 320}").getOrThrow().cloudHeight());
        assertTrue(read("{\"cloud_height\": -65}").isError());
        assertTrue(read("{\"cloud_height\": 321}").isError());
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

        ShaftVariation folded = settings.variations(Level.OVERWORLD).get(0);
        assertEquals(2, folded.weight());
        assertEquals(128, folded.decayScale());
        assertEquals(0.2F, folded.decayThreshold(), 1e-6);
        assertEquals(0.6F, folded.erosionThreshold(), 1e-6);
        ShaftVariation own = settings.variations(Level.OVERWORLD).get(1);
        assertEquals(512, own.decayScale());
        assertEquals(0.4F, own.decayThreshold(), 1e-6);
        assertEquals(0.9F, own.erosionThreshold(), 1e-6);
        assertEquals(128, settings.variations(Level.END).getFirst().decayScale());

        JsonObject written = write(settings);
        assertEquals(CURRENT_KEYS, written.keySet());
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow());
    }

    @Test
    void oldLayoutWithoutVariationsKeepsTheDefaultDimensions() {
        AeroliteSettings settings = read("{\"min_shaft_spacing\": 64, \"decay_scale\": 256}").getOrThrow();
        assertEquals(AeroliteSettings.DEFAULT.dimensions().keySet(), settings.dimensions().keySet());
        for (DimensionSettings dimension : settings.dimensions().values()) {
            assertEquals(64, dimension.placement().minShaftSpacing());
            assertEquals(ShaftPlacement.DEFAULT.maxShaftSpacing(), dimension.placement().maxShaftSpacing());
            for (ShaftVariation variation : dimension.variations()) {
                assertEquals(256, variation.decayScale());
                assertEquals(ShaftVariation.DEFAULT_DECAY_THRESHOLD, variation.decayThreshold());
            }
        }
    }

    @Test
    void currentLayoutIgnoresTheOldTopLevelFields() {
        AeroliteSettings settings = read("{\"column_chance\": 0.9, \"decay_scale\": 512, \"dimensions\": {\"minecraft:the_end\": {\"variations\": [{"
                + STONE_BLOCKS + "}]}}}").getOrThrow();
        assertEquals(ShaftPlacement.DEFAULT, settings.placement(Level.END));
        assertEquals(ShaftVariation.DEFAULT_DECAY_SCALE, settings.variations(Level.END).getFirst().decayScale());
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
        assertEquals(List.of(ShaftVariation.STONE.withWeight(1)), ShaftVariation.defaultList(ELSEWHERE));
        assertEquals(new DimensionSettings(ShaftPlacement.DEFAULT, List.of(ShaftVariation.STONE.withWeight(1))), DimensionSettings.defaults(ELSEWHERE));
    }

    @Test
    void dimensionMapRoundTrip() {
        ShaftVariation deepslate = new ShaftVariation(4, 0.85F, 128, 0.25F, 0.4F,
                Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        AeroliteSettings settings = TestSettings.SETTINGS
                .withAddedVariation(Level.NETHER, deepslate)
                .withDimension(ELSEWHERE, List.of(deepslate.withWeight(0), TestSettings.END));
        JsonObject json = write(settings);
        assertFalse(json.has("shafts_in_unlisted_dimensions"));
        assertTrue(json.getAsJsonObject("dimensions").has("aerolite:elsewhere"));
        AeroliteSettings parsed = AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(settings, parsed);
        assertEquals(List.of(TestSettings.NETHER, deepslate), parsed.variations(Level.NETHER));
        assertEquals(ShaftPlacement.DEFAULT, parsed.placement(ELSEWHERE));
    }

    @Test
    void variationReadsBlockStateProperties() {
        String oakLog = "{\"interior\": {\"Name\": \"minecraft:oak_log\", \"Properties\": {\"axis\": \"x\"}}, \"wall\": {\"Name\": \"minecraft:stone_bricks\"}, \"path\": {\"Name\": \"minecraft:oxidized_copper\"}}";
        for (String json : List.of(overworldList(oakLog), oldOverworldList(oakLog))) {
            ShaftVariation variation = firstOverworldVariation(json);
            assertEquals(1, variation.weight());
            assertEquals(ShaftVariation.UNIFORM_BIAS, variation.bias());
            assertEquals(Direction.Axis.X, variation.interior().getValue(RotatedPillarBlock.AXIS));
        }
    }

    @Test
    void biasClampsToTheSliderRange() {
        assertEquals(ShaftVariation.MIN_BIAS, firstOverworldVariation(overworldList("{\"bias\": 0, " + STONE_BLOCKS + "}")).bias());
        assertEquals(ShaftVariation.MAX_BIAS, firstOverworldVariation(overworldList("{\"bias\": 1, " + STONE_BLOCKS + "}")).bias());
        assertEquals(0.3F, firstOverworldVariation(overworldList("{\"bias\": 0.3, " + STONE_BLOCKS + "}")).bias(), 1e-6);
        assertEquals(ShaftVariation.MAX_BIAS, firstOverworldVariation(oldOverworldList("{\"bias\": 1, " + STONE_BLOCKS + "}")).bias());
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
        assertEquals(AeroliteSettings.DEFAULT, read("{\"shafts_in_unlisted_dimensions\": false}").getOrThrow());
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
        AeroliteSettings added = TestSettings.SETTINGS.withDimension(ELSEWHERE, List.of(TestSettings.END));
        assertEquals(List.of(TestSettings.END), added.variations(ELSEWHERE));
        assertEquals(ShaftPlacement.DEFAULT, added.placement(ELSEWHERE));
        assertEquals(4, added.dimensions().size());
        AeroliteSettings replaced = added.withDimension(Level.OVERWORLD, List.of(TestSettings.NETHER));
        assertEquals(List.of(TestSettings.NETHER), replaced.variations(Level.OVERWORLD));
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
        assertEquals(List.of(TestSettings.TUFF.withBias(0.75F)), edited.variations(Level.OVERWORLD));
        assertEquals(List.of(TestSettings.STONE, TestSettings.TUFF), TestSettings.SETTINGS.variations(Level.OVERWORLD));
    }

    @Test
    void oldBlockFieldsAreIgnored() {
        assertEquals(AeroliteSettings.DEFAULT, read("{\"interior_block\": {\"Name\": \"minecraft:dirt\"}}").getOrThrow());
    }

}
