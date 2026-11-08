package us.drullk.aerolite.world.island.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A left to right layout of a graph by depth. The output box sits in the right column. Each function node sits one column left of its parent.
 * A detached function node without a parent counts as a root, like a function node at a graph output.
 * Within a column, the function nodes stack from the top in the sequence of the outputs and the input pins, then the detached roots by id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class GraphLayout {

    /** The key of the output box in a position map. No function node has a negative id. */
    public static final int BOX = -1;
    /** The distance of two columns, in canvas units. */
    public static final float COLUMN_WIDTH = 320;
    /** The height of a function node without input pins, in canvas units. */
    public static final float ROW_HEIGHT = 80;
    /** The added height of each input pin, in canvas units. */
    public static final float PIN_HEIGHT = 30;

    private GraphLayout() {
    }

    /** The depth of each function node: 1 at a graph output or a detached root, then one more per link. */
    public static Map<Integer, Integer> depths(FunctionNodeGraph graph) {
        Map<Integer, Integer> depths = new LinkedHashMap<>();
        for (int root : roots(graph)) {
            collect(graph, root, 1, depths);
        }
        return depths;
    }

    /** The positions of the output box and of each function node. */
    public static Map<Integer, Sidecar.Position> layout(FunctionNodeGraph graph) {
        Map<Integer, Integer> depths = depths(graph);
        int deepest = depths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Integer, Sidecar.Position> positions = new LinkedHashMap<>();
        positions.put(BOX, new Sidecar.Position(deepest * COLUMN_WIDTH, 0));
        Map<Integer, Float> columnTops = new HashMap<>();
        depths.forEach((id, depth) -> {
            float y = columnTops.getOrDefault(depth, 0F);
            positions.put(id, new Sidecar.Position((deepest - depth) * COLUMN_WIDTH, y));
            columnTops.put(depth, y + ROW_HEIGHT + PIN_HEIGHT * graph.functionNode(id).inputPins().size());
        });
        return positions;
    }

    /** The function nodes at the graph outputs in output sequence, then the detached function nodes without a parent by id. */
    private static List<Integer> roots(FunctionNodeGraph graph) {
        List<Integer> roots = new ArrayList<>();
        for (String output : graph.outputs()) {
            Integer root = graph.outputSource(output);
            if (root != null) {
                roots.add(root);
            }
        }
        for (int id : graph.functionNodes().keySet()) {
            if (graph.target(id) == null && !roots.contains(id)) {
                roots.add(id);
            }
        }
        return roots;
    }

    private static void collect(FunctionNodeGraph graph, int id, int depth, Map<Integer, Integer> depths) {
        depths.put(id, depth);
        for (String pin : graph.functionNode(id).inputPins()) {
            Integer child = graph.source(new FunctionNodeGraph.Pin(id, pin));
            if (child != null) {
                collect(graph, child, depth + 1, depths);
            }
        }
    }

}
