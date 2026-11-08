package us.drullk.aerolite.world.island.graph;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One reason why a graph does not build, at the path of a function node or of an input pin.
 * A function node outside the trees has no path, so its problem path is its id after {@code #}.
 * The function node id names the function node that holds the problem. A problem of a graph output without a function node has none.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
public record GraphProblem(String path, String message, @Nullable Integer functionNode) {

    private static final String ROOT = "/";
    private static final String DETACHED = "#";

    /** A problem without a function node. */
    public GraphProblem(String path, String message) {
        this(path, message, null);
    }

    /** The text of a problem: the path in brackets, then the message. The root path shows as {@code /}. */
    public static String format(String path, String message) {
        return "[" + (path.isEmpty() ? ROOT : path) + "] " + message;
    }

    /** The problem of a function node outside the trees, at its id. */
    public static GraphProblem detached(int functionNode) {
        return new GraphProblem(DETACHED + functionNode, "The function node is in no tree under an output, so the save drops it", functionNode);
    }

    /**
     * The problems of an error text, one per line. A line in the {@link #format} form gives its path and message.
     * Any other line gives a problem with a null path. No parsed problem has a function node.
     */
    public static List<Parsed> parse(String text) {
        List<Parsed> parsed = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int close = line.indexOf("] ");
            if (line.startsWith("[") && close > 1) {
                String path = line.substring(1, close);
                parsed.add(new Parsed(line, path.equals(ROOT) ? "" : path));
            } else {
                parsed.add(new Parsed(line, null));
            }
        }
        return parsed;
    }

    /** One line of an error text and its path, or null when the line names no path. */
    public record Parsed(String text, @Nullable String path) {
    }

    @Override
    public String toString() {
        return format(this.path, this.message);
    }

}
