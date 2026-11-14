package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the read, the start JSON, the edit and the copy of the island variations and lists of the graphs.
 * It checks the duplicate, the rename with its references and the removal of an edit. It checks the known ids and the new ids.
 * It also checks the density function of a variation as a function graph. It checks the list of the island function of a dimension.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandVariationGraphsTest {

    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceLocation BASIC = id("overworld_basic");
    private static final ResourceLocation EMPTY = id("overworld_empty");
    private static final ResourceLocation LIST = id("overworld_islands");
    private static final ResourceLocation BASIC_ENTRY = id("island_variations/basic");
    private static final ResourceLocation ISLANDS_ENTRY = id("island_variations/overworld");
    private static final String BASIC_JSON = """
            {"density_function": "aerolite:island_variations/basic", "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:plains"},
             "cell_weight": [0.5, 0.5], "fill": [0.3, 0.9], "distortion": 12.0, "distortion_scale": 64, "distortion_octaves": 3}
            """;
    private static final String EMPTY_JSON = """
            {"empty": true, "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:the_void"}, "cell_weight": [0.5, 0.5]}
            """;
    private static final String LIST_JSON = """
            {"entries": [{"variation": "aerolite:overworld_empty", "weight": 1}, {"variation": "aerolite:overworld_basic", "weight": 9}]}
            """;
    private static final String ISLAND_FUNCTION = """
            {"type": "aerolite:island_variations", "noise": "aerolite:islands", "cell_size": 256, "cell_height": 128, "layer_min_y": 0, "jitter": 0.8,
             "variations": "aerolite:overworld_islands"}
            """;

    private static final IslandGraphs.VariationLoader VARIATIONS = new IslandGraphs.VariationLoader() {

        @Override
        public DataResult<JsonObject> variation(ResourceLocation id) {
            if (id.equals(BASIC)) {
                return DataResult.success(json(BASIC_JSON));
            }
            return id.equals(EMPTY) ? DataResult.success(json(EMPTY_JSON)) : DataResult.error(() -> "No variation " + id);
        }

        @Override
        public DataResult<JsonObject> list(ResourceLocation id) {
            return id.equals(LIST) ? DataResult.success(json(LIST_JSON)) : DataResult.error(() -> "No list " + id);
        }

        @Override
        public Set<ResourceLocation> variationIds() {
            return Set.of(BASIC, EMPTY);
        }

        @Override
        public Set<ResourceLocation> listIds() {
            return Set.of(LIST);
        }
    };

    private static final IslandGraphs.EntryLoader LOADER = entry -> {
        if (entry.equals(BASIC_ENTRY)) {
            return GraphJson.readFunction(json("{\"type\": \"aerolite:island_body\"}"));
        }
        if (entry.equals(ISLANDS_ENTRY)) {
            return GraphJson.readFunction(json(ISLAND_FUNCTION));
        }
        return DataResult.error(() -> "No entry " + entry);
    };

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerolite", path);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static IslandGraphs graphs() {
        IslandGraphs graphs = new IslandGraphs();
        JsonObject router = json("""
                {"temperature": {"type": "aerolite:island_carrier", "islands": "aerolite:island_variations/overworld", "argument": 0.0},
                 "final_density": {"type": "minecraft:interpolated", "argument": "aerolite:island_variations/overworld"}}
                """);
        for (String field : RouterGraph.FIELDS) {
            if (!router.has(field)) {
                router.addProperty(field, 0.0);
            }
        }
        graphs.putRouter(OVERWORLD, GraphJson.readRouter(router).getOrThrow(), router);
        return graphs;
    }

    @Test
    void readKeepsTheStartJsonAndAnEditWinsOverTheRegistry() {
        IslandGraphs graphs = graphs();
        JsonObject basic = graphs.variation(BASIC, VARIATIONS).getOrThrow();
        assertEquals(json(BASIC_JSON), basic);
        assertEquals(json(BASIC_JSON), graphs.variationStartJson(BASIC));
        assertTrue(graphs.editedVariations().isEmpty());
        basic.addProperty("distortion", 6.0);
        assertEquals(12.0, graphs.variation(BASIC, VARIATIONS).getOrThrow().get("distortion").getAsDouble(), "A read gave the stored JSON, not a copy");
        graphs.putVariation(BASIC, basic);
        assertEquals(6.0, graphs.variation(BASIC, VARIATIONS).getOrThrow().get("distortion").getAsDouble());
        assertEquals(json(BASIC_JSON), graphs.variationStartJson(BASIC), "The edit changed the start JSON");
        IslandGraphs copy = graphs.copy();
        assertEquals(basic, copy.editedVariations().get(BASIC));
        assertEquals(json(BASIC_JSON), copy.variationStartJson(BASIC));
        assertTrue(graphs.removeVariationEdit(BASIC));
        assertFalse(graphs.removeVariationEdit(BASIC));
        assertEquals(12.0, graphs.variation(BASIC, VARIATIONS).getOrThrow().get("distortion").getAsDouble(), "The removal kept the edit");
        assertEquals(6.0, copy.variation(BASIC, VARIATIONS).getOrThrow().get("distortion").getAsDouble(), "The copy shares the edit");
        assertTrue(graphs.variation(id("gone"), VARIATIONS).isError());
        assertEquals(json(LIST_JSON), graphs.list(LIST, VARIATIONS).getOrThrow());
        assertEquals(json(LIST_JSON), graphs.listStartJson(LIST));
    }

    @Test
    void knownIdsAndNewIds() {
        IslandGraphs graphs = graphs();
        assertEquals(List.of(BASIC, EMPTY), List.copyOf(graphs.variationIds(VARIATIONS)));
        ResourceLocation first = graphs.newVariationId(VARIATIONS);
        assertEquals(ResourceLocation.fromNamespaceAndPath("aerolite_world", "island_1"), first);
        graphs.putVariation(first, json(EMPTY_JSON));
        assertEquals(ResourceLocation.fromNamespaceAndPath("aerolite_world", "island_2"), graphs.newVariationId(VARIATIONS));
        assertTrue(graphs.variationIds(VARIATIONS).contains(first));
        assertEquals(ResourceLocation.fromNamespaceAndPath("aerolite_world", "list_1"), graphs.newListId(VARIATIONS));
        assertNull(graphs.variationStartJson(first), "A new variation has a start JSON");
    }

    @Test
    void duplicateAndRenameKeepTheReferencesOfTheEditedLists() {
        IslandGraphs graphs = graphs();
        ResourceLocation copy = id("copy");
        assertEquals(copy, graphs.duplicateVariation(BASIC, copy, VARIATIONS).getOrThrow());
        assertEquals(json(BASIC_JSON), graphs.editedVariations().get(copy));
        assertTrue(graphs.duplicateVariation(BASIC, EMPTY, VARIATIONS).isError(), "A duplicate replaced a known id");
        graphs.putList(LIST, graphs.list(LIST, VARIATIONS).getOrThrow());
        ResourceLocation renamed = id("renamed_basic");
        assertEquals(renamed, graphs.renameVariation(BASIC, renamed, VARIATIONS).getOrThrow());
        assertEquals("aerolite:renamed_basic", graphs.editedLists().get(LIST).getAsJsonArray("entries").get(1).getAsJsonObject().get("variation").getAsString());
        assertTrue(graphs.editedVariations().containsKey(renamed));
        assertFalse(graphs.editedVariations().containsKey(BASIC));
        assertEquals(json(BASIC_JSON), graphs.variation(BASIC, VARIATIONS).getOrThrow(), "The old id lost its registry JSON");
        ResourceLocation renamedList = id("renamed_islands");
        assertTrue(graphs.function(ISLANDS_ENTRY, LOADER).isSuccess());
        graphs.markEdited(ISLANDS_ENTRY, graphs.function(ISLANDS_ENTRY, LOADER).getOrThrow());
        assertEquals(renamedList, graphs.renameList(LIST, renamedList, VARIATIONS).getOrThrow());
        assertEquals(renamedList, graphs.islandList(OVERWORLD, LOADER).orElseThrow(), "The island function node did not follow the rename");
        assertTrue(graphs.editedLists().containsKey(renamedList));
    }

    @Test
    void islandListReadsTheFirstIslandFunctionNode() {
        assertEquals(LIST, graphs().islandList(OVERWORLD, LOADER).orElseThrow());
    }

    @Test
    void variationFunctionNamesItsEntryOrMovesItsInlineTree() {
        IslandGraphs graphs = graphs();
        IslandGraphs.VariationFunction named = graphs.variationFunction(BASIC, VARIATIONS, LOADER).getOrThrow();
        assertEquals(new IslandGraphs.VariationFunction(BASIC_ENTRY, false), named);
        assertTrue(graphs.editedVariations().isEmpty(), "A named entry changed the variation");
        assertTrue(graphs.variationFunction(EMPTY, VARIATIONS, LOADER).isError(), "An Empty variation gave a density function");
        assertEquals(Set.of(BASIC_ENTRY), graphs.variationFunctions(VARIATIONS));
        ResourceLocation inline = id("inline");
        JsonObject inlineJson = json(BASIC_JSON);
        inlineJson.add("density_function", json("{\"type\": \"minecraft:add\", \"argument1\": {\"type\": \"aerolite:island_body\"}, \"argument2\": 1.0}"));
        graphs.putVariation(inline, inlineJson);
        IslandGraphs.VariationFunction moved = graphs.variationFunction(inline, VARIATIONS, LOADER).getOrThrow();
        assertEquals(new IslandGraphs.VariationFunction(inline, true), moved);
        assertEquals("aerolite:inline", graphs.editedVariations().get(inline).get("density_function").getAsString());
        FunctionGraph graph = graphs.edited().get(inline);
        assertNotNull(graph, "The inline tree is no edited function graph");
        assertEquals(inlineJson.get("density_function"), GraphJson.write(graph).getOrThrow());
        assertNull(graphs.startJson(inline), "The moved entry has a start JSON");
        assertEquals(new IslandGraphs.VariationFunction(inline, false), graphs.variationFunction(inline, VARIATIONS, LOADER).getOrThrow());
        ResourceLocation taken = BASIC_ENTRY;
        JsonObject clash = json(BASIC_JSON);
        clash.add("density_function", json("{\"type\": \"aerolite:island_body\"}"));
        graphs.putVariation(taken, clash);
        assertTrue(graphs.variationFunction(taken, VARIATIONS, LOADER).isError(), "An inline tree replaced an existing entry");
    }

    @Test
    void entriesReadTheEditsBeforeTheRegistries() {
        IslandGraphs graphs = graphs();
        IslandEntries entries = graphs.entries(VARIATIONS, LOADER);
        assertEquals(json("{\"type\": \"aerolite:island_body\"}"), entries.densityFunction(BASIC_ENTRY));
        assertNull(entries.densityFunction(id("gone")));
        JsonObject edited = json(EMPTY_JSON);
        edited.add("cell_weight", JsonParser.parseString("[0.2, 0.4]"));
        graphs.putVariation(EMPTY, edited);
        assertEquals(edited, entries.variation(EMPTY));
        assertEquals(json(LIST_JSON), entries.list(LIST));
        assertEquals(Map.of(), graphs.editedLists());
    }

}
