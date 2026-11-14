package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.variation.IslandVariationList;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports why a graph does not build: an output or an input pin without a function node, a value out of its vanilla range,
 * an unknown type id, an unknown noise id, a reference where vanilla reads no registry, or a spline without points.
 * A null noise id set skips the noise id check. Each function node outside the trees gives one problem, because the save drops it.
 * Each problem names the function node that holds it. A problem of a graph output without a function node names none.
 * The island checks report a router without a carrier and the problems of the island variations and lists.
 * They also report the problems of the function graph of a variation. They read the entries through {@link IslandEntries}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class GraphValidator {

    /** The markers that the island function drops inside the graph of a variation. */
    public static final Set<ResourceLocation> DROPPED_CACHES = Set.of(ResourceLocation.withDefaultNamespace("flat_cache"), ResourceLocation.withDefaultNamespace("cache_2d"));
    /** The router outputs that the climate sampler reads, in the sequence of the island biome source search. */
    public static final List<String> CLIMATE_OUTPUTS = List.of("temperature", "vegetation", "continents", "erosion", "depth", "ridges");

    private static final Set<ResourceLocation> MARKERS = Set.of(ResourceLocation.withDefaultNamespace("interpolated"), ResourceLocation.withDefaultNamespace("flat_cache"),
            ResourceLocation.withDefaultNamespace("cache_2d"), ResourceLocation.withDefaultNamespace("cache_once"), ResourceLocation.withDefaultNamespace("cache_all_in_cell"));
    private static final String TYPE = "type";
    private static final String ARGUMENT = "argument";
    private static final String ISLANDS = "islands";
    private static final String VARIATIONS = "variations";
    private static final String ENTRIES = "entries";
    private static final String EMPTY = "empty";
    private static final String DENSITY_FUNCTION = "density_function";

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

    /** The ids of the island variation lists that the island functions of a router name. References resolve through the entries. */
    public static Set<ResourceLocation> islandLists(JsonObject router, IslandEntries entries) {
        Set<ResourceLocation> lists = new LinkedHashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();
        for (String field : RouterGraph.FIELDS) {
            JsonElement output = router.get(field);
            if (output != null) {
                walk(output, field, entries, visited, (object, type, path) -> {
                    if (type.equals(AeroliteWorldgen.ISLAND_VARIATIONS_TYPE) && object.get(VARIATIONS) instanceof JsonPrimitive primitive && primitive.isString()) {
                        ResourceLocation list = ResourceLocation.tryParse(primitive.getAsString());
                        if (list != null) {
                            lists.add(list);
                        }
                    }
                });
            }
        }
        return lists;
    }

    /**
     * Reports a router that reaches an island function without a carrier on a climate output.
     * The carrier holds an island function.
     * The island biome source reads only such a carrier. The problem names the router box, {@link GraphLayout#BOX}.
     * A router that reaches no island function has no such problem.
     */
    public static List<GraphProblem> carrier(JsonObject router, IslandEntries entries) {
        if (!reachesIslandFunction(router, entries)) {
            return List.of();
        }
        for (String field : CLIMATE_OUTPUTS) {
            JsonElement output = router.get(field);
            JsonObject root = output == null ? null : unwrap(output, entries);
            if (root != null && AeroliteWorldgen.ISLAND_CARRIER_TYPE.equals(typeOf(root)) && root.has(ISLANDS)) {
                JsonObject islands = unwrap(root.get(ISLANDS), entries);
                if (islands != null && AeroliteWorldgen.ISLAND_VARIATIONS_TYPE.equals(typeOf(islands))) {
                    return List.of();
                }
            }
        }
        return List.of(new GraphProblem(FunctionGraph.OUTPUT,
                "No climate output holds a carrier over an island function. The island biome source then gives its fallback biome", GraphLayout.BOX));
    }

    /**
     * The island variation lists and island variations that the lists reach, and their problems.
     * A list problem is a missing entry, no positive weight, or a list that names itself.
     * A variation problem is a missing density function, a dropped cache, or an island function in its graph.
     * The path of each problem is the id of its list or variation.
     *
     * @param lists      the lists to start from
     * @param variations further variations to check, for example the edited ones
     */
    public static IslandReach islands(Collection<ResourceLocation> lists, Collection<ResourceLocation> variations, IslandEntries entries) {
        List<GraphProblem> problems = new ArrayList<>();
        Map<ResourceLocation, List<ResourceLocation>> children = new LinkedHashMap<>();
        Set<ResourceLocation> reachedVariations = new LinkedHashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>(lists);
        Set<ResourceLocation> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (!seen.add(id)) {
                continue;
            }
            JsonObject json = entries.list(id);
            if (json == null) {
                problems.add(new GraphProblem(id.toString(), "The island variation list " + id + " does not exist"));
                continue;
            }
            List<ResourceLocation> listChildren = new ArrayList<>();
            children.put(id, listChildren);
            JsonElement entriesJson = json.get(ENTRIES);
            DataResult<List<IslandVariationList.Entry>> parsed = entriesJson == null
                    ? DataResult.error(() -> "The key entries is missing")
                    : IslandVariationList.Entry.CODEC.listOf().parse(JsonOps.INSTANCE, entriesJson);
            if (parsed.isError()) {
                problems.add(new GraphProblem(id.toString(), "The list does not read: " + parsed.error().orElseThrow().message()));
                continue;
            }
            List<IslandVariationList.Entry> listEntries = parsed.getOrThrow();
            if (listEntries.stream().noneMatch(entry -> entry.weight() > 0)) {
                problems.add(new GraphProblem(id.toString(), "The list holds no entry with a positive weight"));
            }
            for (int index = 0; index < listEntries.size(); index++) {
                IslandVariationList.Entry entry = listEntries.get(index);
                int number = index;
                entry.target().ifLeft(variation -> {
                    if (entries.variation(variation.location()) == null) {
                        problems.add(new GraphProblem(id.toString(), "The entry " + number + " names the missing island variation " + variation.location()));
                    } else {
                        reachedVariations.add(variation.location());
                    }
                }).ifRight(list -> {
                    if (entries.list(list.location()) == null) {
                        problems.add(new GraphProblem(id.toString(), "The entry " + number + " names the missing island variation list " + list.location()));
                    } else {
                        listChildren.add(list.location());
                        queue.add(list.location());
                    }
                });
            }
        }
        for (ResourceLocation list : children.keySet()) {
            if (reaches(children, list)) {
                problems.add(new GraphProblem(list.toString(), "The list names itself through its entries"));
            }
        }
        for (ResourceLocation variation : variations) {
            if (entries.variation(variation) == null) {
                problems.add(new GraphProblem(variation.toString(), "The island variation " + variation + " does not exist"));
            } else {
                reachedVariations.add(variation);
            }
        }
        for (ResourceLocation variation : reachedVariations) {
            variation(variation, Objects.requireNonNull(entries.variation(variation)), entries, problems);
        }
        return new IslandReach(Set.copyOf(children.keySet()), Collections.unmodifiableSet(reachedVariations), List.copyOf(problems));
    }

    /**
     * The problems of the function graph of an island variation, on its function nodes.
     * The island function drops a {@code flat_cache} or {@code cache_2d} function node. The node gives the value of its input.
     * An island function in the graph has no end when the game maps it. A reference node shows the problems of its entry.
     */
    public static List<GraphProblem> variationGraph(FunctionGraph graph, IslandEntries entries) {
        List<GraphProblem> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> node : graph.paths().entrySet()) {
            FunctionNode functionNode = graph.functionNode(node.getValue());
            if (functionNode instanceof FunctionNode.Typed typed) {
                String message = variationMessage(typed.type().id());
                if (message != null) {
                    problems.add(new GraphProblem(node.getKey(), message, node.getValue()));
                }
            } else if (functionNode instanceof FunctionNode.Reference reference) {
                Set<ResourceLocation> visited = new HashSet<>();
                visited.add(reference.entry());
                JsonElement json = entries.densityFunction(reference.entry());
                if (json != null) {
                    walk(json, FunctionGraph.OUTPUT, entries, visited, (object, type, path) -> {
                        if (variationMessage(type) != null) {
                            problems.add(new GraphProblem(node.getKey(), "The entry " + reference.entry() + " holds " + type + " at " + shown(path) + ". "
                                    + variationMessage(type), node.getValue()));
                        }
                    });
                }
            }
        }
        return problems;
    }

    /** The lists and variations that a check reached, and the problems of the check. */
    public record IslandReach(Set<ResourceLocation> lists, Set<ResourceLocation> variations, List<GraphProblem> problems) {
    }

    /** The problem text of a type inside the graph of a variation. A type without a problem there gives null. */
    @Nullable
    private static String variationMessage(ResourceLocation type) {
        if (DROPPED_CACHES.contains(type)) {
            return "The island function drops this cache in a variation graph, so the cache gives the value of its input";
        }
        if (type.equals(AeroliteWorldgen.ISLAND_VARIATIONS_TYPE)) {
            return "A variation graph holds no island function. The game maps such a graph without end";
        }
        return null;
    }

    private static void variation(ResourceLocation id, JsonObject json, IslandEntries entries, List<GraphProblem> problems) {
        if (json.get(EMPTY) instanceof JsonPrimitive empty && empty.isBoolean() && empty.getAsBoolean()) {
            return;
        }
        JsonElement function = json.get(DENSITY_FUNCTION);
        if (function == null) {
            problems.add(new GraphProblem(id.toString(), "The island variation holds no density function"));
            return;
        }
        Set<ResourceLocation> visited = new HashSet<>();
        if (function.isJsonPrimitive() && function.getAsJsonPrimitive().isString()) {
            ResourceLocation entry = ResourceLocation.tryParse(function.getAsString());
            if (entry == null || entries.densityFunction(entry) == null) {
                problems.add(new GraphProblem(id.toString(), "The island variation names the missing density function entry " + function.getAsString()));
                return;
            }
        }
        walk(function, FunctionGraph.OUTPUT, entries, visited, (object, type, path) -> {
            String message = variationMessage(type);
            if (message != null) {
                problems.add(new GraphProblem(id.toString(), "The graph holds " + type + " at " + shown(path) + ". " + message));
            }
        });
    }

    private static boolean reaches(Map<ResourceLocation, List<ResourceLocation>> children, ResourceLocation start) {
        Deque<ResourceLocation> queue = new ArrayDeque<>(children.getOrDefault(start, List.of()));
        Set<ResourceLocation> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (id.equals(start)) {
                return true;
            }
            if (seen.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return false;
    }

    private static boolean reachesIslandFunction(JsonObject router, IslandEntries entries) {
        boolean[] found = new boolean[1];
        Set<ResourceLocation> visited = new HashSet<>();
        for (String field : RouterGraph.FIELDS) {
            JsonElement output = router.get(field);
            if (output != null) {
                walk(output, field, entries, visited, (object, type, path) -> found[0] |= type.equals(AeroliteWorldgen.ISLAND_VARIATIONS_TYPE));
            }
        }
        return found[0];
    }

    /** The function object under references and markers. A number, a missing entry and a reference loop give null. */
    @Nullable
    private static JsonObject unwrap(JsonElement json, IslandEntries entries) {
        Set<ResourceLocation> visited = new HashSet<>();
        JsonElement current = json;
        while (true) {
            if (current.isJsonPrimitive() && current.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(current.getAsString());
                JsonElement entry = id == null || !visited.add(id) ? null : entries.densityFunction(id);
                if (entry == null) {
                    return null;
                }
                current = entry;
            } else if (current.isJsonObject()) {
                JsonObject object = current.getAsJsonObject();
                ResourceLocation type = typeOf(object);
                if (type != null && MARKERS.contains(type) && object.has(ARGUMENT)) {
                    current = object.get(ARGUMENT);
                } else {
                    return object;
                }
            } else {
                return null;
            }
        }
    }

    @Nullable
    private static ResourceLocation typeOf(JsonObject object) {
        return object.get(TYPE) instanceof JsonPrimitive primitive && primitive.isString() ? ResourceLocation.tryParse(primitive.getAsString()) : null;
    }

    private static String shown(String path) {
        return path.isEmpty() ? "/" : path;
    }

    /** Takes each function object of a density function JSON with its type and path. */
    @FunctionalInterface
    private interface JsonVisitor {
        void visit(JsonObject object, ResourceLocation type, String path);
    }

    /**
     * Walks the function objects of a density function JSON through the function places of {@link FunctionNodeTypes}.
     * A reference string walks its entry once per walk, at the path of the reference.
     */
    private static void walk(JsonElement json, String path, IslandEntries entries, Set<ResourceLocation> visited, JsonVisitor visitor) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            ResourceLocation id = ResourceLocation.tryParse(json.getAsString());
            JsonElement entry = id == null || !visited.add(id) ? null : entries.densityFunction(id);
            if (entry != null) {
                walk(entry, path, entries, visited, visitor);
            }
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        ResourceLocation typeId = typeOf(object);
        FunctionNodeType type = typeId == null ? null : FunctionNodeTypes.get(typeId);
        if (type == null) {
            return;
        }
        visitor.visit(object, typeId, path);
        for (FieldSpec field : type.fields()) {
            JsonElement value = object.get(field.name());
            if (value == null) {
                continue;
            }
            String fieldPath = FunctionNodeGraph.childPath(path, field.name());
            if (field.kind() == FieldKind.FUNCTION) {
                walk(value, fieldPath, entries, visited, visitor);
            } else if (field.kind() == FieldKind.SPLINE) {
                walkSpline(value, fieldPath, entries, visited, visitor);
            }
        }
    }

    private static void walkSpline(JsonElement json, String path, IslandEntries entries, Set<ResourceLocation> visited, JsonVisitor visitor) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement coordinate = object.get(SplineValue.COORDINATE);
        if (coordinate != null) {
            walk(coordinate, FunctionNodeGraph.childPath(path, SplineValue.COORDINATE), entries, visited, visitor);
        }
        if (object.get("points") instanceof JsonArray points) {
            for (int index = 0; index < points.size(); index++) {
                if (points.get(index) instanceof JsonObject point && point.has("value")) {
                    walkSpline(point.get("value"), path + "/points/" + index + "/value", entries, visited, visitor);
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
