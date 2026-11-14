package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import us.drullk.aerolite.world.AeroliteWorldgen;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the function node types against the density function type registry, and the JSON round trip of each type.
 * The saved JSON of each type also parses through the vanilla codec with the vanilla registries, and the vanilla encode gives it back.
 * It checks the field descriptions of the Aerolite types by id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class FunctionNodeTypesTest {

    static Stream<FunctionNodeType> types() {
        return FunctionNodeTypes.all().stream();
    }

    @Test
    void everyRegisteredTypeHasAFieldDescription() {
        Set<ResourceLocation> registered = new HashSet<>(BuiltInRegistries.DENSITY_FUNCTION_TYPE.keySet());
        assertTrue(registered.contains(AeroliteWorldgen.VORONOI_ISLANDS_TYPE), "The registry does not hold aerolite:voronoi_islands");
        for (ResourceLocation id : registered) {
            assertNotNull(FunctionNodeTypes.get(id), "No field description for " + id);
        }
        for (FunctionNodeType type : FunctionNodeTypes.all()) {
            assertTrue(registered.contains(type.id()), "The registry does not hold " + type.id());
        }
    }

    @Test
    void theAeroliteTypesHaveTheirFieldDescriptions() {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("voronoi_islands", List.of("noise", "cell_size", "cell_height", "layer_min_y", "jitter", "fill_min", "fill_max", "empty_chance",
                "distortion", "distortion_scale", "distortion_octaves"));
        expected.put("island_variations", List.of("noise", "cell_size", "cell_height", "layer_min_y", "jitter", "variations"));
        expected.put("island_carrier", List.of("islands", "argument"));
        for (String node : List.of("island_body", "cell_gauge", "wall_distance", "cell_radius", "cell_fill", "cell_weight")) {
            expected.put(node, List.of());
        }
        expected.put("site_offset", List.of("axis"));
        expected.put("site_distance", List.of("plane"));
        expected.put("cell_random", List.of("min", "max", "channel"));
        for (Map.Entry<String, List<String>> type : expected.entrySet()) {
            FunctionNodeType found = FunctionNodeTypes.get(ResourceLocation.fromNamespaceAndPath("aerolite", type.getKey()));
            assertNotNull(found, "No field description for aerolite:" + type.getKey());
            assertEquals(type.getValue(), found.fields().stream().map(FieldSpec::name).toList(), "The fields of aerolite:" + type.getKey());
        }
        FunctionNodeType islands = FunctionNodeTypes.get(AeroliteWorldgen.ISLAND_VARIATIONS_TYPE);
        assertNotNull(islands);
        assertEquals(FieldKind.VARIATION_LIST, islands.fields().getLast().kind());
        FunctionNodeType carrier = FunctionNodeTypes.get(AeroliteWorldgen.ISLAND_CARRIER_TYPE);
        assertNotNull(carrier);
        assertTrue(carrier.fields().stream().allMatch(field -> field.kind() == FieldKind.FUNCTION && field.acceptsReference()));
    }

    @ParameterizedTest
    @MethodSource("types")
    void everyTypeSavesTheLoadedJson(FunctionNodeType type) {
        JsonElement json = GraphTestData.full(type);
        FunctionGraph graph = GraphJson.readFunction(json).getOrThrow();
        JsonElement saved = GraphJson.write(graph).getOrThrow();
        assertEquals(json.toString(), saved.toString());
        assertTrue(GraphValidator.validate(graph, null).isEmpty(), () -> "Problems: " + GraphValidator.validate(graph, null));
    }

    @ParameterizedTest
    @MethodSource("types")
    void everyTypeParsesThroughTheVanillaCodec(FunctionNodeType type) {
        JsonElement saved = GraphJson.write(GraphJson.readFunction(GraphTestData.full(type)).getOrThrow()).getOrThrow();
        DataResult<DensityFunction> parsed = DensityFunction.DIRECT_CODEC.parse(GraphTestData.ops(), saved);
        DensityFunction function = parsed.getOrThrow();
        JsonElement encoded = DensityFunction.DIRECT_CODEC.encodeStart(GraphTestData.ops(), function).getOrThrow();
        JsonElement expected = type.id().equals(FunctionNodeTypes.CONSTANT) ? saved.getAsJsonObject().get("argument") : saved;
        assertEquals(expected, encoded);
    }

    @Test
    void loadRefusesAKeyTheTypeDoesNotHave() {
        JsonElement json = JsonParser.parseString("""
                {"type": "minecraft:add", "argument1": 0.0, "argument2": {"type": "minecraft:abs", "argument": 1.0, "extra": 2.0}}
                """);
        DataResult<FunctionGraph> result = GraphJson.readFunction(json);
        assertEquals("[argument2] The type minecraft:abs has no field extra", result.error().orElseThrow().message());
    }

    @Test
    void bareNumberIsABareConstant() {
        JsonElement json = new JsonPrimitive(0.75);
        FunctionGraph graph = GraphJson.readFunction(json).getOrThrow();
        FunctionNode.Typed functionNode = (FunctionNode.Typed) graph.functionNode(graph.output());
        assertTrue(functionNode.bare());
        assertEquals(FunctionNodeTypes.CONSTANT, functionNode.type().id());
        assertEquals(json, GraphJson.write(graph).getOrThrow());
    }

}
