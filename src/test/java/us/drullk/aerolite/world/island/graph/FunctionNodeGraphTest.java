package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the tree rule of a graph, the tree removal, the paths of its function nodes, the path lookup and the sidecar round trip. The graphs are arbitrary structures.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.

 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
class FunctionNodeGraphTest {

    private static final String NESTED = """
            {"type": "minecraft:add",
             "argument1": {"type": "minecraft:abs", "argument": "minecraft:shift_x"},
             "argument2": {"type": "minecraft:spline", "spline": {"coordinate": "minecraft:shift_z", "points": [
                 {"location": 0.0, "value": {"coordinate": 0.5, "points": [{"location": 1.0, "value": 2.0, "derivative": 0.0}]}, "derivative": 0.0}]}}}
            """;

    private static FunctionNode.Typed typed(String path) {
        FunctionNodeType type = FunctionNodeTypes.get(ResourceLocation.withDefaultNamespace(path));
        return new FunctionNode.Typed(type, Map.of());
    }

    @Test
    void secondLinkFromOneOutputIsRefused() {
        FunctionGraph graph = new FunctionGraph();
        int add = graph.add(typed("add"));
        int reference = graph.add(new FunctionNode.Reference(ResourceLocation.withDefaultNamespace("zero")));
        assertTrue(graph.link(reference, new FunctionNodeGraph.Pin(add, "argument1")));
        assertFalse(graph.link(reference, new FunctionNodeGraph.Pin(add, "argument2")), "One output fed two inputs");
        assertFalse(graph.setOutput(reference), "One output fed an input and the graph output");
        assertNull(graph.source(new FunctionNodeGraph.Pin(add, "argument2")));
        assertEquals(reference, graph.source(new FunctionNodeGraph.Pin(add, "argument1")));
    }

    @Test
    void linkIntoAnOccupiedPinReplacesTheOldLink() {
        FunctionGraph graph = new FunctionGraph();
        int abs = graph.add(typed("abs"));
        int first = graph.add(new FunctionNode.Reference(ResourceLocation.withDefaultNamespace("zero")));
        int second = graph.add(new FunctionNode.Reference(ResourceLocation.withDefaultNamespace("y")));
        FunctionNodeGraph.Pin pin = new FunctionNodeGraph.Pin(abs, "argument");
        assertTrue(graph.setOutput(abs));
        assertTrue(graph.link(first, pin));
        assertTrue(graph.link(second, pin));
        assertEquals(second, graph.source(pin));
        assertNull(graph.target(first));
        assertEquals(List.of(first), graph.detached());
        assertEquals(List.of(GraphProblem.detached(first)), GraphValidator.validate(graph, null));
    }

    @Test
    void loopIsRefused() {
        FunctionGraph graph = new FunctionGraph();
        int outer = graph.add(typed("abs"));
        int inner = graph.add(typed("square"));
        assertTrue(graph.link(inner, new FunctionNodeGraph.Pin(outer, "argument")));
        assertFalse(graph.link(outer, new FunctionNodeGraph.Pin(inner, "argument")), "A link closed a loop");
        assertFalse(graph.link(inner, new FunctionNodeGraph.Pin(inner, "argument")), "A function node fed itself");
    }

