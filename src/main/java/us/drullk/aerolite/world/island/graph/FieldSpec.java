package us.drullk.aerolite.world.island.graph;

import java.util.List;

/**
 * The description of one field of a function node type. A {@code MapCodec} is not introspectable, so each type holds these by hand.
 * The range applies to {@link FieldKind#NUMBER} and {@link FieldKind#INTEGER}. Infinite bounds mean that vanilla checks no range.
 * The choices apply to {@link FieldKind#ENUM}.
 * The reference flag applies to {@link FieldKind#FUNCTION}: vanilla reads the input of {@code minecraft:clamp} without the registry, so it takes no reference node.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public record FieldSpec(String name, FieldKind kind, double min, double max, List<String> choices, boolean acceptsReference) {

    /** The range of {@code DensityFunctions.NOISE_VALUE_CODEC}. */
    public static final double NOISE_VALUE_LIMIT = 1000000.0;

    public FieldSpec {
        choices = List.copyOf(choices);
    }

    /** An input pin that takes a reference node. */
    public static FieldSpec function(String name) {
        return new FieldSpec(name, FieldKind.FUNCTION, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, List.of(), true);
    }

    /** An input pin that vanilla reads with {@code DensityFunction.DIRECT_CODEC}. It takes no reference node. */
    public static FieldSpec directFunction(String name) {
        return new FieldSpec(name, FieldKind.FUNCTION, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, List.of(), false);
    }

    public static FieldSpec number(String name) {
        return number(name, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static FieldSpec number(String name, double min, double max) {
        return new FieldSpec(name, FieldKind.NUMBER, min, max, List.of(), false);
    }

    /** A number in the range of {@code DensityFunctions.NOISE_VALUE_CODEC}. */
    public static FieldSpec noiseValue(String name) {
        return number(name, -NOISE_VALUE_LIMIT, NOISE_VALUE_LIMIT);
    }

    public static FieldSpec integer(String name, int min, int max) {
        return new FieldSpec(name, FieldKind.INTEGER, min, max, List.of(), false);
    }

    /** An int that vanilla reads with {@code Codec.INT}, so without a range. */
    public static FieldSpec integer(String name) {
        return new FieldSpec(name, FieldKind.INTEGER, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, List.of(), false);
    }

    public static FieldSpec noise(String name) {
        return new FieldSpec(name, FieldKind.NOISE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, List.of(), false);
    }

    public static FieldSpec choice(String name, List<String> choices) {
        return new FieldSpec(name, FieldKind.ENUM, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, choices, false);
    }

    public static FieldSpec spline(String name) {
        return new FieldSpec(name, FieldKind.SPLINE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, List.of(), true);
    }

    /** The id of an island variation list. */
    public static FieldSpec variationList(String name) {
        return new FieldSpec(name, FieldKind.VARIATION_LIST, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, List.of(), false);
    }

    /** True when vanilla checks a range on this field. */
    public boolean hasRange() {
        return this.min > Double.NEGATIVE_INFINITY || this.max < Double.POSITIVE_INFINITY;
    }

    public boolean inRange(double value) {
        return value >= this.min && value <= this.max;
    }

}
