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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The state of the node canvas of the islands preview, free of ImGui types. The canvas draws it, and the probe drives it.
 * The canvas shows the router graph of the previewed dimension, or the function graph of one opened entry.
 * Back returns to the router graph of the previewed dimension. An edit of an opened entry makes it an edited function graph.
 * Each graph edit starts a terrain build through the change action.
 * The editor window is closed when the preview opens. The open state is not saved.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
public final class IslandEditor {

    /** One area of the canvas window in the last drawn frame, in window coordinates. */
    public record Area(float x, float y, float width, float height) {

        public static final Area NONE = new Area(0, 0, 0, 0);

        public float centerX() {
            return this.x + this.width / 2;
        }

        public float centerY() {
            return this.y + this.height / 2;
        }

    }

    /**
     * The problems of the shown graph and the build error, as the field editor shows them.
     *
     * @param byFunctionNode the texts of each function node with a problem, in problem sequence
     * @param general        the texts that name no function node of the shown graph
     * @param notice         true while any problem or build error exists
     */
    public record ShownProblems(Map<Integer, List<String>> byFunctionNode, List<String> general, boolean notice) {

        public static final ShownProblems NONE = new ShownProblems(Map.of(), List.of(), false);

        /** The texts of one function node. A null function node has none. */
        public List<String> of(@Nullable Integer functionNode) {
            return functionNode == null ? List.of() : this.byFunctionNode.getOrDefault(functionNode, List.of());
        }

        public boolean has(int functionNode) {
            return this.byFunctionNode.containsKey(functionNode);
        }

    }

    private final IslandGraphs graphs;
    private final IslandGraphs.EntryLoader loader;
    private final Set<ResourceLocation> noises;
    private final Supplier<ResourceKey<Level>> dimension;
    private final Runnable onChange;
    /** The entry of the open function graph, or null for the router graph. */
    @Nullable
    private ResourceLocation entry;
    private boolean open;
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
    private Float zoomRequest;
    private Area canvasArea = Area.NONE;
    private Area fieldsArea = Area.NONE;
    /** The scroll of the field editor in the last drawn frame, and its largest value. */
    private float fieldsScroll;
    private float fieldsScrollMax;
    /** The zoom of the shown graph in the last drawn frame. */
    private float zoom = 1;
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

    /** Opens the editor window. The next drawn frame puts it back at its margin. */
    public void open() {
        this.open = true;
    }

    /** Closes the editor window. The graphs stay as they are. */
    public void close() {
        this.open = false;
    }

    public boolean isOpen() {
        return this.open;
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

    /** Asks the canvas to pan the shown graph, in screen units. At zoom 1 a screen unit is a canvas unit. */
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

    /** Asks the canvas to zoom the shown graph. The canvas clamps the zoom to the bounds of {@link CanvasZoom}. */
    public void requestZoom(float zoom) {
        this.zoomRequest = zoom;
    }

    /** The zoom that the canvas must take, once. The canvas takes it before a pan request. */
    @Nullable
    public Float takeZoomRequest() {
        Float request = this.zoomRequest;
        this.zoomRequest = null;
        return request;
    }

    /** The size of the canvas in the last drawn frame, in screen units. */
    public Sidecar.Position canvasSize() {
        return new Sidecar.Position(this.canvasArea.width(), this.canvasArea.height());
    }

    /** The canvas in the last drawn frame. */
    public Area canvasArea() {
        return this.canvasArea;
    }

    public void setCanvasArea(Area canvasArea) {
        this.canvasArea = canvasArea;
    }

    /** The field editor in the last drawn frame. */
    public Area fieldsArea() {
        return this.fieldsArea;
    }

    public float fieldsScroll() {
        return this.fieldsScroll;
    }

    public float fieldsScrollMax() {
        return this.fieldsScrollMax;
    }

    public void setFieldsView(Area fieldsArea, float scroll, float scrollMax) {
        this.fieldsArea = fieldsArea;
        this.fieldsScroll = scroll;
        this.fieldsScrollMax = scrollMax;
    }

    /** The zoom of the shown graph in the last drawn frame. */
    public float zoom() {
        return this.zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
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

    /** The problems of the shown graph and the build error, split by function node. */
    public ShownProblems shownProblems() {
        // The build error comes from the router graph, so its paths name function nodes only while that graph shows.
        return split(this.problems(), this.buildError, this.entry == null ? this.graph() : null);
    }

    /**
     * Splits the problems and the build error by function node. A problem names its own function node.
     * A build error line that repeats a problem shows once. Another build error line names the function node at its path in {@code buildGraph}.
     *
     * @param buildGraph the graph that the paths of the build error name, or null when they name no shown function node
     */
    public static ShownProblems split(List<GraphProblem> problems, @Nullable String buildError, @Nullable FunctionNodeGraph buildGraph) {
        Map<Integer, List<String>> byFunctionNode = new LinkedHashMap<>();
        List<String> general = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (GraphProblem problem : problems) {
            String text = problem.toString();
            texts.add(text);
            if (problem.functionNode() != null) {
                byFunctionNode.computeIfAbsent(problem.functionNode(), ignored -> new ArrayList<>()).add(text);
            } else {
                general.add(text);
            }
        }
        if (buildError != null) {
            for (GraphProblem.Parsed line : GraphProblem.parse(buildError)) {
                if (texts.contains(line.text())) {
                    continue;
                }
                Integer functionNode = line.path() != null && buildGraph != null ? buildGraph.functionNodeAt(line.path()) : null;
                if (functionNode != null) {
                    byFunctionNode.computeIfAbsent(functionNode, ignored -> new ArrayList<>()).add(line.text());
                } else {
                    general.add(line.text());
                }
            }
        }
        if (problems.isEmpty() && buildError == null) {
            return ShownProblems.NONE;
        }
        Map<Integer, List<String>> frozen = new LinkedHashMap<>();
        byFunctionNode.forEach((id, list) -> frozen.put(id, List.copyOf(list)));
        return new ShownProblems(Collections.unmodifiableMap(frozen), List.copyOf(general), true);
    }

}
