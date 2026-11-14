package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.LatticeSettings;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The graphs of the island editor: one router graph per preset dimension with a noise generator, and the edited function graphs by entry id.
 * Each router graph keeps the router JSON that it loaded from. Dimension default saves the field of that JSON.
 * The positions of the function nodes of each graph are in memory by function node id. The output box has the key {@link GraphLayout#BOX}.
 * An entry that the editor opens without an edit stays in a cache. An edit moves it to the edited function graphs.
 * The first load of an entry keeps its saved JSON as the start JSON. A restored entry takes the start JSON of its pack, if the pack holds one.
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

    private final Map<ResourceKey<Level>, RouterGraph> routers = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, JsonObject> shipped = new HashMap<>();
    private final Map<ResourceLocation, FunctionGraph> edited = new LinkedHashMap<>();
    private final Map<ResourceLocation, FunctionGraph> loaded = new HashMap<>();
    private final Map<ResourceLocation, JsonElement> start = new HashMap<>();
    private final Map<GraphId, Map<Integer, Sidecar.Position>> positions = new HashMap<>();

    /** Adds the router graph of a dimension and the router JSON that dimension default saves. */
    public void putRouter(ResourceKey<Level> dimension, RouterGraph graph, JsonObject shippedRouter) {
        this.routers.put(dimension, graph);
        this.shipped.put(dimension, shippedRouter.deepCopy());
    }

    /** A copy whose graphs and positions are copies. The cache of opened entries stays behind. The start JSON comes along. */
    public IslandGraphs copy() {
        IslandGraphs copy = new IslandGraphs();
        this.routers.forEach((dimension, graph) -> copy.putRouter(dimension, graph.copy(), this.shipped.get(dimension)));
        this.edited.forEach((entry, graph) -> copy.edited.put(entry, graph.copy()));
        this.positions.forEach((id, map) -> copy.positions.put(id, new HashMap<>(map)));
        copy.start.putAll(this.start);
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
