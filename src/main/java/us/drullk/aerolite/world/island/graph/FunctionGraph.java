package us.drullk.aerolite.world.island.graph;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A graph with one output. It maps to one density function JSON tree. The path of its root function node is the empty string.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class FunctionGraph extends FunctionNodeGraph {

    /** The name of the one graph output, and the path of the root function node. */
    public static final String OUTPUT = "";

    public FunctionGraph() {
        super(List.of(OUTPUT));
    }

    /** Links a function node to the output, with the rules of {@link #link(int, Pin)}. */
    public boolean setOutput(int source) {
        return this.linkOutput(source, OUTPUT);
    }

    /** The root function node, or null. */
    @Nullable
    public Integer output() {
        return this.outputSource(OUTPUT);
    }

}
