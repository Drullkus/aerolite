package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Checks the problems that the validator reports, each with its path and its function node.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.

 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
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

    @Test
    void eachProblemNamesTheFunctionNodeThatHoldsIt() {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString("""
                {"type": "minecraft:max", "argument1": {"type": "minecraft:clamp", "input": 0.0, "min": -2000000.0, "max": 1.0},
                 "argument2": {"type": "minecraft:add", "argument1": {"type": "mypack:unknown", "x": 1},
                               "argument2": {"type": "minecraft:clamp", "input": "minecraft:zero", "min": 0.0, "max": 1.0}}}
                """)).getOrThrow();
        int abs = graph.add(new FunctionNode.Typed(FunctionNodeTypes.get(ResourceLocation.withDefaultNamespace("abs")), Map.of()));
        Map<String, Integer> paths = graph.paths();
        List<GraphProblem> problems = GraphValidator.validate(graph, null);
        assertEquals(List.of("[argument1/min] The value -2000000.0 is outside -1000000.0 to 1000000.0",
                "[argument2/argument1] The type mypack:unknown is unknown",
                "[argument2/argument2/input] This input does not take a reference",
                "[#" + abs + "] The function node is in no tree under an output, so the save drops it"), problems.stream().map(GraphProblem::toString).toList());
        assertEquals(paths.get("argument1"), problems.get(0).functionNode());
        assertEquals(paths.get("argument2/argument1"), problems.get(1).functionNode());
        assertEquals(paths.get("argument2/argument2/input"), problems.get(2).functionNode());
        assertEquals(abs, problems.get(3).functionNode());
        for (GraphProblem problem : problems) {
            assertEquals(problem.functionNode(), graph.functionNodeAt(problem.path()), "The path lookup of " + problem);
        }
    }

    @Test
    void missingInputNamesItsFunctionNodeAndMissingOutputNamesNone() {
        FunctionGraph graph = new FunctionGraph();
        int abs = graph.add(new FunctionNode.Typed(FunctionNodeTypes.get(ResourceLocation.withDefaultNamespace("abs")), Map.of()));
        GraphProblem output = GraphValidator.validate(graph, null).getFirst();
        assertEquals("[/] The output has no function node", output.toString());
        assertNull(output.functionNode());
        graph.setOutput(abs);
        assertEquals(abs, GraphValidator.validate(graph, null).getFirst().functionNode());
    }

    @Test
    void routerOutputProblemsNameNoFunctionNode() {
        RouterGraph graph = new RouterGraph(new RouterGraph.DimensionDefault());
        graph.set("lava", new RouterGraph.Constant(2.0E9));
        List<GraphProblem> problems = GraphValidator.validate(graph, null, null);
        assertEquals(RouterGraph.FIELDS.size(), problems.size());
        for (GraphProblem problem : problems) {
            assertNull(problem.functionNode(), problem.toString());
            assertNull(graph.functionNodeAt(problem.path()), problem.toString());
        }
    }

}
