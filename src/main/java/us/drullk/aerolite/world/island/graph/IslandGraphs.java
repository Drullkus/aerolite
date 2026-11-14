package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.LatticeSettings;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The graphs of the island editor: one router graph per preset dimension with a noise generator, and the edited function graphs by entry id.
 * Each router graph keeps the router JSON that it loaded from. Dimension default saves the field of that JSON.
 * The positions of the function nodes of each graph are in memory by function node id. The output box has the key {@link GraphLayout#BOX}.
 * An entry that the editor opens without an edit stays in a cache. An edit moves it to the edited function graphs.
 * The first load of an entry keeps its saved JSON as the start JSON. A restored entry takes the start JSON of its pack, if the pack holds one.
 * The graphs also hold the island variations and lists of the flow as JSON. The edit and start JSON rules are the same.
 * A read of an id without an edit loads the registry JSON through a {@link VariationLoader}.
 * The density function of a variation opens as a function graph. An inline tree first moves to an entry under the id of the variation.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandGraphs {

    /** The id of one graph of the editor. */
    public sealed interface GraphId {

        /** The router graph of a dimension. */
        record Router(ResourceKey<Level> dimension) implements GraphId {
        }

        /** The function graph of a density function entry. */
        record Entry(ResourceLocation entry) implements GraphId {
        }
    }

    /** Loads the function graph of a density function entry that has no edit. */
    @FunctionalInterface
    public interface EntryLoader {
        DataResult<FunctionGraph> load(ResourceLocation entry);
    }

    /** The first function node of a type under the router outputs of a dimension, and the way to write it. */
    private record Found(FunctionNode.Typed functionNode, Consumer<FunctionNode.Typed> writer) {
    }

    /** The writer of the function node with an id in a graph. */
    @FunctionalInterface
    private interface Writer {
        Consumer<FunctionNode.Typed> at(FunctionNodeGraph graph, int id);
    }

    /**
     * Loads the JSON of the island variations and the island variation lists of the registries.
     * An id without an entry gives an error.
     */
    public interface VariationLoader {

        /** The loader without entries. */
        VariationLoader NONE = new VariationLoader() {

            @Override
            public DataResult<JsonObject> variation(ResourceLocation id) {
                return DataResult.error(() -> "The island variation " + id + " does not exist");
            }

            @Override
            public DataResult<JsonObject> list(ResourceLocation id) {
                return DataResult.error(() -> "The island variation list " + id + " does not exist");
            }

            @Override
            public Set<ResourceLocation> variationIds() {
                return Set.of();
            }

            @Override
            public Set<ResourceLocation> listIds() {
                return Set.of();
            }
        };

        DataResult<JsonObject> variation(ResourceLocation id);

        DataResult<JsonObject> list(ResourceLocation id);

        /** The ids of the island variations of the registries. */
        Set<ResourceLocation> variationIds();

        /** The ids of the island variation lists of the registries. */
        Set<ResourceLocation> listIds();
    }

    /**
     * The density function entry of an island variation.
     *
     * @param moved true when the inline tree of the variation moved to the entry in this call
     */
    public record VariationFunction(ResourceLocation entry, boolean moved) {
    }

    /** The namespace of the new entries of the flow. */
    public static final String NEW_NAMESPACE = "aerolite_world";
    /** The path start of a new island variation, for example {@code aerolite_world:island_1}. */
    public static final String NEW_VARIATION = "island_";
    /** The path start of a new island variation list, for example {@code aerolite_world:list_1}. */
    public static final String NEW_LIST = "list_";

    private static final String DENSITY_FUNCTION = "density_function";
    private static final String EMPTY = "empty";
    private static final String ENTRIES = "entries";
    private static final String VARIATION = "variation";
    private static final String LIST = "list";
    private static final String VARIATIONS_FIELD = "variations";

    private final Map<ResourceKey<Level>, RouterGraph> routers = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, JsonObject> shipped = new HashMap<>();
    private final Map<ResourceLocation, FunctionGraph> edited = new LinkedHashMap<>();
    private final Map<ResourceLocation, FunctionGraph> loaded = new HashMap<>();
    private final Map<ResourceLocation, JsonElement> start = new HashMap<>();
    private final Map<GraphId, Map<Integer, Sidecar.Position>> positions = new HashMap<>();
    private final JsonEntries variations = new JsonEntries();
    private final JsonEntries lists = new JsonEntries();

    /** Adds the router graph of a dimension and the router JSON that dimension default saves. */
    public void putRouter(ResourceKey<Level> dimension, RouterGraph graph, JsonObject shippedRouter) {
        this.routers.put(dimension, graph);
        this.shipped.put(dimension, shippedRouter.deepCopy());
    }

    /**
     * A copy whose graphs, positions, variations and lists are copies. The cache of opened entries stays behind.
     * The start JSON comes along.
     */
    public IslandGraphs copy() {
        IslandGraphs copy = new IslandGraphs();
        this.routers.forEach((dimension, graph) -> copy.putRouter(dimension, graph.copy(), this.shipped.get(dimension)));
        this.edited.forEach((entry, graph) -> copy.edited.put(entry, graph.copy()));
        this.positions.forEach((id, map) -> copy.positions.put(id, new HashMap<>(map)));
        copy.start.putAll(this.start);
        this.variations.copyInto(copy.variations);
        this.lists.copyInto(copy.lists);
        return copy;
    }

    /** The dimensions with a router graph, in the sequence of their addition. */
    public List<ResourceKey<Level>> dimensions() {
        return List.copyOf(this.routers.keySet());
    }

    @Nullable
    public RouterGraph router(ResourceKey<Level> dimension) {
        return this.routers.get(dimension);
    }

    /** The router JSON that the router graph of the dimension loaded from. */
    public JsonObject shipped(ResourceKey<Level> dimension) {
        JsonObject json = this.shipped.get(dimension);
        if (json == null) {
            throw new IllegalArgumentException("No router graph for " + dimension.location());
        }
        return json.deepCopy();
    }

    /** The edited function graphs by entry id. */
    public Map<ResourceLocation, FunctionGraph> edited() {
        return Collections.unmodifiableMap(this.edited);
    }

    /** The function graph of an entry: the edited one, else the cached one, else a new load. */
    public DataResult<FunctionGraph> function(ResourceLocation entry, EntryLoader loader) {
        FunctionGraph graph = this.edited.get(entry);
        if (graph == null) {
            graph = this.loaded.get(entry);
        }
        if (graph != null) {
            return DataResult.success(graph);
        }
        DataResult<FunctionGraph> load = loader.load(entry);
        load.ifSuccess(result -> {
            this.loaded.put(entry, result);
            GraphJson.write(result).ifSuccess(json -> this.start.putIfAbsent(entry, json));
        });
        return load;
    }

    /** Moves the function graph of an entry to the edited function graphs. A graph of another entry is an error. */
    public void markEdited(ResourceLocation entry, FunctionGraph graph) {
        FunctionGraph current = this.edited.get(entry);
        if (current == graph) {
            return;
        }
        if (current != null) {
            throw new IllegalStateException("The entry " + entry + " has another edited function graph");
        }
        this.loaded.remove(entry);
        this.edited.put(entry, graph);
    }

    /** Puts a function graph from an island pack as the edited function graph of an entry. The entry loses its start JSON. */
    public void restoreEdited(ResourceLocation entry, FunctionGraph graph) {
        this.loaded.remove(entry);
        this.start.remove(entry);
        this.edited.put(entry, graph);
    }

    /** Sets the start JSON of an entry, for example the start JSON that an island pack keeps for a restored entry. */
    public void setStartJson(ResourceLocation entry, JsonElement json) {
        this.start.put(entry, json.deepCopy());
    }

    /** The saved JSON of the first load of an entry, or null for an entry that the flow did not load. */
    @Nullable
    public JsonElement startJson(ResourceLocation entry) {
        JsonElement json = this.start.get(entry);
        return json == null ? null : json.deepCopy();
    }

    /** The positions of the function nodes of a graph, by id. The map is live. */
    public Map<Integer, Sidecar.Position> positions(GraphId graph) {
        return this.positions.computeIfAbsent(graph, ignored -> new HashMap<>());
    }

    /**
     * The sidecar of a graph. Detached function nodes have no path, so it holds no position for them.
     * The sidecar of a router graph also holds the position of the router box.
     */
    public Sidecar sidecar(GraphId id, FunctionNodeGraph graph) {
        Sidecar sidecar = Sidecar.of(graph, this.positions(id));
        if (id instanceof GraphId.Router) {
            return sidecar.withRouter(Optional.ofNullable(this.positions(id).get(GraphLayout.BOX)));
        }
        return sidecar;
    }

    /** The island settings of the first {@code aerolite:voronoi_islands} function node of the dimension. */
    public Optional<IslandSettings> islands(ResourceKey<Level> dimension, EntryLoader loader) {
        Found found = this.locate(dimension, loader, IslandSettingsFunctionNode::isIslands);
        return found == null ? Optional.empty() : IslandSettingsFunctionNode.read(found.functionNode());
    }

    /** Writes the island settings into the first {@code aerolite:voronoi_islands} function node of the dimension. False when it has none. */
    public boolean setIslands(ResourceKey<Level> dimension, IslandSettings settings, EntryLoader loader) {
        Found found = this.locate(dimension, loader, IslandSettingsFunctionNode::isIslands);
        if (found == null) {
            return false;
        }
        found.writer().accept(IslandSettingsFunctionNode.write(found.functionNode(), settings));
        return true;
    }

    /** The lattice settings of the first island function node of the dimension. */
    public Optional<LatticeSettings> lattice(ResourceKey<Level> dimension, EntryLoader loader) {
        Found found = this.locate(dimension, loader, IslandFunctionNode::isIslandFunction);
        return found == null ? Optional.empty() : IslandFunctionNode.read(found.functionNode());
    }

    /** Writes the lattice settings into the first island function node of the dimension. False when it has none. */
    public boolean setLattice(ResourceKey<Level> dimension, LatticeSettings settings, EntryLoader loader) {
        Found found = this.locate(dimension, loader, IslandFunctionNode::isIslandFunction);
        if (found == null) {
            return false;
        }
        found.writer().accept(IslandFunctionNode.write(found.functionNode(), settings));
        return true;
    }

    /** The id of the island variation list of the first island function node of the dimension. */
    public Optional<ResourceLocation> islandList(ResourceKey<Level> dimension, EntryLoader loader) {
        Found found = this.locate(dimension, loader, IslandFunctionNode::isIslandFunction);
        return found != null && found.functionNode().values().get(VARIATIONS_FIELD) instanceof FieldValue.VariationList list
                ? Optional.of(list.id())
                : Optional.empty();
    }

    /** The JSON of an island variation: the edited one, else the registry one. The result is a copy. */
    public DataResult<JsonObject> variation(ResourceLocation id, VariationLoader loader) {
        return this.variations.get(id, loader::variation);
    }

    /** The JSON of an island variation list: the edited one, else the registry one. The result is a copy. */
    public DataResult<JsonObject> list(ResourceLocation id, VariationLoader loader) {
        return this.lists.get(id, loader::list);
    }

    /** Puts an edited island variation. Read a registry variation first, so its start JSON stays for the island pack. */
    public void putVariation(ResourceLocation id, JsonObject json) {
        this.variations.put(id, json);
    }

    /** Puts an edited island variation list. Read a registry list first, so its start JSON stays for the island pack. */
    public void putList(ResourceLocation id, JsonObject json) {
        this.lists.put(id, json);
    }

    /** The edited island variations by id, as copies. */
    public Map<ResourceLocation, JsonObject> editedVariations() {
        return this.variations.edited();
    }

    /** The edited island variation lists by id, as copies. */
    public Map<ResourceLocation, JsonObject> editedLists() {
        return this.lists.edited();
    }

    /** The JSON of the first read of an island variation. A variation that the flow did not read from the registries gives null. */
    @Nullable
    public JsonObject variationStartJson(ResourceLocation id) {
        return this.variations.start(id);
    }

    /** The JSON of the first read of an island variation list. A list that the flow did not read from the registries gives null. */
    @Nullable
    public JsonObject listStartJson(ResourceLocation id) {
        return this.lists.start(id);
    }

    public void setVariationStartJson(ResourceLocation id, JsonObject json) {
        this.variations.setStart(id, json);
    }

    public void setListStartJson(ResourceLocation id, JsonObject json) {
        this.lists.setStart(id, json);
    }

    /** Puts an island variation from an island pack as an edited variation. The variation loses its start JSON. */
    public void restoreVariation(ResourceLocation id, JsonObject json) {
        this.variations.restore(id, json);
    }

    /** Puts an island variation list from an island pack as an edited list. The list loses its start JSON. */
    public void restoreList(ResourceLocation id, JsonObject json) {
        this.lists.restore(id, json);
    }

    /** Removes the edit of an island variation. A registry variation shows its registry JSON again. False without an edit. */
    public boolean removeVariationEdit(ResourceLocation id) {
        return this.variations.remove(id);
    }

    /** Removes the edit of an island variation list. A registry list shows its registry JSON again. False without an edit. */
    public boolean removeListEdit(ResourceLocation id) {
        return this.lists.remove(id);
    }

    /** Every known island variation id: the registry ids and the edited ids, in sort sequence. */
    public SortedSet<ResourceLocation> variationIds(VariationLoader loader) {
        return this.variations.ids(loader.variationIds());
    }

    /** Every known island variation list id: the registry ids and the edited ids, in sort sequence. */
    public SortedSet<ResourceLocation> listIds(VariationLoader loader) {
        return this.lists.ids(loader.listIds());
    }

    /** The first unknown id {@code aerolite_world:island_<n>} from n = 1. */
    public ResourceLocation newVariationId(VariationLoader loader) {
        return newId(NEW_VARIATION, this.variationIds(loader));
    }

    /** The first unknown id {@code aerolite_world:list_<n>} from n = 1. */
    public ResourceLocation newListId(VariationLoader loader) {
        return newId(NEW_LIST, this.listIds(loader));
    }

    /** Copies an island variation under a new id as an edit. A known new id is an error. */
    public DataResult<ResourceLocation> duplicateVariation(ResourceLocation from, ResourceLocation to, VariationLoader loader) {
        if (this.variationIds(loader).contains(to)) {
            return DataResult.error(() -> "The island variation " + to + " exists");
        }
        return this.variation(from, loader).map(json -> {
            this.putVariation(to, json);
            return to;
        });
    }

    /** Copies an island variation list under a new id as an edit. A known new id is an error. */
    public DataResult<ResourceLocation> duplicateList(ResourceLocation from, ResourceLocation to, VariationLoader loader) {
        if (this.listIds(loader).contains(to)) {
            return DataResult.error(() -> "The island variation list " + to + " exists");
        }
        return this.list(from, loader).map(json -> {
            this.putList(to, json);
            return to;
        });
    }

    /**
     * Moves an island variation to a new id as an edit. The old id loses its edit, so a registry variation shows its registry JSON again.
     * Each edited list that names the old id names the new id after the move. A known new id is an error.
     */
    public DataResult<ResourceLocation> renameVariation(ResourceLocation from, ResourceLocation to, VariationLoader loader) {
        if (from.equals(to)) {
            return DataResult.success(to);
        }
        DataResult<ResourceLocation> moved = this.duplicateVariation(from, to, loader);
        moved.ifSuccess(ignored -> {
            this.variations.remove(from);
            this.renameInLists(VARIATION, from, to);
        });
        return moved;
    }

    /**
     * Moves an island variation list to a new id as an edit. The old id loses its edit, so a registry list shows its registry JSON again.
     * Each edited list that names the old id names the new id after the move.
     * So does each island function node of a router graph or an edited function graph.
     * A known new id is an error.
     */
    public DataResult<ResourceLocation> renameList(ResourceLocation from, ResourceLocation to, VariationLoader loader) {
        if (from.equals(to)) {
            return DataResult.success(to);
        }
        DataResult<ResourceLocation> moved = this.duplicateList(from, to, loader);
        moved.ifSuccess(ignored -> {
            this.lists.remove(from);
            this.renameInLists(LIST, from, to);
            List<FunctionNodeGraph> graphs = new ArrayList<>(this.routers.values());
            graphs.addAll(this.edited.values());
            for (FunctionNodeGraph graph : graphs) {
                for (Map.Entry<Integer, FunctionNode> node : Map.copyOf(graph.functionNodes()).entrySet()) {
                    if (IslandFunctionNode.isIslandFunction(node.getValue()) && node.getValue() instanceof FunctionNode.Typed typed
                            && new FieldValue.VariationList(from).equals(typed.values().get(VARIATIONS_FIELD))) {
                        graph.replace(node.getKey(), typed.with(VARIATIONS_FIELD, new FieldValue.VariationList(to)));
                    }
                }
            }
        });
        return moved;
    }

    /**
     * The density function entry of an island variation. A variation that names an entry gives that entry.
     * An inline tree first moves to an edited function graph under the id of the variation. The variation then names that entry.
     * An Empty variation and a variation without a density function are errors. So is an inline tree whose entry id exists.
     */
    public DataResult<VariationFunction> variationFunction(ResourceLocation variation, VariationLoader variationLoader, EntryLoader loader) {
        DataResult<JsonObject> read = this.variation(variation, variationLoader);
        if (read.isError()) {
            return read.map(ignored -> new VariationFunction(variation, false));
        }
        JsonObject json = read.getOrThrow();
        if (json.get(EMPTY) instanceof JsonPrimitive empty && empty.isBoolean() && empty.getAsBoolean()) {
            return DataResult.error(() -> "The island variation " + variation + " is Empty and holds no density function");
        }
        JsonElement function = json.get(DENSITY_FUNCTION);
        if (function == null) {
            return DataResult.error(() -> "The island variation " + variation + " holds no density function");
        }
        if (function.isJsonPrimitive() && function.getAsJsonPrimitive().isString()) {
            ResourceLocation entry = ResourceLocation.tryParse(function.getAsString());
            if (entry == null) {
                return DataResult.error(() -> "The island variation " + variation + " names the bad entry id " + function.getAsString());
            }
            return this.function(entry, loader).map(ignored -> new VariationFunction(entry, false));
        }
        if (this.edited.containsKey(variation) || this.loaded.containsKey(variation) || loader.load(variation).isSuccess()) {
            return DataResult.error(() -> "The density function entry " + variation + " exists, so the inline tree of the island variation does not move there");
        }
        return GraphJson.readFunction(function).map(graph -> {
            this.loaded.remove(variation);
            this.start.remove(variation);
            this.edited.put(variation, graph);
            json.addProperty(DENSITY_FUNCTION, variation.toString());
            this.putVariation(variation, json);
            return new VariationFunction(variation, true);
        });
    }

    /** The density function entries that the known island variations name. An inline tree names none. */
    public Set<ResourceLocation> variationFunctions(VariationLoader loader) {
        Set<ResourceLocation> entries = new LinkedHashSet<>();
        for (ResourceLocation id : this.variationIds(loader)) {
            this.variation(id, loader).ifSuccess(json -> {
                if (json.get(DENSITY_FUNCTION) instanceof JsonPrimitive primitive && primitive.isString()) {
                    ResourceLocation entry = ResourceLocation.tryParse(primitive.getAsString());
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            });
        }
        return entries;
    }

    /** The saved JSON of the entries, the variations and the lists for the island checks. The edits of the flow win over the registries. */
    public IslandEntries entries(VariationLoader variationLoader, EntryLoader loader) {
        return new IslandEntries() {

            @Override
            @Nullable
            public JsonElement densityFunction(ResourceLocation id) {
                return IslandGraphs.this.function(id, loader).flatMap(GraphJson::write).result().orElse(null);
            }

            @Override
            @Nullable
            public JsonObject variation(ResourceLocation id) {
                return IslandGraphs.this.variation(id, variationLoader).result().orElse(null);
            }

            @Override
            @Nullable
            public JsonObject list(ResourceLocation id) {
                return IslandGraphs.this.list(id, variationLoader).result().orElse(null);
            }
        };
    }

    /** Points each entry of each edited list with the key and the old id to the new id. */
    private void renameInLists(String key, ResourceLocation from, ResourceLocation to) {
        for (Map.Entry<ResourceLocation, JsonObject> list : this.lists.edited().entrySet()) {
            JsonObject json = list.getValue();
            boolean changed = false;
            if (json.get(ENTRIES) instanceof JsonArray entries) {
                for (JsonElement entry : entries) {
                    if (entry instanceof JsonObject object && object.get(key) instanceof JsonPrimitive primitive && primitive.isString()
                            && from.equals(ResourceLocation.tryParse(primitive.getAsString()))) {
                        object.addProperty(key, to.toString());
                        changed = true;
                    }
                }
            }
            if (changed) {
                this.lists.put(list.getKey(), json);
            }
        }
    }

    private static ResourceLocation newId(String prefix, Set<ResourceLocation> known) {
        for (int n = 1; ; n++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NEW_NAMESPACE, prefix + n);
            if (!known.contains(id)) {
                return id;
            }
        }
    }

    /** The edited, loaded and start JSON of the entries of one registry. */
    private static final class JsonEntries {

        private final Map<ResourceLocation, JsonObject> edited = new LinkedHashMap<>();
        private final Map<ResourceLocation, JsonObject> loaded = new HashMap<>();
        private final Map<ResourceLocation, JsonObject> start = new HashMap<>();

        private DataResult<JsonObject> get(ResourceLocation id, Function<ResourceLocation, DataResult<JsonObject>> load) {
            JsonObject json = this.edited.get(id);
            if (json == null) {
                json = this.loaded.get(id);
            }
            if (json != null) {
                return DataResult.success(json.deepCopy());
            }
            DataResult<JsonObject> result = load.apply(id);
            result.ifSuccess(loadedJson -> {
                this.loaded.put(id, loadedJson.deepCopy());
                this.start.putIfAbsent(id, loadedJson.deepCopy());
            });
            return result.map(JsonObject::deepCopy);
        }

        private void put(ResourceLocation id, JsonObject json) {
            this.loaded.remove(id);
            this.edited.put(id, json.deepCopy());
        }

        private void restore(ResourceLocation id, JsonObject json) {
            this.start.remove(id);
            this.put(id, json);
        }

        private boolean remove(ResourceLocation id) {
            return this.edited.remove(id) != null;
        }

        private Map<ResourceLocation, JsonObject> edited() {
            Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
            this.edited.forEach((id, json) -> copy.put(id, json.deepCopy()));
            return Collections.unmodifiableMap(copy);
        }

        @Nullable
        private JsonObject start(ResourceLocation id) {
            JsonObject json = this.start.get(id);
            return json == null ? null : json.deepCopy();
        }

        private void setStart(ResourceLocation id, JsonObject json) {
            this.start.put(id, json.deepCopy());
        }

        private SortedSet<ResourceLocation> ids(Set<ResourceLocation> registry) {
            SortedSet<ResourceLocation> ids = new TreeSet<>(registry);
            ids.addAll(this.edited.keySet());
            return Collections.unmodifiableSortedSet(ids);
        }

        private void copyInto(JsonEntries copy) {
            this.edited.forEach((id, json) -> copy.edited.put(id, json.deepCopy()));
            this.start.forEach((id, json) -> copy.start.put(id, json.deepCopy()));
        }
    }

    /**
     * The first function node of the type under the router outputs of the dimension, in field sequence and pin sequence.
     * The search follows reference nodes into their function graphs. An output on dimension default searches its saved JSON.
     * A write to a function node of that JSON first loads the JSON into the router graph and links it to the output.
     */
    @Nullable
    private Found locate(ResourceKey<Level> dimension, EntryLoader loader, Predicate<FunctionNode> type) {
        RouterGraph router = this.routers.get(dimension);
        if (router == null) {
            return null;
        }
        JsonObject shippedRouter = this.shipped.get(dimension);
        Set<ResourceLocation> visited = new HashSet<>();
        Writer direct = (graph, id) -> functionNode -> graph.replace(id, functionNode);
        for (String field : RouterGraph.FIELDS) {
            RouterGraph.Output output = router.output(field);
            if (output instanceof RouterGraph.Output.Linked linked) {
                Found found = this.search(router, linked.functionNode(), loader, visited, direct, type);
                if (found != null) {
                    return found;
                }
            } else if (output instanceof RouterGraph.DimensionDefault && shippedRouter.has(field)) {
                FunctionGraph scratch = GraphJson.readFunction(shippedRouter.get(field)).result().orElse(null);
                Integer root = scratch == null ? null : scratch.output();
                if (root == null) {
                    continue;
                }
                Writer materialize = (graph, id) -> functionNode -> {
                    int loadedRoot = GraphJson.readInto(router, shippedRouter.get(field), field).getOrThrow(IllegalStateException::new);
                    router.link(loadedRoot, field);
                    Found again = this.search(router, loadedRoot, loader, new HashSet<>(), direct, type);
                    if (again == null) {
                        throw new IllegalStateException("The loaded output " + field + " lost its function node");
                    }
                    again.writer().accept(functionNode);
                };
                Found found = this.search(scratch, root, loader, visited, materialize, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private Found search(FunctionNodeGraph graph, int id, EntryLoader loader, Set<ResourceLocation> visited, Writer writer, Predicate<FunctionNode> type) {
        FunctionNode functionNode = graph.functionNode(id);
        if (type.test(functionNode)) {
            return new Found((FunctionNode.Typed) functionNode, writer.at(graph, id));
        }
        if (functionNode instanceof FunctionNode.Reference reference && visited.add(reference.entry())) {
            FunctionGraph entryGraph = this.function(reference.entry(), loader).result().orElse(null);
            Integer root = entryGraph == null ? null : entryGraph.output();
            if (root != null) {
                ResourceLocation entry = reference.entry();
                Writer entryWriter = (target, targetId) -> updated -> {
                    this.markEdited(entry, entryGraph);
                    target.replace(targetId, updated);
                };
                return this.search(entryGraph, root, loader, visited, entryWriter, type);
            }
            return null;
        }
        for (String pin : functionNode.inputPins()) {
            Integer child = graph.source(new FunctionNodeGraph.Pin(id, pin));
            if (child != null) {
                Found found = this.search(graph, child, loader, visited, writer, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

}
