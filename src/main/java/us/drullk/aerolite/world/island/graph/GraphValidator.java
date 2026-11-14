package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports why a graph does not build: an output or an input pin without a function node, a value out of its vanilla range,
 * an unknown type id, an unknown noise id, a reference where vanilla reads no registry, or a spline without points.
 * A null noise id set skips the noise id check. Each function node outside the trees gives one problem, because the save drops it.
 * Each problem names the function node that holds it. A problem of a graph output without a function node names none.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class GraphValidator {

    private GraphValidator() {
    }

    public static List<GraphProblem> validate(FunctionGraph graph, @Nullable Set<ResourceLocation> noises) {
        List<GraphProblem> problems = new ArrayList<>();
        Integer root = graph.output();
        if (root == null) {
            problems.add(new GraphProblem(FunctionGraph.OUTPUT, "The output has no function node"));
        } else {
            if (graph.functionNode(root) instanceof FunctionNode.Reference) {
                problems.add(new GraphProblem(FunctionGraph.OUTPUT, "A density function entry is not a reference", root));
            }
            check(graph, root, FunctionGraph.OUTPUT, noises, problems);
        }
        detached(graph, problems);
        return problems;
    }

    /** Validates a router graph. A null shipped router reports each output on dimension default. */
    public static List<GraphProblem> validate(RouterGraph graph, @Nullable JsonObject shipped, @Nullable Set<ResourceLocation> noises) {
        List<GraphProblem> problems = new ArrayList<>();
        for (String field : RouterGraph.FIELDS) {
            RouterGraph.Output output = graph.output(field);
            if (output instanceof RouterGraph.Output.Linked linked) {
                check(graph, linked.functionNode(), field, noises, problems);
            } else if (output instanceof RouterGraph.Constant constant) {
                range(field, null, constant.value(), -FieldSpec.NOISE_VALUE_LIMIT, FieldSpec.NOISE_VALUE_LIMIT, problems);
            } else if (shipped == null || !shipped.has(field)) {
                problems.add(new GraphProblem(field, "The output is on dimension default, and the shipped router has no value for it"));
            }
        }
        detached(graph, problems);
        return problems;
    }

    private static void detached(FunctionNodeGraph graph, List<GraphProblem> problems) {
        for (int functionNode : graph.detached()) {
            problems.add(GraphProblem.detached(functionNode));
        }
    }

    private static void check(FunctionNodeGraph graph, int id, String path, @Nullable Set<ResourceLocation> noises, List<GraphProblem> problems) {
        FunctionNode functionNode = graph.functionNode(id);
        if (functionNode instanceof FunctionNode.Unknown unknown) {
            problems.add(new GraphProblem(path, "The type " + unknown.typeId() + " is unknown", id));
            return;
        }
        if (!(functionNode instanceof FunctionNode.Typed typed)) {
            return;
        }
        for (FieldSpec field : typed.type().fields()) {
            String fieldPath = FunctionNodeGraph.childPath(path, field.name());
            FieldValue value = typed.values().get(field.name());
            switch (field.kind()) {
                case FUNCTION -> {
                    Integer child = graph.source(new FunctionNodeGraph.Pin(id, field.name()));
                    if (child == null) {
                        problems.add(new GraphProblem(fieldPath, "The input has no function node", id));
                    } else {
                        if (!field.acceptsReference() && graph.functionNode(child) instanceof FunctionNode.Reference) {
                            problems.add(new GraphProblem(fieldPath, "This input does not take a reference", child));
                        }
                        check(graph, child, fieldPath, noises, problems);
                    }
                }
                case NUMBER -> {
                    if (value instanceof FieldValue.Number number) {
                        range(typed.bare() ? path : fieldPath, id, number.value(), field.min(), field.max(), problems);
                    } else {
                        problems.add(new GraphProblem(fieldPath, "The field has no number", id));
                    }
                }
                case INTEGER -> {
                    if (value instanceof FieldValue.Int integer) {
                        range(fieldPath, id, integer.value(), field.min(), field.max(), problems);
                    } else {
                        problems.add(new GraphProblem(fieldPath, "The field has no int", id));
                    }
                }
                case ENUM -> {
                    if (!(value instanceof FieldValue.Choice choice) || !field.choices().contains(choice.value())) {
                        problems.add(new GraphProblem(fieldPath, "The field takes one of " + String.join(", ", field.choices()), id));
                    }
                }
                case NOISE -> {
                    if (!(value instanceof FieldValue.Noise noise)) {
                        problems.add(new GraphProblem(fieldPath, "The field has no noise", id));
                    } else if (noise.id() != null && noises != null && !noises.contains(noise.id())) {
                        problems.add(new GraphProblem(fieldPath, "The noise " + noise.id() + " is unknown", id));
                    }
                }
                case SPLINE -> {
                    if (value instanceof FieldValue.Spline spline) {
                        spline(graph, id, spline.value(), field.name(), fieldPath, noises, problems);
                    } else {
                        problems.add(new GraphProblem(fieldPath, "The field has no spline", id));
                    }
                }
                case VARIATION_LIST -> {
                    if (!(value instanceof FieldValue.VariationList)) {
                        problems.add(new GraphProblem(fieldPath, "The field has no island variation list", id));
                    }
                }
            }
        }
    }

    private static void spline(FunctionNodeGraph graph, int id, SplineValue spline, String pin, String path, @Nullable Set<ResourceLocation> noises,
                               List<GraphProblem> problems) {
        if (!(spline instanceof SplineValue.Multipoint multipoint)) {
            return;
        }
        String coordinatePath = FunctionNodeGraph.childPath(path, SplineValue.COORDINATE);
        Integer coordinate = graph.source(new FunctionNodeGraph.Pin(id, pin + "/" + SplineValue.COORDINATE));
        if (coordinate == null) {
            problems.add(new GraphProblem(coordinatePath, "The input has no function node", id));
        } else {
            check(graph, coordinate, coordinatePath, noises, problems);
        }
        if (multipoint.points().isEmpty()) {
            problems.add(new GraphProblem(path, "The spline has no points", id));
        }
        for (int index = 0; index < multipoint.points().size(); index++) {
            spline(graph, id, multipoint.points().get(index).value(), pin + "/points/" + index + "/value", path + "/points/" + index + "/value", noises, problems);
        }
    }

    private static void range(String path, @Nullable Integer functionNode, double value, double min, double max, List<GraphProblem> problems) {
        if (!(value >= min && value <= max)) {
            problems.add(new GraphProblem(path, "The value " + value + " is outside " + min + " to " + max, functionNode));
        }
    }

}
