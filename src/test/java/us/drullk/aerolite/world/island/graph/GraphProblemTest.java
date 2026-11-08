package us.drullk.aerolite.world.island.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Checks the problem text form and the parse of an error text into lines with paths.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
class GraphProblemTest {

    @Test
    void twoArgumentProblemHasNoFunctionNode() {
        assertNull(new GraphProblem("final_density", "A message").functionNode());
        assertEquals(new GraphProblem("final_density", "A message", null), new GraphProblem("final_density", "A message"));
    }

    @Test
    void parseReadsThePathOfEachFormattedLine() {
        String text = GraphProblem.format("final_density/argument", "Out of range") + "\n" + GraphProblem.format("", "At the root");
        assertEquals(List.of(new GraphProblem.Parsed("[final_density/argument] Out of range", "final_density/argument"),
                new GraphProblem.Parsed("[/] At the root", "")), GraphProblem.parse(text));
    }

    @Test
    void parseGivesNoPathToAnUnformattedLine() {
        assertEquals(List.of(new GraphProblem.Parsed("No key type in MapLike", null),
                        new GraphProblem.Parsed("[final_density]without a space", null),
                        new GraphProblem.Parsed("[] An empty path", null)),
                GraphProblem.parse("No key type in MapLike\n\n[final_density]without a space\n[] An empty path\n"));
    }

    @Test
    void parseKeepsTheDetachedIdForm() {
        GraphProblem detached = GraphProblem.detached(7);
        assertEquals(List.of(new GraphProblem.Parsed(detached.toString(), "#7")), GraphProblem.parse(detached.toString()));
    }

}
