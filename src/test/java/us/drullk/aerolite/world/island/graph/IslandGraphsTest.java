package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.IslandSettings;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the island sliders over the graphs: the search through a reference node and through dimension default, the write, and the copy.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class IslandGraphsTest {

    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceLocation ISLANDS = ResourceLocation.fromNamespaceAndPath("aerolite", "islands/overworld");
    private static final IslandSettings OTHER = new IslandSettings(320, 96, -32, 0.25, 0.1, 0.7, 0.35, 6.0, 128, 2);
    private static final IslandGraphs.EntryLoader LOADER = entry -> entry.equals(ISLANDS)
            ? GraphJson.readFunction(GraphTestData.islandFunction("overworld"))
            : DataResult.error(() -> "No entry " + entry);

    private static IslandGraphs graphs(JsonObject router) {
        IslandGraphs graphs = new IslandGraphs();
        graphs.putRouter(OVERWORLD, GraphJson.readRouter(router, router).getOrThrow(), router);
        return graphs;
    }

    private static JsonObject shippedRouter() {
        return GraphTestData.islandNoiseSettings("overworld").getAsJsonObject("noise_router");
    }

    @Test
    void slidersFollowTheReferenceOfADimensionDefaultOutput() {
        IslandGraphs graphs = graphs(shippedRouter());
        assertEquals(IslandSettings.DEFAULT, graphs.islands(OVERWORLD, LOADER).orElseThrow());
        assertTrue(graphs.setIslands(OVERWORLD, OTHER, LOADER));
        assertTrue(graphs.edited().containsKey(ISLANDS), "The write did not edit the entry");
        assertEquals(OTHER, graphs.islands(OVERWORLD, LOADER).orElseThrow());
        Map<ResourceLocation, DataResult<JsonElement>> edited = new LinkedHashMap<>();
        graphs.edited().forEach((entry, graph) -> edited.put(entry, GraphJson.write(graph)));
        RouterGraph router = graphs.router(OVERWORLD);
        assertNotNull(router);
        JsonObject built = GraphInline.router(GraphJson.writeRouter(router, graphs.shipped(OVERWORLD)).getOrThrow(), edited).getOrThrow();
        assertEquals(OTHER.cellSize(), built.getAsJsonObject("final_density").getAsJsonObject("argument").get("cell_size").getAsInt());
    }

    @Test
    void writeToAnInlineDefaultOutputLoadsItIntoTheRouterGraph() {
        JsonObject router = shippedRouter();
        // The initial density comes first in the field sequence, so the search finds its island function node first.
        router.getAsJsonObject("initial_density_without_jaggedness").add("argument1", GraphTestData.islandFunction("overworld"));
        IslandGraphs graphs = graphs(router);
        RouterGraph graph = graphs.router(OVERWORLD);
        assertNotNull(graph);
        assertTrue(graph.linked("initial_density_without_jaggedness") == null, "The output did not start on dimension default");
        assertTrue(graphs.setIslands(OVERWORLD, OTHER, LOADER));
        assertNotNull(graph.linked("initial_density_without_jaggedness"), "The write did not load the output into the router graph");
        assertTrue(graphs.edited().isEmpty(), "The write edited the entry instead of the inline function node");
        assertEquals(OTHER, graphs.islands(OVERWORLD, LOADER).orElseThrow());
    }

    @Test
    void secondWriteReusesTheLoadedFunctionNodes() {
        JsonObject router = shippedRouter();
        router.getAsJsonObject("initial_density_without_jaggedness").add("argument1", GraphTestData.islandFunction("overworld"));
        IslandGraphs graphs = graphs(router);
        RouterGraph graph = graphs.router(OVERWORLD);
        assertNotNull(graph);
        assertTrue(graphs.setIslands(OVERWORLD, OTHER, LOADER));
        int count = graph.functionNodes().size();
        IslandSettings second = OTHER.withCellHeight(192);
        assertTrue(graphs.setIslands(OVERWORLD, second, LOADER));
        assertEquals(count, graph.functionNodes().size(), "The second write loaded the output again");
        assertEquals(second, graphs.islands(OVERWORLD, LOADER).orElseThrow());
    }

    @Test
    void secondWriteKeepsOneEditedEntry() {
        IslandGraphs graphs = graphs(shippedRouter());
        assertTrue(graphs.setIslands(OVERWORLD, OTHER, LOADER));
        FunctionGraph entry = graphs.edited().get(ISLANDS);
        assertTrue(graphs.setIslands(OVERWORLD, OTHER.withCellHeight(192), LOADER));
        assertEquals(1, graphs.edited().size());
        assertTrue(graphs.edited().get(ISLANDS) == entry, "The second write made another edited function graph");
        assertTrue(graphs.router(OVERWORLD).functionNodes().isEmpty(), "The write added function nodes to the router graph");
    }

    @Test
    void routerWithoutIslandFunctionNodeHasNoSliders() {
        JsonObject router = shippedRouter();
        IslandGraphs graphs = graphs(router);
        RouterGraph graph = graphs.router(OVERWORLD);
        assertNotNull(graph);
        graph.set("final_density", new RouterGraph.Constant(0));
        graph.set("initial_density_without_jaggedness", new RouterGraph.Constant(0));
        assertTrue(graphs.islands(OVERWORLD, LOADER).isEmpty());
        assertFalse(graphs.setIslands(OVERWORLD, OTHER, LOADER));
    }

    @Test
    void copyKeepsTheOriginal() {
        IslandGraphs graphs = graphs(shippedRouter());
        graphs.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).put(GraphLayout.BOX, new Sidecar.Position(1, 2));
        IslandGraphs copy = graphs.copy();
        assertTrue(copy.setIslands(OVERWORLD, OTHER, LOADER));
        copy.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).put(GraphLayout.BOX, new Sidecar.Position(3, 4));
        assertEquals(IslandSettings.DEFAULT, graphs.islands(OVERWORLD, LOADER).orElseThrow());
        assertTrue(graphs.edited().isEmpty(), "The copy edit reached the original");
        assertEquals(new Sidecar.Position(1, 2), graphs.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).get(GraphLayout.BOX));
    }

    @Test
    void newFunctionNodeTakesValuesInRange() {
        ResourceLocation noise = ResourceLocation.withDefaultNamespace("temperature");
        for (FunctionNodeType type : FunctionNodeTypes.all()) {
            FunctionNode.Typed functionNode = FunctionNodeDefaults.create(type, noise);
            for (FieldSpec field : type.fields()) {
                FieldValue value = functionNode.values().get(field.name());
                if (value instanceof FieldValue.Number number) {
                    assertTrue(field.inRange(number.value()), () -> type.id() + " " + field.name() + " is out of range");
                } else if (value instanceof FieldValue.Int integer) {
                    assertTrue(field.inRange(integer.value()), () -> type.id() + " " + field.name() + " is out of range");
                } else if (field.kind() != FieldKind.FUNCTION) {
                    assertNotNull(value, () -> type.id() + " " + field.name() + " has no value");
                }
            }
        }
    }

}
