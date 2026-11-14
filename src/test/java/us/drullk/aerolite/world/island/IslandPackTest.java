package us.drullk.aerolite.world.island;

import com.google.gson.JsonArray;
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
import us.drullk.aerolite.world.island.graph.FunctionNode;
import us.drullk.aerolite.world.island.graph.GraphJson;
import us.drullk.aerolite.world.island.graph.GraphLayout;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;
import us.drullk.aerolite.world.island.graph.Sidecar;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the content of the island pack as a pure function of the graphs, and the restore of graphs from that content.
 * It also checks the registry folders of the island variations and lists, their start JSON and their restore.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
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
    private static final String START_JSON_PATH = "data/aerolite/aerolite_sidecar/start_json/aerolite/islands/overworld.json";
    private static final Sidecar.Position BOX_POSITION = new Sidecar.Position(700, -20);
    private static final IslandSettings OTHER = new IslandSettings(384, 96, -32, 0.25, 0.1, 0.7, 0.35, 6.0, 128, 2);
    private static final double BARRIER = 0.5;
    private static final Sidecar.Position ROOT_POSITION = new Sidecar.Position(30, 40);
    private static final Sidecar.Position ROUTER_POSITION = new Sidecar.Position(50, 60);

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
        graphs.putRouter(OVERWORLD, GraphJson.readRouter(router).getOrThrow(), router);
        return graphs;
    }

    /** Removes the function nodes of a router output and gives it a value without a link. */
    private static void unlink(RouterGraph router, String field, RouterGraph.Unlinked value) {
        Integer root = router.linked(field);
        assertNotNull(root, field + " has no link");
        router.removeTree(root);
        router.set(field, value);
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
        unlink(router, "barrier", new RouterGraph.Constant(BARRIER));
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
    void expandedRouterWritesOnlyWhileAnOutputDiffers() {
        JsonObject shipped = settings().getAsJsonObject("noise_router");
        IslandGraphs graphs = graphs(shipped);
        RouterGraph router = graphs.router(OVERWORLD);
        assertNotNull(router);
        assertNotNull(router.linked("final_density"), "The final density output did not load expanded");
        assertTrue(this.files(graphs).isEmpty(), "An untouched expanded router wrote a pack");
        unlink(router, "final_density", new RouterGraph.Constant(-1.0));
        assertTrue(this.files(graphs).containsKey(SETTINGS_PATH), "A constant output wrote no noise settings");
        int root = GraphJson.readInto(router, shipped.get("final_density"), "final_density").getOrThrow();
        assertTrue(router.link(root, "final_density"));
        assertTrue(this.files(graphs).isEmpty(), "A router linked to its shipped function nodes again wrote a pack");
    }

    @Test
    void routerOutputSetBackToDimensionDefaultWritesNothing() {
        IslandGraphs graphs = graphs(settings().getAsJsonObject("noise_router"));
        RouterGraph router = graphs.router(OVERWORLD);
        assertNotNull(router);
        unlink(router, "barrier", new RouterGraph.Constant(BARRIER));
        assertFalse(this.files(graphs).isEmpty(), "A changed router output wrote no pack");
        router.set("barrier", new RouterGraph.DimensionDefault());
        assertTrue(this.files(graphs).isEmpty(), "A router on dimension default wrote a pack");
    }

    @Test
    void restoreAppliesTheRouterTheEntriesAndThePositions() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        RouterGraph editedRouter = edited.router(OVERWORLD);
        assertNotNull(editedRouter);
        unlink(editedRouter, "barrier", new RouterGraph.Constant(BARRIER));
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        FunctionGraph editedEntry = edited.edited().get(ISLANDS);
        assertNotNull(editedEntry);
        Integer editedRoot = editedEntry.output();
        assertNotNull(editedRoot);
        edited.positions(new IslandGraphs.GraphId.Entry(ISLANDS)).put(editedRoot, ROOT_POSITION);
        Integer editedFinalDensity = editedRouter.linked("final_density");
        assertNotNull(editedFinalDensity);
        edited.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).put(editedFinalDensity, ROUTER_POSITION);
        Map<String, JsonElement> files = this.files(edited);

        // Registries with the pack give the router of the pack, so the start loads that router.
        IslandGraphs restored = graphs(files.get(SETTINGS_PATH).getAsJsonObject().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        RouterGraph router = restored.router(OVERWORLD);
        assertNotNull(router);
        Integer barrier = router.linked("barrier");
        assertNotNull(barrier, "The restored barrier output has no link");
        assertEquals(FunctionNode.Typed.bareConstant(BARRIER), router.functionNode(barrier));
        Integer finalDensity = router.linked("final_density");
        assertNotNull(finalDensity, "The restored final density output has no link");
        assertEquals(ROUTER_POSITION, restored.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).get(finalDensity),
                "The router sidecar did not place the expanded function node at its path");
        assertEquals(settings().getAsJsonObject("noise_router"), restored.shipped(OVERWORLD));
        FunctionGraph entry = restored.edited().get(ISLANDS);
        assertNotNull(entry, "The restore did not make the entry an edited function graph");
        Integer root = entry.output();
        assertNotNull(root);
        assertEquals(ROOT_POSITION, restored.positions(new IslandGraphs.GraphId.Entry(ISLANDS)).get(root));
        assertEquals(OTHER, restored.islands(OVERWORLD, loader()).orElseThrow());
        assertEquals(files, this.files(restored), "The restored graphs write another pack");
    }

    @Test
    void routerBoxPositionRoundTripsThroughTheRouterSidecar() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        RouterGraph editedRouter = edited.router(OVERWORLD);
        assertNotNull(editedRouter);
        unlink(editedRouter, "barrier", new RouterGraph.Constant(BARRIER));
        edited.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).put(GraphLayout.BOX, BOX_POSITION);
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        edited.positions(new IslandGraphs.GraphId.Entry(ISLANDS)).put(GraphLayout.BOX, BOX_POSITION);
        Map<String, JsonElement> files = this.files(edited);
        JsonObject router = files.get(ROUTER_SIDECAR_PATH).getAsJsonObject().getAsJsonObject("router");
        assertNotNull(router, "The router sidecar has no router field");
        assertEquals(BOX_POSITION.x(), router.get("x").getAsFloat());
        assertEquals(BOX_POSITION.y(), router.get("y").getAsFloat());
        assertFalse(files.get(ENTRY_SIDECAR_PATH).getAsJsonObject().has("router"), "The entry sidecar has a router field");

        IslandGraphs restored = graphs(files.get(SETTINGS_PATH).getAsJsonObject().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertEquals(BOX_POSITION, restored.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).get(GraphLayout.BOX),
                "The restore did not place the router box");
        assertEquals(files, this.files(restored), "The restored graphs write another pack");
    }

    @Test
    void routerSidecarWithoutRouterFieldStillLoads() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        RouterGraph editedRouter = edited.router(OVERWORLD);
        assertNotNull(editedRouter);
        unlink(editedRouter, "barrier", new RouterGraph.Constant(BARRIER));
        Map<String, JsonElement> files = new LinkedHashMap<>(this.files(edited));
        assertFalse(files.get(ROUTER_SIDECAR_PATH).getAsJsonObject().has("router"), "A router box without a position wrote a router field");
        IslandGraphs restored = graphs(files.get(SETTINGS_PATH).getAsJsonObject().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertFalse(restored.positions(new IslandGraphs.GraphId.Router(OVERWORLD)).containsKey(GraphLayout.BOX), "The restore invented a router box position");
    }

    @Test
    void startJsonIsWrittenAndRestored() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        JsonElement start = edited.startJson(ISLANDS);
        assertNotNull(start, "The first load kept no start JSON");
        Map<String, JsonElement> files = this.files(edited);
        assertEquals(start, files.get(START_JSON_PATH), "The pack holds another start JSON");
        assertEquals(START_JSON_PATH, IslandPack.startJsonPath(ISLANDS));

        IslandGraphs restored = graphs(settings().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertEquals(start, restored.startJson(ISLANDS), "The restore did not give the entry its start JSON");
        assertEquals(files, this.files(restored), "The restored graphs write another pack");
    }

    @Test
    void restoredEntrySetBackToItsStartWritesNoFile() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        IslandSettings start = edited.islands(OVERWORLD, loader()).orElseThrow();
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        Map<String, JsonElement> files = this.files(edited);

        IslandGraphs restored = graphs(settings().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertTrue(restored.setIslands(OVERWORLD, start, loader()));
        assertTrue(this.files(restored).isEmpty(), "A restored entry equal to its start JSON wrote a pack");
    }

    @Test
    void olderPackWithoutStartJsonStillDiffers() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        IslandSettings start = edited.islands(OVERWORLD, loader()).orElseThrow();
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        Map<String, JsonElement> files = new LinkedHashMap<>(this.files(edited));
        assertNotNull(files.remove(START_JSON_PATH), "The pack holds no start JSON");

        IslandGraphs restored = graphs(settings().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertTrue(restored.setIslands(OVERWORLD, start, loader()));
        Map<String, JsonElement> again = this.files(restored);
        assertTrue(again.containsKey(ENTRY_PATH), "A restored entry without a start JSON wrote no file");
        assertFalse(again.containsKey(START_JSON_PATH), "An entry without a start JSON wrote a start JSON file");
    }

    @Test
    void newEntryWritesTheRegistryEntryAsItsStartJson() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        JsonElement registry = GraphJson.write(GraphJson.readFunction(generated(ENTRY_PATH)).getOrThrow()).getOrThrow();
        Map<String, JsonElement> files = this.files(edited);
        assertEquals(registry, files.get(START_JSON_PATH), "The start JSON of a new entry is not its registry entry");
        assertFalse(registry.equals(files.get(ENTRY_PATH)), "The edited entry equals its registry entry");
    }

    @Test
    void entrySidecarWithRouterFieldGivesNoBoxPosition() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        Map<String, JsonElement> files = new LinkedHashMap<>(this.files(edited));
        JsonObject sidecar = files.get(ENTRY_SIDECAR_PATH).getAsJsonObject().deepCopy();
        JsonObject box = new JsonObject();
        box.addProperty("x", BOX_POSITION.x());
        box.addProperty("y", BOX_POSITION.y());
        sidecar.add("router", box);
        files.put(ENTRY_SIDECAR_PATH, sidecar);

        IslandGraphs restored = graphs(settings().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertTrue(restored.edited().containsKey(ISLANDS), "The entry with a router field did not restore");
        assertFalse(restored.positions(new IslandGraphs.GraphId.Entry(ISLANDS)).containsKey(GraphLayout.BOX),
                "The entry graph took the router field of its sidecar");
    }

    @Test
    void startJsonCompareIgnoresTheKeySequence() {
        IslandGraphs edited = graphs(settings().getAsJsonObject("noise_router"));
        IslandSettings start = edited.islands(OVERWORLD, loader()).orElseThrow();
        assertTrue(edited.setIslands(OVERWORLD, OTHER, loader()));
        Map<String, JsonElement> files = new LinkedHashMap<>(this.files(edited));
        JsonElement reversed = reversed(files.get(START_JSON_PATH));
        assertFalse(reversed.toString().equals(files.get(START_JSON_PATH).toString()), "The reversed start JSON keeps the key sequence");
        files.put(START_JSON_PATH, reversed);

        IslandGraphs restored = graphs(settings().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertTrue(restored.setIslands(OVERWORLD, start, loader()));
        assertTrue(this.files(restored).isEmpty(), "A start JSON in another key sequence counts as a change");
    }

    private static final ResourceLocation BASIC = ResourceLocation.fromNamespaceAndPath("aerolite", "overworld_basic");
    private static final ResourceLocation LIST = ResourceLocation.fromNamespaceAndPath("aerolite", "overworld_islands");
    private static final ResourceLocation NEW_LIST = ResourceLocation.fromNamespaceAndPath("aerolite_world", "list_1");
    private static final String BASIC_PATH = "data/aerolite/aerolite/island_variation/overworld_basic.json";
    private static final String BASIC_START_PATH = "data/aerolite/aerolite_sidecar/start_json/island_variation/aerolite/overworld_basic.json";
    private static final String NEW_LIST_PATH = "data/aerolite_world/aerolite/island_variation_list/list_1.json";
    private static final String NEW_LIST_START_PATH = "data/aerolite/aerolite_sidecar/start_json/island_variation_list/aerolite_world/list_1.json";

    /** The shipped Basic variation and list of the Overworld, from the generated resources. */
    private static IslandGraphs.VariationLoader variations() {
        return new IslandGraphs.VariationLoader() {

            @Override
            public DataResult<JsonObject> variation(ResourceLocation id) {
                return id.equals(BASIC) ? DataResult.success(generated("data/aerolite/aerolite/island_variation/overworld_basic.json"))
                        : DataResult.error(() -> "No variation " + id);
            }

            @Override
            public DataResult<JsonObject> list(ResourceLocation id) {
                return id.equals(LIST) ? DataResult.success(generated("data/aerolite/aerolite/island_variation_list/overworld_islands.json"))
                        : DataResult.error(() -> "No list " + id);
            }

            @Override
            public Set<ResourceLocation> variationIds() {
                return Set.of(BASIC);
            }

            @Override
            public Set<ResourceLocation> listIds() {
                return Set.of(LIST);
            }
        };
    }

    /** Graphs with the fill of the Basic variation edited and one new list of the Basic variation. */
    private static IslandGraphs variationGraphs() {
        IslandGraphs graphs = graphs(settings().getAsJsonObject("noise_router"));
        JsonObject basic = graphs.variation(BASIC, variations()).getOrThrow();
        JsonArray fill = new JsonArray();
        fill.add(0.5);
        fill.add(0.95);
        basic.add("fill", fill);
        graphs.putVariation(BASIC, basic);
        graphs.putList(NEW_LIST, JsonParser.parseString("{\"entries\": [{\"variation\": \"aerolite:overworld_basic\", \"weight\": 1}]}").getAsJsonObject());
        return graphs;
    }

    @Test
    void changedVariationAndNewListGoToTheirRegistryFolders() {
        IslandGraphs graphs = variationGraphs();
        Map<String, JsonElement> files = this.files(graphs);
        assertTrue(files.containsKey(IslandPack.PACK_META), "The pack has no pack.mcmeta");
        assertEquals(BASIC_PATH, IslandPack.entryPath(IslandVariationRegistries.ISLAND_VARIATION, BASIC));
        assertEquals(graphs.editedVariations().get(BASIC), files.get(BASIC_PATH));
        assertEquals(generated("data/aerolite/aerolite/island_variation/overworld_basic.json"), files.get(BASIC_START_PATH));
        assertEquals(graphs.editedLists().get(NEW_LIST), files.get(NEW_LIST_PATH));
        assertFalse(files.containsKey(NEW_LIST_START_PATH), "A new list has a start JSON file");
        assertFalse(files.containsKey(SETTINGS_PATH), "An edited variation alone wrote the noise settings");
    }

    @Test
    void variationSetBackToItsStartWritesNoPack() {
        IslandGraphs graphs = graphs(settings().getAsJsonObject("noise_router"));
        JsonObject basic = graphs.variation(BASIC, variations()).getOrThrow();
        graphs.putVariation(BASIC, reversed(basic).getAsJsonObject());
        assertTrue(this.files(graphs).isEmpty(), "A variation equal to its start JSON wrote a pack");
        graphs.putList(LIST, graphs.list(LIST, variations()).getOrThrow());
        assertTrue(this.files(graphs).isEmpty(), "A list equal to its start JSON wrote a pack");
    }

    @Test
    void restoreReadsBothRegistryFoldersBack() {
        IslandGraphs graphs = variationGraphs();
        Map<String, JsonElement> files = this.files(graphs);
        IslandGraphs restored = graphs(settings().getAsJsonObject("noise_router"));
        this.pack.restore(files, restored);
        assertEquals(graphs.editedVariations(), restored.editedVariations());
        assertEquals(graphs.editedLists(), restored.editedLists());
        assertEquals(generated("data/aerolite/aerolite/island_variation/overworld_basic.json"), restored.variationStartJson(BASIC));
        assertEquals(graphs.editedVariations().get(BASIC), restored.variation(BASIC, variations()).getOrThrow());
        assertEquals(files, this.files(restored), "The restored graphs write another pack");
    }

    @Test
    void islandProblemOfAnEditedListStopsThePack() {
        IslandGraphs graphs = variationGraphs();
        graphs.putList(NEW_LIST, JsonParser.parseString("{\"entries\": [{\"variation\": \"aerolite:gone\", \"weight\": 1}]}").getAsJsonObject());
        DataResult<Map<String, JsonElement>> files = this.pack.files(graphs, Map.of(OVERWORLD, new IslandPack.NoiseSettingsEntry(SETTINGS_ID, settings())), null,
                graphs.entries(variations(), loader()));
        assertTrue(files.isError(), "A list with a missing entry wrote a pack");
        assertTrue(files.error().orElseThrow().message().contains("names the missing island variation aerolite:gone"), files.error().orElseThrow().message());
    }

    /** A copy of the JSON with the keys of each object in reversed sequence. */
    private static JsonElement reversed(JsonElement json) {
        if (json.isJsonArray()) {
            JsonArray array = new JsonArray();
            json.getAsJsonArray().forEach(element -> array.add(reversed(element)));
            return array;
        }
        if (!json.isJsonObject()) {
            return json.deepCopy();
        }
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(json.getAsJsonObject().entrySet());
        Collections.reverse(entries);
        JsonObject object = new JsonObject();
        entries.forEach(entry -> object.add(entry.getKey(), reversed(entry.getValue())));
        return object;
    }

}
