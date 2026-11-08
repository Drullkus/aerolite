package us.drullk.aerolite.world.island.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A left to right layout of a graph by depth. The output box sits in the right column, {@link #BOX_GAP} further right. Each function node sits one column left of its parent.
 * A detached function node without a parent counts as a root, like a function node at a graph output.
 * Each root takes one band of rows below the band of the root before it. The roots follow the output sequence, then the detached roots by id.
 * Within a band, the function nodes of one column stack from the band top in pin sequence. So no two trees share a row.
 * The bands follow the pin sequence of the box, so the links into the box do not cross.
 * The vertical center of the box sits at the average Y of every placed function node of the graph, detached roots too.
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
    /** The added distance of the box column, in canvas units. The vertical runs of the box links clear the depth 1 column. */
    public static final float BOX_GAP = COLUMN_WIDTH / 2;
    /** The added height of a reference node for its entry id and its button Open, in canvas units. */
    public static final float REFERENCE_HEIGHT = 50;

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
        positions.put(BOX, new Sidecar.Position(deepest * COLUMN_WIDTH + BOX_GAP, 0));
        float bandTop = 0;
        float sumY = 0;
        int count = 0;
        for (int root : roots(graph)) {
            Map<Integer, Integer> tree = new LinkedHashMap<>();
            collect(graph, root, 1, tree);
            Map<Integer, Float> columnTops = new HashMap<>();
            float bandBottom = bandTop;
            for (Map.Entry<Integer, Integer> entry : tree.entrySet()) {
                int depth = entry.getValue();
                float y = columnTops.getOrDefault(depth, bandTop);
                positions.put(entry.getKey(), new Sidecar.Position((deepest - depth) * COLUMN_WIDTH, y));
                sumY += y;
                count++;
                float next = y + height(graph.functionNode(entry.getKey()));
                columnTops.put(depth, next);
                bandBottom = Math.max(bandBottom, next);
            }
            bandTop = bandBottom;
        }
        // The box keeps its first map slot, so the replace keeps the map sequence.
        float center = count == 0 ? boxHeight(graph) / 2 : sumY / count;
        positions.put(BOX, new Sidecar.Position(deepest * COLUMN_WIDTH + BOX_GAP, center - boxHeight(graph) / 2));
        return positions;
    }

    /** The layout height of the box: a title row and one input pin per graph output, in canvas units. */
    public static float boxHeight(FunctionNodeGraph graph) {
        return ROW_HEIGHT + PIN_HEIGHT * graph.outputs().size();
    }

    /** The layout height of a function node, in canvas units. */
    private static float height(FunctionNode functionNode) {
        float extra = functionNode instanceof FunctionNode.Reference ? REFERENCE_HEIGHT : 0;
        return ROW_HEIGHT + extra + PIN_HEIGHT * functionNode.inputPins().size();
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
