package us.drullk.aerolite.world.island.variation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the codecs of the island variation and the island variation list: the round trip, the defaults, the clamps and the errors.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandVariationCodecTest {

    private static final HolderLookup.Provider REGISTRIES = VanillaRegistries.createLookup();

    private static RegistryOps<JsonElement> ops() {
        return REGISTRIES.createSerializationContext(JsonOps.INSTANCE);
    }

    private static IslandVariation parse(String json) {
        return IslandVariation.CODEC.parse(ops(), JsonParser.parseString(json)).getOrThrow();
    }

    private static JsonObject encode(IslandVariation variation) {
        return IslandVariation.CODEC.encodeStart(ops(), variation).getOrThrow().getAsJsonObject();
    }

    @Test
    void islandRoundTripsEveryField() {
        String json = """
                {"density_function": "minecraft:zero", "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:plains"},
                 "cell_weight": [0.25, 0.75], "fill": [0.4, 0.6], "distortion": 8.0, "distortion_scale": 128, "distortion_octaves": 2}
                """;
        IslandVariation variation = parse(json);
        assertFalse(variation.empty());
        assertEquals(new UnitRange(0.25, 0.75), variation.cellWeight());
        assertEquals(new UnitRange(0.4, 0.6), variation.fill());
        assertEquals(8.0, variation.distortion());
        assertEquals(128, variation.distortionScale());
        assertEquals(2, variation.distortionOctaves());
        assertEquals(JsonParser.parseString(json), encode(variation));
    }

    @Test
    void islandWithoutOptionalFieldsTakesTheBasicDefaults() {
        IslandVariation variation = parse("""
                {"density_function": "minecraft:zero", "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:plains"}}
                """);
        assertEquals(new UnitRange(0.5, 0.5), variation.cellWeight());
        assertEquals(new UnitRange(0.3, 0.9), variation.fill());
        assertEquals(12.0, variation.distortion());
        assertEquals(64, variation.distortionScale());
        assertEquals(3, variation.distortionOctaves());
        assertEquals(Set.of("density_function", "biome_source", "cell_weight", "fill", "distortion", "distortion_scale", "distortion_octaves"),
                encode(variation).keySet(), "The codec does not write every field");
    }

    @Test
    void emptyVariationWritesOnlyItsFields() {
        IslandVariation variation = parse("""
                {"empty": true, "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:the_void"}}
                """);
        assertTrue(variation.empty());
        assertTrue(variation.densityFunction().isEmpty());
        assertEquals(IslandVariation.DEFAULT_CELL_WEIGHT, variation.cellWeight());
        assertEquals(Set.of("empty", "biome_source", "cell_weight"), encode(variation).keySet());
        assertEquals(variation.cellWeight(), parse(encode(variation).toString()).cellWeight());
    }

    @Test
    void rangesSortAndClamp() {
        IslandVariation variation = parse("""
                {"density_function": "minecraft:zero", "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:plains"},
                 "cell_weight": [1.5, -0.2], "fill": [0.8, 0.2]}
                """);
        assertEquals(new UnitRange(0.0, 1.0), variation.cellWeight());
        assertEquals(0.2, variation.fill().min());
        assertEquals(0.8, variation.fill().max());
        assertTrue(UnitRange.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[0.1, 0.2, 0.3]")).error().isPresent(), "A triple parsed as a range");
    }

    @Test
    void islandWithoutDensityFunctionIsAnError() {
        DataResult<IslandVariation> result = IslandVariation.CODEC.parse(ops(), JsonParser.parseString("""
                {"biome_source": {"type": "minecraft:fixed", "biome": "minecraft:plains"}}
                """));
        assertTrue(result.error().isPresent());
        DataResult<IslandVariation> distortion = IslandVariation.CODEC.parse(ops(), JsonParser.parseString("""
                {"density_function": "minecraft:zero", "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:plains"}, "distortion": 64}
                """));
        assertTrue(distortion.error().isPresent(), "A distortion above its bound parsed");
    }

    @Test
    void listRoundTripsInSequence() {
        String json = """
                {"entries": [{"variation": "aerolite:overworld_empty", "weight": 1}, {"variation": "aerolite:overworld_basic", "weight": 9},
                 {"list": "aerolite:forest_group", "weight": 3}]}
                """;
        IslandVariationList list = IslandVariationList.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(List.of(
                IslandVariationList.Entry.variation(variation("overworld_empty"), 1),
                IslandVariationList.Entry.variation(variation("overworld_basic"), 9),
                IslandVariationList.Entry.list(ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION_LIST, id("forest_group")), 3)), list.entries());
        assertEquals(JsonParser.parseString(json), IslandVariationList.CODEC.encodeStart(JsonOps.INSTANCE, list).getOrThrow());
    }

    @Test
    void listRefusesBadEntries() {
        for (String json : List.of(
                "{\"entries\": [{\"variation\": \"aerolite:a\", \"list\": \"aerolite:b\", \"weight\": 1}]}",
                "{\"entries\": [{\"weight\": 1}]}",
                "{\"entries\": [{\"variation\": \"aerolite:a\", \"weight\": 0}]}",
                "{\"entries\": [{\"variation\": \"aerolite:a\", \"weight\": 17}]}",
                "{\"entries\": []}")) {
            assertTrue(IslandVariationList.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), () -> "The list parsed: " + json);
        }
    }

    private static ResourceKey<IslandVariation> variation(String path) {
        return ResourceKey.create(IslandVariationRegistries.ISLAND_VARIATION, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerolite", path);
    }

}
