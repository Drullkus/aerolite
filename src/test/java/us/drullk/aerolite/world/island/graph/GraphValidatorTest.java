package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the problems that the validator reports, each with its path.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class GraphValidatorTest {

    private static List<String> problems(String json) {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString(json)).getOrThrow();
        return GraphValidator.validate(graph, Set.of(ResourceLocation.withDefaultNamespace("temperature"))).stream().map(GraphProblem::toString).toList();
    }

    @Test
    void missingOutput() {
        assertEquals(List.of("[/] The output has no function node"),
                GraphValidator.validate(new FunctionGraph(), null).stream().map(GraphProblem::toString).toList());
    }

    @Test
    void missingInput() {
        FunctionGraph graph = new FunctionGraph();
        int abs = graph.add(new FunctionNode.Typed(FunctionNodeTypes.get(ResourceLocation.withDefaultNamespace("abs")), Map.of()));
        graph.setOutput(abs);
        assertEquals(List.of("[argument] The input has no function node"), GraphValidator.validate(graph, null).stream().map(GraphProblem::toString).toList());
    }

    @Test
    void rangeViolations() {
        assertEquals(List.of("[/] The value 2000000.0 is outside -1000000.0 to 1000000.0"), problems("2000000.0"));
        assertEquals(List.of("[argument1/min] The value -2000000.0 is outside -1000000.0 to 1000000.0",
                        "[argument2/smear_scale_multiplier] The value 9.0 is outside 1.0 to 8.0"),
                problems("""
                        {"type": "minecraft:max", "argument1": {"type": "minecraft:clamp", "input": 0.0, "min": -2000000.0, "max": 1.0},
                         "argument2": {"type": "minecraft:old_blended_noise", "xz_scale": 1.0, "y_scale": 1.0, "xz_factor": 80.0, "y_factor": 160.0,
                                       "smear_scale_multiplier": 9.0}}
                        """));
        assertEquals(List.of("[from_y] The value -5000.0 is outside -4064.0 to 4062.0"),
                problems("""
                        {"type": "minecraft:y_clamped_gradient", "from_y": -5000, "to_y": 0, "from_value": 1.0, "to_value": -1.0}
                        """));
    }

    @Test
    void unknownTypeAndUnknownNoise() {
        assertEquals(List.of("[argument1] The type mypack:unknown is unknown", "[argument2/noise] The noise minecraft:vegetation is unknown"),
                problems("""
                        {"type": "minecraft:add", "argument1": {"type": "mypack:unknown", "x": 1},
                         "argument2": {"type": "minecraft:noise", "noise": "minecraft:vegetation", "xz_scale": 1.0, "y_scale": 1.0}}
                        """));
    }

    @Test
    void referenceWhereVanillaReadsNoRegistry() {
        assertEquals(List.of("[input] This input does not take a reference"),
                problems("""
                        {"type": "minecraft:clamp", "input": "minecraft:zero", "min": 0.0, "max": 1.0}
                        """));
        assertEquals(List.of("[/] A density function entry is not a reference"), problems("\"minecraft:zero\""));
    }

    @Test
    void detachedFunctionNodeIsReportedAtItsId() {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString("""
                {"type": "minecraft:abs", "argument": "minecraft:zero"}
                """)).getOrThrow();
        int detached = graph.add(new FunctionNode.Reference(ResourceLocation.withDefaultNamespace("y")));
        assertEquals(List.of("[#" + detached + "] The function node is in no tree under an output, so the save drops it"),
                GraphValidator.validate(graph, null).stream().map(GraphProblem::toString).toList());
        RouterGraph router = new RouterGraph(new RouterGraph.Constant(0.0));
        int routerDetached = router.add(new FunctionNode.Reference(ResourceLocation.withDefaultNamespace("y")));
        assertEquals(List.of(GraphProblem.detached(routerDetached)), GraphValidator.validate(router, null, null));
    }

    @Test
    void routerOnDimensionDefaultNeedsTheShippedField() {
        RouterGraph graph = new RouterGraph(new RouterGraph.DimensionDefault());
        JsonObject shipped = new JsonObject();
        RouterGraph.FIELDS.stream().filter(field -> !field.equals("lava")).forEach(field -> shipped.addProperty(field, 0.0));
        assertEquals(List.of("[lava] The output is on dimension default, and the shipped router has no value for it"),
                GraphValidator.validate(graph, shipped, null).stream().map(GraphProblem::toString).toList());
    }

}
