package us.drullk.aerolite.world.island.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the island checks of the validator: the missing carrier, the three list problems, the variation problems,
 * and the problems of the function graph of a variation on its function nodes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandChecksTest {

    private static final ResourceLocation ISLANDS_ENTRY = id("island_variations/overworld");
    private static final ResourceLocation BASIC_ENTRY = id("island_variations/basic");
    private static final ResourceLocation LIST = id("overworld_islands");
    private static final ResourceLocation OTHER_LIST = id("other_islands");
    private static final ResourceLocation BASIC = id("overworld_basic");
    private static final ResourceLocation EMPTY = id("overworld_empty");
    private static final String ISLAND_FUNCTION = """
            {"type": "aerolite:island_variations", "noise": "aerolite:islands", "cell_size": 256, "cell_height": 128, "layer_min_y": 0, "jitter": 0.8,
             "variations": "aerolite:overworld_islands"}
            """;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aerolite", path);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** Entries over three maps. */
    private record MapEntries(Map<ResourceLocation, JsonElement> functions, Map<ResourceLocation, JsonObject> variations,
                              Map<ResourceLocation, JsonObject> lists) implements IslandEntries {

        @Override
        @Nullable
        public JsonElement densityFunction(ResourceLocation id) {
            return this.functions.get(id);
        }

        @Override
        @Nullable
        public JsonObject variation(ResourceLocation id) {
            return this.variations.get(id);
        }

        @Override
        @Nullable
        public JsonObject list(ResourceLocation id) {
            return this.lists.get(id);
        }
    }

    /** The shipped shape: a list of Empty and Basic, the Basic graph and the island function entry. */
    private static MapEntries shipped() {
        Map<ResourceLocation, JsonElement> functions = new HashMap<>();
        functions.put(ISLANDS_ENTRY, json(ISLAND_FUNCTION));
        functions.put(BASIC_ENTRY, json("{\"type\": \"aerolite:island_body\"}"));
        Map<ResourceLocation, JsonObject> variations = new HashMap<>();
        variations.put(BASIC, json("{\"density_function\": \"aerolite:island_variations/basic\"}"));
        variations.put(EMPTY, json("{\"empty\": true}"));
        Map<ResourceLocation, JsonObject> lists = new HashMap<>();
        lists.put(LIST, json("""
                {"entries": [{"variation": "aerolite:overworld_empty", "weight": 1}, {"variation": "aerolite:overworld_basic", "weight": 9}]}
                """));
        return new MapEntries(functions, variations, lists);
    }

    private static List<String> texts(List<GraphProblem> problems) {
        return problems.stream().map(GraphProblem::toString).toList();
    }

    private static List<String> listProblems(MapEntries entries) {
        return texts(GraphValidator.islands(List.of(LIST), List.of(), entries).problems());
    }

    @Test
    void shippedListHasNoProblemAndReachesBothVariations() {
        GraphValidator.IslandReach reach = GraphValidator.islands(List.of(LIST), List.of(), shipped());
        assertEquals(List.of(), reach.problems());
        assertEquals(Set.of(LIST), reach.lists());
        assertEquals(Set.of(EMPTY, BASIC), reach.variations());
    }

    @Test
    void listWithAMissingEntry() {
        MapEntries entries = shipped();
        entries.lists().put(LIST, json("""
                {"entries": [{"variation": "aerolite:gone", "weight": 1}, {"list": "aerolite:gone_list", "weight": 2},
                             {"variation": "aerolite:overworld_basic", "weight": 1}]}
                """));
        assertEquals(List.of("[aerolite:overworld_islands] The entry 0 names the missing island variation aerolite:gone",
                "[aerolite:overworld_islands] The entry 1 names the missing island variation list aerolite:gone_list"), listProblems(entries));
        assertEquals(List.of("[aerolite:other_islands] The island variation list aerolite:other_islands does not exist"),
                texts(GraphValidator.islands(List.of(OTHER_LIST), List.of(), entries).problems()));
    }

    @Test
    void listWithoutAPositiveWeight() {
        MapEntries entries = shipped();
        entries.lists().put(LIST, json("""
                {"entries": [{"variation": "aerolite:overworld_empty", "weight": 0}, {"variation": "aerolite:overworld_basic", "weight": 0}]}
                """));
        assertEquals(List.of("[aerolite:overworld_islands] The list holds no entry with a positive weight"), listProblems(entries));
    }

    @Test
    void listThatNamesItselfDirectlyOrThroughAnotherList() {
        MapEntries entries = shipped();
        entries.lists().put(LIST, json("""
                {"entries": [{"variation": "aerolite:overworld_basic", "weight": 1}, {"list": "aerolite:overworld_islands", "weight": 1}]}
                """));
        assertEquals(List.of("[aerolite:overworld_islands] The list names itself through its entries"), listProblems(entries));
        entries.lists().put(LIST, json("""
                {"entries": [{"variation": "aerolite:overworld_basic", "weight": 1}, {"list": "aerolite:other_islands", "weight": 1}]}
                """));
        entries.lists().put(OTHER_LIST, json("""
                {"entries": [{"list": "aerolite:overworld_islands", "weight": 1}]}
                """));
        assertEquals(List.of("[aerolite:overworld_islands] The list names itself through its entries",
                "[aerolite:other_islands] The list names itself through its entries"), listProblems(entries));
    }

    @Test
    void variationWithAMissingDensityFunctionEntry() {
        MapEntries entries = shipped();
        entries.variations().put(BASIC, json("{\"density_function\": \"aerolite:gone\"}"));
        assertEquals(List.of("[aerolite:overworld_basic] The island variation names the missing density function entry aerolite:gone"), listProblems(entries));
    }

    @Test
    void variationGraphWithACacheOrAnIslandFunction() {
        MapEntries entries = shipped();
        entries.functions().put(BASIC_ENTRY, json("""
                {"type": "minecraft:add", "argument1": {"type": "minecraft:flat_cache", "argument": {"type": "aerolite:island_body"}},
                 "argument2": {"type": "minecraft:cache_2d", "argument": "aerolite:island_variations/overworld"}}
                """));
        List<String> problems = listProblems(entries);
        assertEquals(3, problems.size(), problems::toString);
        assertTrue(problems.get(0).startsWith("[aerolite:overworld_basic] The graph holds minecraft:flat_cache at argument1. The island function drops"), problems.get(0));
        assertTrue(problems.get(1).startsWith("[aerolite:overworld_basic] The graph holds minecraft:cache_2d at argument2."), problems.get(1));
        assertTrue(problems.get(2).startsWith("[aerolite:overworld_basic] The graph holds aerolite:island_variations at argument2/argument. A variation graph holds no island function"),
                problems.get(2));
    }

    @Test
    void variationGraphProblemsSitOnTheirFunctionNodes() {
        MapEntries entries = shipped();
        entries.functions().put(id("cached"), json("""
                {"type": "minecraft:cache_2d", "argument": {"type": "aerolite:cell_gauge"}}
                """));
        FunctionGraph graph = GraphJson.readFunction(JsonParser.parseString("""
                {"type": "minecraft:add", "argument1": {"type": "minecraft:flat_cache", "argument": {"type": "aerolite:cell_random", "min": 0.0, "max": 1.0, "channel": 0}},
                 "argument2": {"type": "minecraft:max", "argument1": "aerolite:cached", "argument2": "aerolite:island_variations/overworld"}}
                """)).getOrThrow();
        List<GraphProblem> problems = GraphValidator.variationGraph(graph, entries);
        assertEquals(3, problems.size(), problems::toString);
        assertEquals(graph.paths().get("argument1"), problems.get(0).functionNode());
        assertEquals("argument1", problems.get(0).path());
        assertEquals(graph.paths().get("argument2/argument1"), problems.get(1).functionNode());
        assertTrue(problems.get(1).message().startsWith("The entry aerolite:cached holds minecraft:cache_2d at /."), problems.get(1).message());
        assertEquals(graph.paths().get("argument2/argument2"), problems.get(2).functionNode());
        assertTrue(problems.get(2).message().startsWith("The entry aerolite:island_variations/overworld holds aerolite:island_variations"), problems.get(2).message());
        assertEquals(List.of(), GraphValidator.variationGraph(GraphJson.readFunction(json("{\"type\": \"aerolite:island_body\"}")).getOrThrow(), entries));
    }

    @Test
    void routerWithoutACarrierShowsAProblemOnTheRouterBox() {
        MapEntries entries = shipped();
        JsonObject router = json("""
                {"temperature": {"type": "aerolite:island_carrier", "islands": "aerolite:island_variations/overworld", "argument": 0.0},
                 "final_density": {"type": "minecraft:interpolated", "argument": "aerolite:island_variations/overworld"}}
                """);
        assertEquals(List.of(), GraphValidator.carrier(router, entries));
        assertEquals(Set.of(LIST), GraphValidator.islandLists(router, entries));
        router.addProperty("temperature", 0.0);
        List<GraphProblem> problems = GraphValidator.carrier(router, entries);
        assertEquals(1, problems.size());
        assertEquals(GraphLayout.BOX, problems.getFirst().functionNode());
        assertTrue(problems.getFirst().message().startsWith("No climate output holds a carrier over an island function"), problems.getFirst().message());
        router.add("vegetation", json("""
                {"type": "minecraft:flat_cache", "argument": {"type": "aerolite:island_carrier", "islands": "aerolite:island_variations/overworld", "argument": 0.0}}
                """));
        assertEquals(List.of(), GraphValidator.carrier(router, entries), "A carrier under a marker on another climate output counts");
        router.add("vegetation", json("""
                {"type": "aerolite:island_carrier", "islands": 0.0, "argument": 0.0}
                """));
        assertEquals(1, GraphValidator.carrier(router, entries).size(), "A carrier without an island function does not count");
        assertEquals(List.of(), GraphValidator.carrier(json("{\"final_density\": 0.0, \"temperature\": 0.0}"), entries),
                "A router without an island function has no carrier problem");
    }

}
