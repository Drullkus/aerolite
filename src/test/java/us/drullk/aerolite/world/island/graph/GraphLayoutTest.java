package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the left to right layout of a graph: each function node gets a distinct position, and each child sits one column left of its parent.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class GraphLayoutTest {

    /** The shipped Overworld router with every output loaded as function nodes. */
    private static RouterGraph router() {
        JsonObject shipped = GraphTestData.islandNoiseSettings("overworld").getAsJsonObject("noise_router");
        return GraphJson.readRouter(shipped, new JsonObject()).getOrThrow();
    }

    @Test
    void everyFunctionNodeGetsADistinctPosition() {
        RouterGraph graph = router();
        int detached = graph.add(FunctionNode.Typed.bareConstant(0));
        Map<Integer, Sidecar.Position> positions = GraphLayout.layout(graph);
        assertTrue(positions.keySet().containsAll(graph.functionNodes().keySet()), "A function node has no position");
        assertTrue(positions.containsKey(GraphLayout.BOX), "The box has no position");
        assertTrue(positions.containsKey(detached), "The detached function node has no position");
        Set<Sidecar.Position> distinct = new HashSet<>(positions.values());
        assertEquals(positions.size(), distinct.size(), "Two function nodes share a position");
    }

    @Test
    void eachChildSitsOneColumnLeftOfItsParent() {
        RouterGraph graph = router();
        Map<Integer, Integer> depths = GraphLayout.depths(graph);
        Map<Integer, Sidecar.Position> positions = GraphLayout.layout(graph);
        float box = positions.get(GraphLayout.BOX).x();
        for (int id : graph.functionNodes().keySet()) {
            FunctionNodeGraph.Target target = graph.target(id);
            if (target instanceof FunctionNodeGraph.Target.Input input) {
                int parent = input.pin().functionNode();
                assertEquals(depths.get(parent) + 1, depths.get(id), "A child is not one level deeper than its parent");
                assertEquals(positions.get(parent).x() - GraphLayout.COLUMN_WIDTH, positions.get(id).x(), "A child is not one column left of its parent");
            } else if (target instanceof FunctionNodeGraph.Target.Output) {
                assertEquals(1, depths.get(id), "A root is not at depth 1");
                assertEquals(box - GraphLayout.COLUMN_WIDTH, positions.get(id).x(), "A root is not one column left of the box");
            }
        }
    }

    @Test
    void columnKeepsThePathSequence() {
        RouterGraph graph = router();
        Map<Integer, Integer> depths = GraphLayout.depths(graph);
        Map<Integer, Sidecar.Position> positions = GraphLayout.layout(graph);
        Map<Integer, Float> lastY = new java.util.HashMap<>();
        for (int id : graph.paths().values()) {
            int depth = depths.get(id);
            float y = positions.get(id).y();
            assertTrue(y > lastY.getOrDefault(depth, Float.NEGATIVE_INFINITY), "A column does not stack in path sequence");
            lastY.put(depth, y);
        }
    }

}
