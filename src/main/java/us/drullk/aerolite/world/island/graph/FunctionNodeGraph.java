package us.drullk.aerolite.world.island.graph;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The function nodes and the links of one graph. Each function node has one output.
 * The tree rule: each output feeds at most one input pin or graph output, so the linked function nodes form trees.
 * The graph refuses a second link from one output and a link that closes a loop.
 * A shared function node is its own density function entry, and each graph that uses it holds a {@link FunctionNode.Reference}.
 * A function node without a link stays in the graph. A save writes only the trees under the graph outputs.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public abstract sealed class FunctionNodeGraph permits FunctionGraph, RouterGraph {

    /** One input pin of one function node. */
    public record Pin(int functionNode, String name) {
    }

    /** Where the output of one function node goes. */
    public sealed interface Target {

        record Input(Pin pin) implements Target {
        }

        record Output(String output) implements Target {
        }
    }

    private final List<String> outputs;
    private final Map<Integer, FunctionNode> functionNodes = new LinkedHashMap<>();
    private final Map<Pin, Integer> inputLinks = new HashMap<>();
    private final Map<String, Integer> outputLinks = new HashMap<>();
    private final Map<Integer, Target> targets = new HashMap<>();
    private int nextId;

    protected FunctionNodeGraph(List<String> outputs) {
        this.outputs = List.copyOf(outputs);
    }

    /** Copies the function nodes, the links and the next id of the source into this empty graph. */
    protected void copyFrom(FunctionNodeGraph source) {
        if (!this.outputs.equals(source.outputs) || !this.functionNodes.isEmpty()) {
            throw new IllegalStateException("A copy needs an empty graph with the same outputs");
        }
        this.functionNodes.putAll(source.functionNodes);
        this.inputLinks.putAll(source.inputLinks);
        this.outputLinks.putAll(source.outputLinks);
        this.targets.putAll(source.targets);
        this.nextId = source.nextId;
    }

    /** The graph output names. Each is the path of the function node that feeds it. */
    public List<String> outputs() {
        return this.outputs;
    }

    /** Adds a function node without links and gives its id. */
    public int add(FunctionNode functionNode) {
        int id = this.nextId++;
        this.functionNodes.put(id, functionNode);
        return id;
    }

    public FunctionNode functionNode(int id) {
        FunctionNode functionNode = this.functionNodes.get(id);
        if (functionNode == null) {
            throw new NoSuchElementException("No function node " + id);
        }
        return functionNode;
    }

    public Map<Integer, FunctionNode> functionNodes() {
        return Collections.unmodifiableMap(this.functionNodes);
    }

    /** Replaces a function node. The links into pins that the new function node does not have go. */
    public void replace(int id, FunctionNode functionNode) {
        this.functionNode(id);
        this.functionNodes.put(id, functionNode);
        List<String> pins = functionNode.inputPins();
        for (Pin pin : List.copyOf(this.inputLinks.keySet())) {
            if (pin.functionNode() == id && !pins.contains(pin.name())) {
                this.unlink(this.inputLinks.get(pin));
            }
        }
    }

    /** Removes a function node and every link at it. Its children stay without a link. */
    public void remove(int id) {
        this.functionNode(id);
        this.unlink(id);
        for (String pin : this.functionNodes.get(id).inputPins()) {
            Integer child = this.inputLinks.get(new Pin(id, pin));
            if (child != null) {
                this.unlink(child);
            }
        }
        this.functionNodes.remove(id);
    }

    /**
     * Links the output of a function node to an input pin. A link that already feeds the pin goes.
     * Returns false and changes nothing when the output already has a link, the pin does not exist, or the link closes a loop.
     */
    public boolean link(int source, Pin pin) {
        this.functionNode(source);
        if (this.targets.containsKey(source) || !this.functionNodes.containsKey(pin.functionNode()) || !this.functionNode(pin.functionNode()).inputPins().contains(pin.name())
                || this.isAncestorOrSelf(source, pin.functionNode())) {
            return false;
        }
        Integer previous = this.inputLinks.get(pin);
        if (previous != null) {
            this.unlink(previous);
        }
        this.inputLinks.put(pin, source);
        this.targets.put(source, new Target.Input(pin));
        return true;
    }

    /** Links the output of a function node to a graph output, with the rules of {@link #link(int, Pin)}. */
    protected boolean linkOutput(int source, String output) {
        this.functionNode(source);
        if (this.targets.containsKey(source) || !this.outputs.contains(output)) {
            return false;
        }
        Integer previous = this.outputLinks.get(output);
        if (previous != null) {
            this.unlink(previous);
        }
        this.outputLinks.put(output, source);
        this.targets.put(source, new Target.Output(output));
        return true;
    }

    /** Removes the link from the output of a function node, if it has one. */
    public void unlink(int source) {
        Target target = this.targets.remove(source);
        if (target instanceof Target.Input input) {
            this.inputLinks.remove(input.pin());
        } else if (target instanceof Target.Output output) {
            this.outputLinks.remove(output.output());
        }
    }

    /** The function node that feeds a pin, or null. */
    @Nullable
    public Integer source(Pin pin) {
        return this.inputLinks.get(pin);
    }

    /** The function node that feeds a graph output, or null. */
    @Nullable
    public Integer outputSource(String output) {
        return this.outputLinks.get(output);
    }

    /** Where the output of a function node goes, or null. */
    @Nullable
    public Target target(int source) {
        return this.targets.get(source);
    }

    /** True when the function node {@code candidate} is {@code functionNode} or feeds it through links. */
    private boolean isAncestorOrSelf(int candidate, int functionNode) {
        int current = functionNode;
        while (true) {
            if (current == candidate) {
                return true;
            }
            if (this.targets.get(current) instanceof Target.Input input) {
                current = input.pin().functionNode();
            } else {
                return false;
            }
        }
    }

    /** The path of each function node under a graph output, in output sequence and pin sequence. */
    public Map<String, Integer> paths() {
        Map<String, Integer> paths = new LinkedHashMap<>();
        for (String output : this.outputs) {
            Integer root = this.outputLinks.get(output);
            if (root != null) {
                this.collect(root, output, paths);
            }
        }
        return Collections.unmodifiableMap(paths);
    }

    private void collect(int id, String path, Map<String, Integer> paths) {
        paths.put(path, id);
        for (String pin : this.functionNodes.get(id).inputPins()) {
            Integer child = this.inputLinks.get(new Pin(id, pin));
            if (child != null) {
                this.collect(child, childPath(path, pin), paths);
            }
        }
    }

    /** The path of the function node at a pin of the function node at a path. */
    public static String childPath(String parent, String pin) {
        return parent.isEmpty() ? pin : parent + "/" + pin;
    }

    /** The ids of the function nodes in no tree under a graph output. */
    public List<Integer> detached() {
        List<Integer> detached = new ArrayList<>(this.functionNodes.keySet());
        detached.removeAll(this.paths().values());
        return detached;
    }

}
