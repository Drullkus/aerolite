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
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the function node types against the density function type registry, and the JSON round trip of each type.
 * The saved JSON of each type also parses through the vanilla codec with the vanilla registries, and the vanilla encode gives it back.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
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
