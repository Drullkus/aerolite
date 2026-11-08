package us.drullk.aerolite.world.island.graph;

/**
 * The kind of one field of a function node type. It sets the JSON form of the field value.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public enum FieldKind {
    /** An input pin. Its value is a child density function. */
    FUNCTION,
    /** A JSON number that the vanilla codec reads as a double. */
    NUMBER,
    /** A JSON number that the vanilla codec reads as an int. */
    INTEGER,
    /** A noise parameters holder: an id string or an inline noise object. */
    NOISE,
    /** One string of a fixed list. */
    ENUM,
    /** A cubic spline. Each spline coordinate is an input pin. */
    SPLINE
}