    @Test
    void removeKeepsTheChildrenWithoutALink() {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString(NESTED)).getOrThrow();
        int root = graph.output();
        graph.remove(root);
        assertNull(graph.output());
        assertEquals(graph.functionNodes().size(), graph.detached().size());
    }

    @Test
    void removeTreeRemovesTheChainUnderItsRootOnly() {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString(NESTED)).getOrThrow();
        int add = graph.output();
        int abs = graph.paths().get("argument1");
        int shiftX = graph.paths().get("argument1/argument");
        int spline = graph.paths().get("argument2");
        graph.removeTree(abs);
        assertFalse(graph.functionNodes().containsKey(abs), "The root of the removed tree stays");
        assertFalse(graph.functionNodes().containsKey(shiftX), "A function node under the removed root stays");
        assertNull(graph.source(new FunctionNodeGraph.Pin(add, "argument1")), "The pin of the removed root keeps a link");
        assertEquals(spline, graph.source(new FunctionNodeGraph.Pin(add, "argument2")), "The sibling tree lost its link");
        assertTrue(graph.detached().isEmpty(), "The removal left a detached function node");
        graph.removeTree(add);
        assertNull(graph.output());
        assertTrue(graph.functionNodes().isEmpty(), "The removal of the whole tree left function nodes");
    }

    @Test
    void pathsFollowTheFieldsAndTheSplineLevels() {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString(NESTED)).getOrThrow();
        assertEquals(List.of("", "argument1", "argument1/argument", "argument2", "argument2/spline/coordinate",
                "argument2/spline/points/0/value/coordinate"), List.copyOf(graph.paths().keySet()));
    }

    @Test
    void sidecarRoundTripsThePositions() {
        JsonElement json = JsonParser.parseString(NESTED);
        FunctionGraph graph = GraphJson.readFunction(json).getOrThrow();
        Map<Integer, Sidecar.Position> byFunctionNode = new HashMap<>();
        Map<String, Integer> paths = graph.paths();
        float x = 0.0F;
        for (Map.Entry<String, Integer> entry : paths.entrySet()) {
            if (!entry.getKey().equals("argument1")) {
                byFunctionNode.put(entry.getValue(), new Sidecar.Position(x, -x));
            }
            x += 10.0F;
        }
        Sidecar sidecar = Sidecar.of(graph, byFunctionNode);
        JsonElement encoded = Sidecar.CODEC.encodeStart(JsonOps.INSTANCE, sidecar).getOrThrow();
        Sidecar decoded = Sidecar.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(sidecar, decoded);
        assertFalse(decoded.positions().containsKey("argument1"), "A function node without a position got one");

        FunctionGraph reloaded = GraphJson.readFunction(GraphJson.write(graph).getOrThrow()).getOrThrow();
        Map<Integer, Sidecar.Position> applied = decoded.apply(reloaded);
        Map<String, Integer> reloadedPaths = reloaded.paths();
        for (Map.Entry<String, Integer> entry : paths.entrySet()) {
            assertEquals(byFunctionNode.get(entry.getValue()), applied.get(reloadedPaths.get(entry.getKey())), "The position of " + entry.getKey() + " moved");
        }
    }

    @Test
    void replaceDropsTheLinksOfRemovedSplinePins() {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString(NESTED)).getOrThrow();
        int spline = graph.paths().get("argument2");
        int nested = graph.paths().get("argument2/spline/points/0/value/coordinate");
        FunctionNode.Typed functionNode = (FunctionNode.Typed) graph.functionNode(spline);
        SplineValue.Multipoint outer = (SplineValue.Multipoint) ((FieldValue.Spline) functionNode.values().get("spline")).value();
        SplineValue flat = new SplineValue.Multipoint(List.of(new SplineValue.Point(0.0, new SplineValue.Constant(1.0), 0.0)));
        assertEquals(1, outer.points().size());
        graph.replace(spline, functionNode.with("spline", new FieldValue.Spline(flat)));
        assertNull(graph.target(nested));
        assertEquals(List.of("spline/coordinate"), graph.functionNode(spline).inputPins());
    }

    @Test
    void functionNodeAtNamesTheDeepestFunctionNodeOnThePath() {
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString(NESTED)).getOrThrow();
        Map<String, Integer> paths = graph.paths();
        paths.forEach((path, id) -> assertEquals(id, graph.functionNodeAt(path), path));
        assertEquals(paths.get("argument1/argument"), graph.functionNodeAt("argument1/argument/x"));
        assertEquals(paths.get("argument2"), graph.functionNodeAt("argument2/spline/points/0/value"));
        assertEquals(paths.get(""), graph.functionNodeAt("unknown_pin"));
        int detached = graph.add(typed("abs"));
        assertEquals(detached, graph.functionNodeAt("#" + detached));
        assertNull(graph.functionNodeAt("#" + (detached + 1)));
        assertNull(graph.functionNodeAt("#text"));
        assertNull(new FunctionGraph().functionNodeAt(""));
        RouterGraph router = new RouterGraph(new RouterGraph.Constant(0.0));
        assertNull(router.functionNodeAt("continents"));
        assertNull(router.functionNodeAt(""));
    }

}
