package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the inline step of the preview build: a reference to an edited entry takes its JSON, an untouched reference stays,
 * and an entry that reaches itself is an error.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class GraphInlineTest {

    private static final ResourceLocation ISLANDS = ResourceLocation.fromNamespaceAndPath("aerolite", "islands/overworld");
    private static final ResourceLocation OTHER = ResourceLocation.fromNamespaceAndPath("aerolite", "other");

    private static JsonObject router() {
        return GraphTestData.islandNoiseSettings("overworld").getAsJsonObject("noise_router");
    }

    @Test
    void referenceToEditedEntryTakesItsJson() {
        JsonObject edited = GraphTestData.islandFunction("overworld");
        edited.addProperty("cell_size", 512);
        JsonObject result = GraphInline.router(router(), Map.of(ISLANDS, DataResult.success(edited))).getOrThrow();
        assertEquals(edited, result.getAsJsonObject("final_density").get("argument"));
        assertEquals(edited, result.getAsJsonObject("initial_density_without_jaggedness").get("argument1"));
        assertFalse(result.toString().contains(ISLANDS.toString()), "A reference to the edited entry stayed");
    }

    @Test
    void untouchedReferenceStaysAString() {
        JsonObject router = router();
        JsonObject result = GraphInline.router(router, Map.of(OTHER, DataResult.success(new JsonPrimitive(1.0)))).getOrThrow();
        assertEquals(router, result);
        assertEquals(new JsonPrimitive("minecraft:overworld/continents"), result.get("continents"));
    }

    @Test
    void noiseIdIsNoReference() {
        ResourceLocation noise = ResourceLocation.fromNamespaceAndPath("aerolite", "islands");
        JsonObject function = GraphTestData.islandFunction("overworld");
        JsonElement result = GraphInline.function(function, Map.of(noise, DataResult.success(new JsonPrimitive(1.0)))).getOrThrow();
        assertEquals(function, result, "The inline replaced a noise id");
    }

    @Test
    void splineCoordinateTakesTheEditedJson() {
        JsonObject spline = new JsonObject();
        spline.addProperty("type", "minecraft:spline");
        spline.add("spline", GraphTestData.spline());
        ResourceLocation erosion = ResourceLocation.withDefaultNamespace("overworld/erosion");
        JsonElement result = GraphInline.function(spline, Map.of(erosion, DataResult.success(new JsonPrimitive(0.5)))).getOrThrow();
        assertFalse(result.toString().contains(erosion.toString()), "The nested spline coordinate kept the reference");
        assertTrue(result.toString().contains("minecraft:overworld/continents"), "The untouched coordinate lost its reference");
    }

    @Test
    void entryThatInlinesItselfIsAnError() {
        Map<ResourceLocation, DataResult<JsonElement>> edited = Map.of(ISLANDS, DataResult.success(new JsonPrimitive(ISLANDS.toString())));
        DataResult<JsonObject> result = GraphInline.router(router(), edited);
        assertTrue(result.error().isPresent(), "A self inline built");
        assertTrue(result.error().get().message().contains("reaches itself"), result.error().get().message());
    }

    @Test
    void loopOverTwoEntriesIsAnError() {
        JsonObject add = new JsonObject();
        add.addProperty("type", "minecraft:add");
        add.addProperty("argument1", ISLANDS.toString());
        add.addProperty("argument2", 1.0);
        Map<ResourceLocation, DataResult<JsonElement>> edited = Map.of(
                ISLANDS, DataResult.success(new JsonPrimitive(OTHER.toString())),
                OTHER, DataResult.success(add));
        assertTrue(GraphInline.router(router(), edited).error().isPresent(), "A loop over two entries built");
    }

    @Test
    void reachedEntryErrorIsAnError() {
        Map<ResourceLocation, DataResult<JsonElement>> edited = Map.of(ISLANDS, DataResult.error(() -> "The output has no function node"));
        DataResult<JsonObject> result = GraphInline.router(router(), edited);
        assertTrue(result.error().isPresent(), "A broken edited entry built");
        assertTrue(result.error().get().message().contains("The output has no function node"), result.error().get().message());
    }

}
