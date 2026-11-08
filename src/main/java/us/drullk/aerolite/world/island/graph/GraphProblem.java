package us.drullk.aerolite.world.island.graph;

/**
 * One reason why a graph does not build, at the path of a function node or of an input pin.
 * A function node outside the trees has no path, so its problem path is its id after {@code #}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public record GraphProblem(String path, String message) {

    /** The text of a problem: the path in brackets, then the message. The root path shows as {@code /}. */
    public static String format(String path, String message) {
        return "[" + (path.isEmpty() ? "/" : path) + "] " + message;
    }

    /** The problem of a function node outside the trees, at its id. */
    public static GraphProblem detached(int functionNode) {
        return new GraphProblem("#" + functionNode, "The function node is in no tree under an output, so the save drops it");
    }

    @Override
    public String toString() {
        return format(this.path, this.message);
    }

}
