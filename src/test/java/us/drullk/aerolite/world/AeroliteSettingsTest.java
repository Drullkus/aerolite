package us.drullk.aerolite.world;

import com.google.gson.JsonElement;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the codec of the preset settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 */
class AeroliteSettingsTest {

    @Test
    void settingsRoundTrip() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withDecayScale(64).withDecayThreshold(0.35F).withColumnChance(0.6F).withShaftSpacing(64, 96).withErosionThreshold(0.55F).withCloudHeight(200);
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void missingFieldsTakeTheDefaults() {
        assertEquals(AeroliteSettings.DEFAULT, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
    }

    @Test
    void decayScaleIsAPowerOfTwo() {
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"decay_scale\": 48}")).isError());
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"decay_scale\": 2048}")).isError());
    }

    @Test
    void erosionStaysAboveTheDecayThresholdOfAnOldSave() {
        AeroliteSettings settings = AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"decay_threshold\": 0.9}")).getOrThrow();
        assertEquals(0.95F, settings.erosionThreshold(), 1e-6);
    }

    @Test
    void minimumSpacingDoesNotExceedMaximumSpacing() {
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min_shaft_spacing\": 512, \"max_shaft_spacing\": 256}")).isError());
    }

    @Test
    void cloudHeightDefaultsToMinus32() {
        assertEquals(-32, AeroliteSettings.DEFAULT.cloudHeight());
    }

    @Test
    void cloudHeightRoundTrip() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withCloudHeight(136);
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(136, json.getAsJsonObject().get("cloud_height").getAsInt());
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void cloudHeightStaysInsideTheBuildHeight() {
        assertEquals(-64, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": -64}")).getOrThrow().cloudHeight());
        assertEquals(320, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": 320}")).getOrThrow().cloudHeight());
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": -65}")).isError());
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": 321}")).isError());
    }

    private static final ResourceKey<Level> ELSEWHERE = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aerolite", "elsewhere"));
    private static final String STONE_BLOCKS = "\"interior\": {\"Name\": \"minecraft:stone\"}, \"wall\": {\"Name\": \"minecraft:stone\"}, \"path\": {\"Name\": \"minecraft:stone\"}";

    private static DataResult<AeroliteSettings> read(String json) {
        return AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static String overworldList(String... variations) {
        return "{\"variations\": {\"minecraft:overworld\": [" + String.join(", ", variations) + "]}}";
    }

    @Test
    void defaultVariationsCoverTheThreeDimensions() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT;
        assertEquals(List.of(ShaftVariation.STONE, ShaftVariation.TRIAL_CHAMBER), settings.variations(Level.OVERWORLD));
        assertEquals(List.of(ShaftVariation.NETHER), settings.variations(Level.NETHER));
        assertEquals(List.of(ShaftVariation.END), settings.variations(Level.END));
        assertSame(Blocks.OXIDIZED_COPPER, ShaftVariation.TRIAL_CHAMBER.path().getBlock());
    }

    @Test
    void defaultListOfAnotherDimensionIsStoneWithWeightOne() {
        assertEquals(AeroliteSettings.DEFAULT.variations(Level.OVERWORLD), ShaftVariation.defaultList(Level.OVERWORLD));
        assertEquals(AeroliteSettings.DEFAULT.variations(Level.NETHER), ShaftVariation.defaultList(Level.NETHER));
        assertEquals(AeroliteSettings.DEFAULT.variations(Level.END), ShaftVariation.defaultList(Level.END));
        assertEquals(List.of(ShaftVariation.STONE.withWeight(1)), ShaftVariation.defaultList(ELSEWHERE));
    }

    @Test
    void variationMapRoundTrip() {
        ShaftVariation deepslate = new ShaftVariation(4, 0.85F,
                Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        AeroliteSettings settings = AeroliteSettings.DEFAULT
                .withAddedVariation(Level.NETHER, deepslate)
                .withDimension(ELSEWHERE, List.of(deepslate.withWeight(0), ShaftVariation.END));
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertFalse(json.getAsJsonObject().has("shafts_in_unlisted_dimensions"));
        assertTrue(json.getAsJsonObject().getAsJsonObject("variations").has("aerolite:elsewhere"));
        AeroliteSettings parsed = AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(settings, parsed);
        assertEquals(List.of(ShaftVariation.NETHER, deepslate), parsed.variations(Level.NETHER));
    }

    @Test
    void variationReadsBlockStateProperties() {
        ShaftVariation variation = read(overworldList(
                "{\"interior\": {\"Name\": \"minecraft:oak_log\", \"Properties\": {\"axis\": \"x\"}}, \"wall\": {\"Name\": \"minecraft:stone_bricks\"}, \"path\": {\"Name\": \"minecraft:oxidized_copper\"}}"
        )).getOrThrow().variations(Level.OVERWORLD).getFirst();
        assertEquals(1, variation.weight());
        assertEquals(ShaftVariation.UNIFORM_BIAS, variation.bias());
        assertEquals(Direction.Axis.X, variation.interior().getValue(RotatedPillarBlock.AXIS));
    }

    @Test
    void biasClampsToTheSliderRange() {
        assertEquals(ShaftVariation.MIN_BIAS, read(overworldList("{\"bias\": 0, " + STONE_BLOCKS + "}")).getOrThrow().variations(Level.OVERWORLD).getFirst().bias());
        assertEquals(ShaftVariation.MAX_BIAS, read(overworldList("{\"bias\": 1, " + STONE_BLOCKS + "}")).getOrThrow().variations(Level.OVERWORLD).getFirst().bias());
        assertEquals(0.3F, read(overworldList("{\"bias\": 0.3, " + STONE_BLOCKS + "}")).getOrThrow().variations(Level.OVERWORLD).getFirst().bias(), 1e-6);
    }

    @Test
    void variationListNeedsAPositiveWeight() {
        assertTrue(read(overworldList()).isError());
        assertTrue(read(overworldList("{\"weight\": 0, " + STONE_BLOCKS + "}")).isError());
        assertTrue(read(overworldList("{\"weight\": -1, " + STONE_BLOCKS + "}")).isError());
        assertFalse(read(overworldList("{\"weight\": 0, " + STONE_BLOCKS + "}", "{\"weight\": 2, " + STONE_BLOCKS + "}")).isError());
    }

    @Test
    void unlistedDimensionGetsAnEmptyList() {
        assertTrue(AeroliteSettings.DEFAULT.variations(ELSEWHERE).isEmpty());
        AeroliteSettings endOnly = read("{\"variations\": {\"minecraft:the_end\": [{" + STONE_BLOCKS + "}]}}").getOrThrow();
        assertTrue(endOnly.variations(Level.OVERWORLD).isEmpty());
        assertTrue(endOnly.variations(Level.NETHER).isEmpty());
        assertEquals(1, endOnly.variations(Level.END).size());
    }

    @Test
    void oldUnlistedDimensionsFieldIsIgnored() {
        AeroliteSettings settings = read("{\"shafts_in_unlisted_dimensions\": true, \"variations\": {\"minecraft:the_end\": [{" + STONE_BLOCKS + "}]}}").getOrThrow();
        assertEquals(List.of(ELSEWHERE, Level.OVERWORLD, Level.NETHER).stream().map(settings::variations).toList(), List.of(List.of(), List.of(), List.of()));
        assertEquals(AeroliteSettings.DEFAULT, read("{\"shafts_in_unlisted_dimensions\": false}").getOrThrow());
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertFalse(json.getAsJsonObject().has("shafts_in_unlisted_dimensions"));
    }

    @Test
    void withoutDimensionRemovesTheList() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withoutDimension(Level.NETHER);
        assertFalse(settings.variations().containsKey(Level.NETHER));
        assertTrue(settings.variations(Level.NETHER).isEmpty());
        assertEquals(AeroliteSettings.DEFAULT.variations(Level.OVERWORLD), settings.variations(Level.OVERWORLD));
        assertEquals(3, AeroliteSettings.DEFAULT.variations().size());
        assertSame(settings, settings.withoutDimension(ELSEWHERE));
    }

    @Test
    void withDimensionAddsOrReplacesTheList() {
        AeroliteSettings added = AeroliteSettings.DEFAULT.withDimension(ELSEWHERE, List.of(ShaftVariation.END));
        assertEquals(List.of(ShaftVariation.END), added.variations(ELSEWHERE));
        assertEquals(4, added.variations().size());
        AeroliteSettings replaced = added.withDimension(Level.OVERWORLD, List.of(ShaftVariation.NETHER));
        assertEquals(List.of(ShaftVariation.NETHER), replaced.variations(Level.OVERWORLD));
        assertEquals(4, replaced.variations().size());
    }

    @Test
    void settingsWithoutDimensionsRoundTrip() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withoutDimension(Level.OVERWORLD).withoutDimension(Level.NETHER).withoutDimension(Level.END);
        assertTrue(settings.variations().isEmpty());
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void variationEditsReturnNewSettings() {
        AeroliteSettings edited = AeroliteSettings.DEFAULT
                .withVariation(Level.OVERWORLD, 1, ShaftVariation.TRIAL_CHAMBER.withBias(0.75F))
                .withoutVariation(Level.OVERWORLD, 0);
        assertEquals(List.of(ShaftVariation.TRIAL_CHAMBER.withBias(0.75F)), edited.variations(Level.OVERWORLD));
        assertEquals(2, AeroliteSettings.DEFAULT.variations(Level.OVERWORLD).size());
    }

    @Test
    void oldBlockFieldsAreIgnored() {
        assertEquals(AeroliteSettings.DEFAULT, read("{\"interior_block\": {\"Name\": \"minecraft:dirt\"}}").getOrThrow());
    }

}
