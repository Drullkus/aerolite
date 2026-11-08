package us.drullk.aerolite.client.preview;

import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.world.island.graph.FieldValue;
import us.drullk.aerolite.world.island.graph.FunctionGraph;
import us.drullk.aerolite.world.island.graph.FunctionNode;
import us.drullk.aerolite.world.island.graph.FunctionNodeGraph;
import us.drullk.aerolite.world.island.graph.GraphProblem;
import us.drullk.aerolite.world.island.graph.GraphValidator;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;
import us.drullk.aerolite.world.island.graph.Sidecar;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The state of the node canvas of the islands preview, free of ImGui types. The canvas draws it, and the probe drives it.
 * The canvas shows the router graph of the previewed dimension, or the function graph of one opened entry.
 * Back returns to the router graph of the previewed dimension. An edit of an opened entry makes it an edited function graph.
 * Each graph edit starts a terrain build through the change action.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class IslandEditor {

    private final IslandGraphs graphs;
    private final IslandGraphs.EntryLoader loader;
    private final Set<ResourceLocation> noises;
    private final Supplier<ResourceKey<Level>> dimension;
    private final Runnable onChange;
    /** The entry of the open function graph, or null for the router graph. */
    @Nullable
    private ResourceLocation entry;
    @Nullable
    private Integer selected;
    @Nullable
    private Integer selectRequest;
    @Nullable
    private Sidecar.Position paletteRequest;
    private boolean closePaletteRequest;
    private boolean paletteShown;
    @Nullable
    private Sidecar.Position panRequest;
    @Nullable
    private String openError;
    @Nullable
    private String buildError;
    private int version;
    private int problemsVersion = -1;
    @Nullable
    private IslandGraphs.GraphId problemsGraph;
    private List<GraphProblem> problems = List.of();

    /**
     * @param dimension the previewed dimension, or null
     * @param onChange  runs after each graph edit
     */
    public IslandEditor(IslandGraphs graphs, IslandGraphs.EntryLoader loader, Set<ResourceLocation> noises, Supplier<ResourceKey<Level>> dimension,
                        Runnable onChange) {
        this.graphs = graphs;
        this.loader = loader;
        this.noises = Set.copyOf(noises);
        this.dimension = dimension;
        this.onChange = onChange;
    }

    public IslandGraphs graphs() {
        return this.graphs;
    }

    public IslandGraphs.EntryLoader loader() {
        return this.loader;
    }

    /** The ids of the noise registry of the creation context. */
    public Set<ResourceLocation> noises() {
        return this.noises;
    }

    /** The noise id of a new noise field: the first id of the noise registry in sort sequence. */
    public ResourceLocation defaultNoise() {
        return this.noises.stream().sorted().findFirst().orElse(ResourceLocation.withDefaultNamespace("zero"));
    }

    /** The count of graph edits. A cache of a value of the graphs keys on it. */
    public int version() {
        return this.version;
    }

    @Nullable
    public ResourceKey<Level> dimension() {
        return this.dimension.get();
    }

    /** The entry of the open function graph, or null while the router graph shows. */
    @Nullable
    public ResourceLocation openEntry() {
        return this.entry;
    }

    /** The id of the shown graph, or null when the previewed dimension has no router graph. */
    @Nullable
    public IslandGraphs.GraphId graphId() {
        if (this.entry != null) {
            return new IslandGraphs.GraphId.Entry(this.entry);
        }
        ResourceKey<Level> dimension = this.dimension();
        return dimension != null && this.graphs.router(dimension) != null ? new IslandGraphs.GraphId.Router(dimension) : null;
    }

    /** The shown graph, or null when the previewed dimension has no router graph. */
    @Nullable
    public FunctionNodeGraph graph() {
        if (this.entry != null) {
            return this.graphs.function(this.entry, this.loader).result().orElse(null);
        }
        ResourceKey<Level> dimension = this.dimension();
        return dimension == null ? null : this.graphs.router(dimension);
    }

    /** Opens the function graph of an entry. An entry that does not load keeps the view and shows the error. */
    public void openReference(ResourceLocation entry) {
        DataResult<FunctionGraph> graph = this.graphs.function(entry, this.loader);
        if (graph.error().isPresent()) {
            this.openError = graph.error().get().message();
            return;
        }
        this.entry = entry;
        this.openError = null;
        this.selected = null;
        this.selectRequest = null;
    }

    /** Shows the router graph of the previewed dimension. */
    public void back() {
        this.entry = null;
        this.openError = null;
        this.selected = null;
        this.selectRequest = null;
    }

    /** The error of the last open that failed, or null. */
    @Nullable
    public String openError() {
        return this.openError;
    }

    /** The error of the last terrain build, or null when it built. */
    @Nullable
    public String buildError() {
        return this.buildError;
    }

    public void setBuildError(@Nullable String buildError) {
        this.buildError = buildError;
    }

    /** The selected function node of the shown graph, or null. */
    @Nullable
    public Integer selected() {
        FunctionNodeGraph graph = this.graph();
        return this.selected != null && graph != null && graph.functionNodes().containsKey(this.selected) ? this.selected : null;
    }

    public void select(@Nullable Integer functionNode) {
        this.selected = functionNode;
    }

    /** Asks the canvas to select a function node of the shown graph. */
    public void requestSelect(int functionNode) {
        this.selectRequest = functionNode;
        this.selected = functionNode;
    }

    /** The function node that the canvas must select, once. */
    @Nullable
    public Integer takeSelectRequest() {
        Integer request = this.selectRequest;
        this.selectRequest = null;
        return request;
    }

    /** Asks the canvas to open the palette at a canvas point. */
    public void requestPalette(float x, float y) {
        this.paletteRequest = new Sidecar.Position(x, y);
    }

    /** The canvas point where the canvas must open the palette, once. */
    @Nullable
    public Sidecar.Position takePaletteRequest() {
        Sidecar.Position request = this.paletteRequest;
        this.paletteRequest = null;
        return request;
    }

    /** Asks the canvas to pan the shown graph, in canvas units. */
    public void requestPan(float x, float y) {
        this.panRequest = new Sidecar.Position(x, y);
    }

    /** The panning that the canvas must take, once. */
    @Nullable
    public Sidecar.Position takePanRequest() {
        Sidecar.Position request = this.panRequest;
        this.panRequest = null;
        return request;
    }

    /** True when the last drawn frame showed the palette. */
    public boolean paletteShown() {
        return this.paletteShown;
    }

    public void setPaletteShown(boolean paletteShown) {
        this.paletteShown = paletteShown;
    }

    /** Asks the canvas to close the palette. */
    public void requestClosePalette() {
        this.closePaletteRequest = true;
    }

    public boolean takeClosePaletteRequest() {
        boolean request = this.closePaletteRequest;
        this.closePaletteRequest = false;
        return request;
    }

    /** Marks an edit of the shown graph. An open entry becomes an edited function graph. */
    public void edited() {
        if (this.entry != null && this.graph() instanceof FunctionGraph function) {
            this.graphs.markEdited(this.entry, function);
        }
        this.changed();
    }

    /** Marks an edit of any graph and starts a terrain build. */
    public void changed() {
        this.version++;
        this.onChange.run();
    }

    /** Sets one field of a function node of the shown graph. Another kind of function node stays as it is. */
    public void setField(int functionNode, String field, FieldValue value) {
        FunctionNodeGraph graph = Objects.requireNonNull(this.graph(), "No graph shows");
        if (graph.functionNode(functionNode) instanceof FunctionNode.Typed typed && !value.equals(typed.values().get(field))) {
            graph.replace(functionNode, typed.with(field, value));
            this.edited();
        }
    }

    /** Gives a router output of the previewed dimension a value without a link. */
    public void setRouterOutput(String field, RouterGraph.Unlinked value) {
        ResourceKey<Level> dimension = this.dimension();
        RouterGraph router = dimension == null ? null : this.graphs.router(dimension);
        if (router != null) {
            router.set(field, value);
            this.changed();
        }
    }

    /** Adds a function node to the shown graph at a canvas point. Gives its id. */
    public int add(FunctionNode functionNode, Sidecar.Position position) {
        FunctionNodeGraph graph = Objects.requireNonNull(this.graph(), "No graph shows");
        int id = graph.add(functionNode);
        this.graphs.positions(Objects.requireNonNull(this.graphId())).put(id, position);
        this.edited();
        return id;
    }

    /** The validator problems of the shown graph. */
    public List<GraphProblem> problems() {
        IslandGraphs.GraphId id = this.graphId();
        FunctionNodeGraph graph = this.graph();
        if (id == null || graph == null) {
            return List.of();
        }
        if (this.problemsVersion != this.version || !id.equals(this.problemsGraph)) {
            this.problemsVersion = this.version;
            this.problemsGraph = id;
            this.problems = graph instanceof RouterGraph router && id instanceof IslandGraphs.GraphId.Router routerId
                    ? GraphValidator.validate(router, this.graphs.shipped(routerId.dimension()), this.noises)
                    : GraphValidator.validate((FunctionGraph) graph, this.noises);
        }
        return this.problems;
    }

}
