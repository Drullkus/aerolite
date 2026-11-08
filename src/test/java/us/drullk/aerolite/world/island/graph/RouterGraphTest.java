package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the router graph against the three shipped routers of the Islands preset: the exact round trip, the expanded load, and dimension default on save.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class RouterGraphTest {

    private static JsonObject shipped(String dimension) {
        return GraphTestData.islandNoiseSettings(dimension).getAsJsonObject("noise_router");
    }

    @ParameterizedTest
    @ValueSource(strings = {"overworld", "nether", "end"})
    void shippedRouterSavesAsLoaded(String dimension) {
        JsonObject shipped = shipped(dimension);
        RouterGraph graph = GraphJson.readRouter(shipped).getOrThrow();
        for (String field : RouterGraph.FIELDS) {
            assertInstanceOf(RouterGraph.Output.Linked.class, graph.output(field), field);
        }
        JsonObject saved = GraphJson.writeRouter(graph, shipped).getOrThrow();
        assertEquals(shipped, saved);
        assertEquals(RouterGraph.FIELDS, List.copyOf(saved.keySet()), "The save does not follow the vanilla field sequence");
        assertTrue(GraphValidator.validate(graph, shipped, null).isEmpty(), () -> "Problems: " + GraphValidator.validate(graph, shipped, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"overworld", "nether", "end"})
    void dimensionDefaultOnEveryOutputSavesTheShippedRouter(String dimension) {
        JsonObject shipped = shipped(dimension);
        RouterGraph graph = GraphJson.readRouter(shipped).getOrThrow();
        for (String field : RouterGraph.FIELDS) {
            graph.set(field, new RouterGraph.DimensionDefault());
        }
        assertTrue(graph.paths().isEmpty(), "An output on dimension default kept its function node");
        assertEquals(shipped, GraphJson.writeRouter(graph, shipped).getOrThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"overworld", "nether", "end"})
    void constantOutputChangesOnlyItsField(String dimension) {
        JsonObject shipped = shipped(dimension);
        RouterGraph graph = GraphJson.readRouter(shipped).getOrThrow();
        graph.removeTree(graph.linked("final_density"));
        graph.set("final_density", new RouterGraph.Constant(-1.0));
        JsonObject saved = GraphJson.writeRouter(graph, shipped).getOrThrow();
        JsonObject expected = shipped.deepCopy();
        expected.add("final_density", new JsonPrimitive(-1.0));
        assertEquals(expected, saved);
        assertInstanceOf(RouterGraph.Constant.class, graph.output("final_density"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"overworld", "nether", "end"})
    void shippedRouterLoadsExpanded(String dimension) {
        JsonObject shipped = shipped(dimension);
        RouterGraph graph = GraphJson.readRouter(shipped).getOrThrow();
        for (String field : RouterGraph.FIELDS) {
            Integer root = graph.linked(field);
            assertTrue(root != null, () -> field + " has no link");
            JsonElement value = shipped.get(field);
            FunctionNode functionNode = graph.functionNode(root);
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                assertTrue(functionNode instanceof FunctionNode.Typed typed && typed.bare(), () -> field + " is no constant function node");
            } else if (value.isJsonPrimitive()) {
                assertEquals(new FunctionNode.Reference(ResourceLocation.parse(value.getAsString())), functionNode, field);
            }
            graph.set(field, new RouterGraph.Constant(0.0));
            graph.link(root, field);
            assertInstanceOf(RouterGraph.Output.Linked.class, graph.output(field), field);
        }
        assertTrue(graph.detached().isEmpty(), "A function node of the load is outside the trees");
    }

    @ParameterizedTest
    @ValueSource(strings = {"overworld", "nether", "end"})
    void savedRouterReloadsExpanded(String dimension) {
        JsonObject shipped = shipped(dimension);
        RouterGraph graph = GraphJson.readRouter(shipped).getOrThrow();
        graph.removeTree(graph.linked("depth"));
        graph.set("depth", new RouterGraph.Constant(-1.0));
        graph.removeTree(graph.linked("ridges"));
        assertTrue(graph.link(graph.add(new FunctionNode.Reference(ResourceLocation.withDefaultNamespace("zero"))), "ridges"));
        JsonObject saved = GraphJson.writeRouter(graph, shipped).getOrThrow();
        RouterGraph reloaded = GraphJson.readRouter(saved).getOrThrow();
        for (String field : RouterGraph.FIELDS) {
            assertInstanceOf(RouterGraph.Output.Linked.class, reloaded.output(field), field);
        }
        Integer depth = reloaded.linked("depth");
        assertTrue(depth != null && reloaded.functionNode(depth).equals(FunctionNode.Typed.bareConstant(-1.0)), "The constant output did not load as a constant function node");
        assertEquals(saved, GraphJson.writeRouter(reloaded, shipped).getOrThrow());
    }

    @Test
    void unlinkShowsTheHiddenValue() {
        RouterGraph graph = new RouterGraph(new RouterGraph.Constant(0.0));
        int reference = graph.add(new FunctionNode.Reference(ResourceLocation.withDefaultNamespace("zero")));
        assertTrue(graph.link(reference, "depth"));
        assertFalse(graph.link(reference, "ridges"), "One output fed two router outputs");
        graph.unlink(reference);
        assertEquals(new RouterGraph.Constant(0.0), graph.output("depth"));
    }

}
