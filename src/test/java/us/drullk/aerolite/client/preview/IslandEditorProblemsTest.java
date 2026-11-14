package us.drullk.aerolite.client.preview;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.graph.FunctionGraph;
import us.drullk.aerolite.world.island.graph.GraphJson;
import us.drullk.aerolite.world.island.graph.GraphLayout;
import us.drullk.aerolite.world.island.graph.GraphProblem;
import us.drullk.aerolite.world.island.graph.RouterGraph;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the split of the problems and the build error by function node, as the field editor shows them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
class IslandEditorProblemsTest {

    private static FunctionGraph graph() {
        return GraphJson.readFunction(JsonParser.parseString("""
                {"type": "minecraft:add", "argument1": {"type": "minecraft:abs", "argument": "minecraft:zero"}, "argument2": 1.0}
                """)).getOrThrow();
    }

    @Test
    void noProblemAndNoBuildErrorShowNothing() {
        assertSame(IslandEditor.ShownProblems.NONE, IslandEditor.split(List.of(), null, graph()));
        assertFalse(IslandEditor.ShownProblems.NONE.notice());
    }

    @Test
    void problemOfTheOutputBoxShowsOnTheBoxAndWithoutASelection() {
        GraphProblem box = new GraphProblem("", "A router box problem", GraphLayout.BOX);
        IslandEditor.ShownProblems shown = IslandEditor.split(List.of(box), null, graph());
        assertTrue(shown.has(GraphLayout.BOX));
        assertEquals(List.of(box.toString()), shown.of(GraphLayout.BOX));
        assertEquals(List.of(box.toString()), shown.general());
    }

    @Test
    void problemsSplitByTheirFunctionNode() {
        GraphProblem node = new GraphProblem("argument1/argument", "A node problem", 3);
        GraphProblem general = new GraphProblem("", "A graph problem");
        IslandEditor.ShownProblems shown = IslandEditor.split(List.of(node, general), null, graph());
        assertEquals(Map.of(3, List.of(node.toString())), shown.byFunctionNode());
        assertEquals(List.of(node.toString()), shown.of(3));
        assertEquals(List.of(), shown.of(null));
        assertEquals(List.of(), shown.of(4));
        assertEquals(List.of(general.toString()), shown.general());
        assertTrue(shown.has(3));
        assertTrue(shown.notice());
    }

    @Test
    void buildErrorLinesNameTheFunctionNodeAtTheirPath() {
        FunctionGraph graph = graph();
        int abs = graph.paths().get("argument1");
        GraphProblem repeated = new GraphProblem("argument2", "A repeated problem", graph.paths().get("argument2"));
        String buildError = repeated + "\n[argument1/argument] The entry minecraft:zero does not build\nNo key type in MapLike\n[missing/path] Elsewhere";
        IslandEditor.ShownProblems shown = IslandEditor.split(List.of(repeated), buildError, graph);
        assertEquals(List.of("[argument1/argument] The entry minecraft:zero does not build"), shown.of(graph.paths().get("argument1/argument")));
        assertEquals(List.of(repeated.toString()), shown.of(repeated.functionNode()));
        assertEquals(List.of("No key type in MapLike"), shown.general().subList(0, 1));
        assertEquals(List.of(), shown.of(abs));
        // A path outside the graph resolves to the root function node, the deepest one on that path.
        assertEquals(List.of("[missing/path] Elsewhere"), shown.of(graph.paths().get("")));
        assertTrue(shown.notice());
    }

    @Test
    void selectedFunctionNodeShowsOnlyItsOwnProblems() {
        GraphProblem first = new GraphProblem("argument1", "The first problem", 1);
        GraphProblem second = new GraphProblem("argument2", "The second problem", 2);
        IslandEditor.ShownProblems shown = IslandEditor.split(List.of(first, second), null, graph());
        assertEquals(List.of(first.toString()), shown.of(1));
        assertEquals(List.of(second.toString()), shown.of(2));
        assertEquals(List.of(), shown.of(null));
        assertEquals(List.of(), shown.general());
        assertTrue(shown.notice());
    }

    @Test
    void routerBuildErrorAtAPathWithoutAFunctionNodeShowsAsGeneral() {
        RouterGraph router = new RouterGraph(new RouterGraph.DimensionDefault());
        int root = GraphJson.readInto(router, JsonParser.parseString("""
                {"type": "minecraft:abs", "argument": 2.0}
                """), "final_density").getOrThrow();
        assertTrue(router.link(root, "final_density"));
        int argument = router.paths().get("final_density/argument");
        String buildError = "[final_density/argument] Out of range\n[final_density/argument/deeper] Below it\n"
                + "[depth] On dimension default\n[/] At the root\nA line without a path";
        IslandEditor.ShownProblems shown = IslandEditor.split(List.of(), buildError, router);
        assertEquals(List.of("[final_density/argument] Out of range", "[final_density/argument/deeper] Below it"), shown.of(argument));
        assertFalse(shown.has(root));
        assertEquals(List.of("[depth] On dimension default", "[/] At the root", "A line without a path"), shown.general());
        assertTrue(shown.notice());
    }

    @Test
    void buildErrorAloneGivesTheNotice() {
        IslandEditor.ShownProblems shown = IslandEditor.split(List.of(), "A decode error", graph());
        assertEquals(Map.of(), shown.byFunctionNode());
        assertEquals(List.of("A decode error"), shown.general());
        assertTrue(shown.notice());
    }

    @Test
    void buildErrorWithoutAGraphShowsAsGeneral() {
        IslandEditor.ShownProblems shown = IslandEditor.split(List.of(), "[final_density] The input has no function node", null);
        assertEquals(Map.of(), shown.byFunctionNode());
        assertEquals(List.of("[final_density] The input has no function node"), shown.general());
        assertTrue(shown.notice());
    }

}
