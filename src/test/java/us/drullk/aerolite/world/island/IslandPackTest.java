package us.drullk.aerolite.world.island;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.graph.FunctionGraph;
import us.drullk.aerolite.world.island.graph.GraphJson;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;
import us.drullk.aerolite.world.island.graph.Sidecar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the content of the island pack as a pure function of the graphs, and the restore of graphs from that content.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class IslandPackTest {

    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceLocation SETTINGS_ID = ResourceLocation.fromNamespaceAndPath("aerolite", "islands/overworld");
    private static final ResourceLocation ISLANDS = ResourceLocation.fromNamespaceAndPath("aerolite", "islands/overworld");
    private static final String SETTINGS_PATH = "data/aerolite/worldgen/noise_settings/islands/overworld.json";
    private static final String ENTRY_PATH = "data/aerolite/worldgen/density_function/islands/overworld.json";
    private static final String ENTRY_SIDECAR_PATH = "data/aerolite/aerolite_sidecar/density_function/islands/overworld.json";
    private static final String ROUTER_SIDECAR_PATH = "data/aerolite/aerolite_sidecar/noise_router/overworld.json";
    private static final String DIMENSION_DEFAULT_PATH = "data/aerolite/aerolite_sidecar/dimension_default/overworld.json";
    private static final IslandSettings OTHER = new IslandSettings(384, 96, -32, 0.25, 0.1, 0.7, 0.35, 6.0, 128, 2);
    private static final double BARRIER = 0.5;
    private static final Sidecar.Position ROOT_POSITION = new Sidecar.Position(30, 40);

    private final IslandPack pack = new IslandPack();

    private static JsonObject generated(String path) {
        try (InputStream stream = IslandPackTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("The generated resource " + path + " does not exist");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("The read of " + path + " failed", exception);
        }
    }

    private static JsonObject settings() {
        return generated(SETTINGS_PATH);
    }

    private static IslandGraphs.EntryLoader loader() {
        return entry -> entry.equals(ISLANDS)
                ? GraphJson.readFunction(generated(ENTRY_PATH))
                : DataResult.error(() -> "No entry " + entry);
    }

    /** Graphs that start from a router, like the graph holder at the start of a flow. */
    private static IslandGraphs graphs(JsonObject router) {
        IslandGraphs graphs = new IslandGraphs();
        graphs.putRouter(OVERWORLD, GraphJson.readRouter(router, router).getOrThrow(), router);
        return graphs;
    }

    private Map<String, JsonElement> files(IslandGraphs graphs) {
        return this.pack.files(graphs, Map.of(OVERWORLD, new IslandPack.NoiseSettingsEntry(SETTINGS_ID, settings())), null).getOrThrow();
    }

    @Test
    void defaultOutputsAndNoEditWriteNothing() {
        assertTrue(this.files(graphs(settings().getAsJsonObject("noise_router"))).isEmpty(), "A pack without a change holds files");
    }

    @Test
    void changedRouterOutputWritesTheNoiseSettingsWithTheShippedFields() {
        IslandGraphs graphs = graphs(settings().getAsJsonObject("noise_router"));
        RouterGraph router = graphs.router(OVERWORLD);
        assertNotNull(router);
        router.set("barrier", new RouterGraph.Constant(BARRIER));
        Map<String, JsonElement> files = this.files(graphs);
        assertTrue(files.containsKey(IslandPack.PACK_META), "The pack has no pack.mcmeta");
        assertTrue(files.containsKey(ROUTER_SIDECAR_PATH), "The pack has no router sidecar");
        assertEquals(settings().getAsJsonObject("noise_router"), files.get(DIMENSION_DEFAULT_PATH), "The dimension default router is not the shipped router");
        assertFalse(files.containsKey(ENTRY_PATH), "The pack holds an entry without an edit");
        JsonObject written = files.get(SETTINGS_PATH).getAsJsonObject();
        JsonObject shipped = settings();
        for (String key : shipped.keySet()) {
            if (!key.equals("noise_router")) {
                assertEquals(shipped.get(key), written.get(key), "The field " + key + " changed");
            }
        }
        JsonObject writtenRouter = written.getAsJsonObject("noise_router");
        assertEquals(BARRIER, writtenRouter.get("barrier").getAsDouble());
        assertEquals(shipped.getAsJsonObject("noise_router").get("final_density"), writtenRouter.get("final_density"));
    }

    @Test
    void editedEntryWritesItsOwnIdAndItsSidecar() {
        IslandGraphs graphs = graphs(settings().getAsJsonObject("noise_router"));
        assertTrue(graphs.setIslands(OVERWORLD, OTHER, loader()));
        FunctionGraph entry = graphs.edited().get(ISLANDS);
        assertNotNull(entry);
        Integer root = entry.output();
        assertNotNull(root);
        graphs.positions(new IslandGraphs.GraphId.Entry(ISLANDS)).put(root, ROOT_POSITION);
        Map<String, JsonElement> files = this.files(graphs);
        assertFalse(files.containsKey(SETTINGS_PATH), "An edited entry alone wrote the noise settings");
        assertEquals(OTHER.cellSize(), files.get(ENTRY_PATH).getAsJsonObject().get("cell_size").getAsInt());
        assertEquals("aerolite:voronoi_islands", files.get(ENTRY_PATH).getAsJsonObject().get("type").getAsString());
        assertEquals(ROOT_POSITION.x(), files.get(ENTRY_SIDECAR_PATH).getAsJsonObject().getAsJsonObject("positions")
                .getAsJsonObject(FunctionGraph.OUTPUT).get("x").getAsFloat());
    }

    @Test
    void entrySetBackToItsStartWritesNothing() {
        IslandGraphs graphs = graphs(settings().getAsJsonObject("noise_router"));
        IslandSettings start = graphs.islands(OVERWORLD, loader()).orElseThrow();
        assertTrue(graphs.setIslands(OVERWORLD, OTHER, loader()));
        assertFalse(this.files(graphs).isEmpty(), "An edited entry wrote no pack");
        IslandGraphs copy = graphs.copy();
        assertTrue(copy.setIslands(OVERWORLD, start, loader()));
        assertTrue(copy.edited().containsKey(ISLANDS), "The entry is no edited function graph");
        assertTrue(this.files(copy).isEmpty(), "An entry equal to its start JSON wrote a pack");
    }

    @Test
    void routerOutputSetBackToDimensionDefaultWritesNothing() {
        IslandGraphs graphs = graphs(settings().getAsJsonObject("noise_router"));
        RouterGraph router = graphs.router(OVERWORLD);
        assertNotNull(router);
        router.set("barrier", new RouterGraph.Constant(BARRIER));
        assertFalse(this.files(graphs).isEmpty(), "A changed router output wrote no pack");
        router.set("barrier", new RouterGraph.DimensionDefault());
        assertTrue(this.files(graphs).isEmpty(), "A router on dimension default wrote a pack");
    }

    @Test
    void restoreAppliesTheRouterTheEntriesAndThePositions() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        RouterGraph editedRouter = edited.router(OVERWORLD);
        assertNotNull(editedRouter);
        editedRouter.set("barrier", new RouterGraph.Constant(BARRIER));
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        FunctionGraph editedEntry = edited.edited().get(ISLANDS);
        assertNotNull(editedEntry);
        Integer editedRoot = editedEntry.output();
        assertNotNull(editedRoot);
        edited.positions(new IslandGraphs.GraphId.Entry(ISLANDS)).put(editedRoot, ROOT_POSITION);
        Map<String, JsonElement> files = this.files(edited);

        // Registries with the pack give the router of the pack, so the start sees it as dimension default.
        IslandGraphs restored = graphs(files.get(SETTINGS_PATH).getAsJsonObject().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        RouterGraph router = restored.router(OVERWORLD);
        assertNotNull(router);
        assertInstanceOf(RouterGraph.Constant.class, router.output("barrier"));
        assertInstanceOf(RouterGraph.DimensionDefault.class, router.output("final_density"));
        assertEquals(settings().getAsJsonObject("noise_router"), restored.shipped(OVERWORLD));
        FunctionGraph entry = restored.edited().get(ISLANDS);
        assertNotNull(entry, "The restore did not make the entry an edited function graph");
        Integer root = entry.output();
        assertNotNull(root);
        assertEquals(ROOT_POSITION, restored.positions(new IslandGraphs.GraphId.Entry(ISLANDS)).get(root));
        assertEquals(OTHER, restored.islands(OVERWORLD, loader()).orElseThrow());
        assertEquals(files, this.files(restored), "The restored graphs write another pack");
    }

}
