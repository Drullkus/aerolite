package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.world.island.IslandSettings;

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

/**
 * The graphs of the island editor: one router graph per preset dimension with a noise generator, and the edited function graphs by entry id.
 * Each router graph keeps the router JSON that it loaded from. Dimension default saves the field of that JSON.
 * The positions of the function nodes of each graph are in memory by function node id. The output box has the key {@link GraphLayout#BOX}.
 * An entry that the editor opens without an edit stays in a cache. An edit moves it to the edited function graphs.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
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

    /** The first {@code aerolite:voronoi_islands} function node of a dimension, and the way to write it. */
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
    private final Map<GraphId, Map<Integer, Sidecar.Position>> positions = new HashMap<>();

    /** Adds the router graph of a dimension and the router JSON that dimension default saves. */
    public void putRouter(ResourceKey<Level> dimension, RouterGraph graph, JsonObject shippedRouter) {
        this.routers.put(dimension, graph);
        this.shipped.put(dimension, shippedRouter.deepCopy());
    }

    /** A copy whose graphs and positions are copies. The cache of opened entries stays behind. */
    public IslandGraphs copy() {
        IslandGraphs copy = new IslandGraphs();
        this.routers.forEach((dimension, graph) -> copy.putRouter(dimension, graph.copy(), this.shipped.get(dimension)));
        this.edited.forEach((entry, graph) -> copy.edited.put(entry, graph.copy()));
        this.positions.forEach((id, map) -> copy.positions.put(id, new HashMap<>(map)));
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
        load.ifSuccess(result -> this.loaded.put(entry, result));
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

    /** The positions of the function nodes of a graph, by id. The map is live. */
    public Map<Integer, Sidecar.Position> positions(GraphId graph) {
        return this.positions.computeIfAbsent(graph, ignored -> new HashMap<>());
    }

    /** The sidecar of a graph. Detached function nodes have no path, so it holds no position for them. */
    public Sidecar sidecar(GraphId id, FunctionNodeGraph graph) {
        return Sidecar.of(graph, this.positions(id));
    }

    /** The island settings of the first {@code aerolite:voronoi_islands} function node of the dimension. */
    public Optional<IslandSettings> islands(ResourceKey<Level> dimension, EntryLoader loader) {
        Found found = this.locate(dimension, loader);
        return found == null ? Optional.empty() : IslandSettingsFunctionNode.read(found.functionNode());
    }

    /** Writes the island settings into the first {@code aerolite:voronoi_islands} function node of the dimension. False when it has none. */
    public boolean setIslands(ResourceKey<Level> dimension, IslandSettings settings, EntryLoader loader) {
        Found found = this.locate(dimension, loader);
        if (found == null) {
            return false;
        }
        found.writer().accept(IslandSettingsFunctionNode.write(found.functionNode(), settings));
        return true;
    }

    /** The first dimension whose router graph reaches an {@code aerolite:voronoi_islands} function node. */
    public Optional<ResourceKey<Level>> firstIslandsDimension(EntryLoader loader) {
        for (ResourceKey<Level> dimension : this.routers.keySet()) {
            if (this.locate(dimension, loader) != null) {
                return Optional.of(dimension);
            }
        }
        return Optional.empty();
    }

    /**
     * The first {@code aerolite:voronoi_islands} function node under the router outputs of the dimension, in field sequence and pin sequence.
     * The search follows reference nodes into their function graphs. An output on dimension default searches its saved JSON.
     * A write to a function node of that JSON first loads the JSON into the router graph and links it to the output.
     */
    @Nullable
    private Found locate(ResourceKey<Level> dimension, EntryLoader loader) {
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
                Found found = this.search(router, linked.functionNode(), loader, visited, direct);
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
                    Found again = this.search(router, loadedRoot, loader, new HashSet<>(), direct);
                    if (again == null) {
                        throw new IllegalStateException("The loaded output " + field + " lost its island function node");
                    }
                    again.writer().accept(functionNode);
                };
                Found found = this.search(scratch, root, loader, visited, materialize);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private Found search(FunctionNodeGraph graph, int id, EntryLoader loader, Set<ResourceLocation> visited, Writer writer) {
        FunctionNode functionNode = graph.functionNode(id);
        if (IslandSettingsFunctionNode.isIslands(functionNode)) {
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
                return this.search(entryGraph, root, loader, visited, entryWriter);
            }
            return null;
        }
        for (String pin : functionNode.inputPins()) {
            Integer child = graph.source(new FunctionNodeGraph.Pin(id, pin));
            if (child != null) {
                Found found = this.search(graph, child, loader, visited, writer);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

}
