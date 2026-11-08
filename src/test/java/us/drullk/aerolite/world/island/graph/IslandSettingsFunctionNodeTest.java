package us.drullk.aerolite.world.island.graph;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.island.IslandSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the map between the island settings and the fields of an {@code aerolite:voronoi_islands} function node.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
class IslandSettingsFunctionNodeTest {

    private static FunctionNode.Typed shipped() {
        FunctionGraph graph = GraphJson.readFunction(GraphTestData.islandFunction("overworld")).getOrThrow();
        return (FunctionNode.Typed) graph.functionNode(graph.output());
    }

    @Test
    void shippedFunctionNodeReadsAsTheDefaultSettings() {
        assertEquals(IslandSettings.DEFAULT, IslandSettingsFunctionNode.read(shipped()).orElseThrow());
    }

    @Test
    void writeThenReadGivesTheSettings() {
        IslandSettings other = new IslandSettings(320, 96, -32, 0.25, 0.1, 0.7, 0.35, 6.0, 128, 2);
        FunctionNode.Typed written = IslandSettingsFunctionNode.write(shipped(), other);
        assertEquals(other, IslandSettingsFunctionNode.read(written).orElseThrow());
        assertEquals(shipped().values().get("noise"), written.values().get("noise"), "The write changed the noise field");
    }

    @Test
    void writtenFunctionNodeSavesTheVanillaFieldSequence() {
        FunctionGraph graph = new FunctionGraph();
        graph.setOutput(graph.add(IslandSettingsFunctionNode.write(shipped(), IslandSettings.DEFAULT)));
        assertEquals(GraphTestData.islandFunction("overworld"), GraphJson.write(graph).getOrThrow());
    }

    @Test
    void otherTypeReadsAsEmpty() {
        assertTrue(IslandSettingsFunctionNode.read(FunctionNode.Typed.bareConstant(1)).isEmpty());
    }

}
