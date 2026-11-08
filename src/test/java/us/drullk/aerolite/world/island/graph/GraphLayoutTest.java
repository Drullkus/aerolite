package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the left to right layout of a graph: each function node gets a distinct position, and each child sits one column left of its parent.
 * Each tree, detached roots too, takes its own band, so no two function nodes of one column overlap.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class GraphLayoutTest {

    /** The shipped Overworld router with every output loaded as function nodes. */
    private static RouterGraph router() {
        JsonObject shipped = GraphTestData.islandNoiseSettings("overworld").getAsJsonObject("noise_router");
        return GraphJson.readRouter(shipped).getOrThrow();
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

    /** The layout height of a function node, from the constants of the layout. */
    private static float height(FunctionNode functionNode) {
        float extra = functionNode instanceof FunctionNode.Reference ? GraphLayout.REFERENCE_HEIGHT : 0;
        return GraphLayout.ROW_HEIGHT + extra + GraphLayout.PIN_HEIGHT * functionNode.inputPins().size();
    }

    @Test
    void functionNodesOfOneColumnDoNotOverlap() {
        RouterGraph graph = router();
        Map<Integer, Sidecar.Position> positions = GraphLayout.layout(graph);
        List<Integer> ids = new ArrayList<>(graph.functionNodes().keySet());
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                Sidecar.Position first = positions.get(ids.get(i));
                Sidecar.Position second = positions.get(ids.get(j));
                if (first.x() != second.x()) {
                    continue;
                }
                boolean apart = first.y() + height(graph.functionNode(ids.get(i))) <= second.y()
                        || second.y() + height(graph.functionNode(ids.get(j))) <= first.y();
                assertTrue(apart, "The function nodes " + ids.get(i) + " and " + ids.get(j) + " overlap in one column");
            }
        }
    }

    @Test
    void detachedRootTakesABandBelowTheOutputTrees() {
        RouterGraph graph = router();
        float bottom = 0;
        Map<Integer, Sidecar.Position> before = GraphLayout.layout(graph);
        for (int id : graph.functionNodes().keySet()) {
            bottom = Math.max(bottom, before.get(id).y() + height(graph.functionNode(id)));
        }
        int abs = graph.add(new FunctionNode.Typed(FunctionNodeTypes.get(ResourceLocation.withDefaultNamespace("abs")), Map.of()));
        int child = graph.add(FunctionNode.Typed.bareConstant(1));
        assertTrue(graph.link(child, new FunctionNodeGraph.Pin(abs, "argument")));
        Map<Integer, Sidecar.Position> positions = GraphLayout.layout(graph);
        assertEquals(bottom, positions.get(abs).y(), "The detached root does not start the band below the output trees");
        assertEquals(bottom, positions.get(child).y(), "The child of the detached root does not stack from its band top");
        assertEquals(positions.get(abs).x() - GraphLayout.COLUMN_WIDTH, positions.get(child).x(), "The child of the detached root is not one column left of it");
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
