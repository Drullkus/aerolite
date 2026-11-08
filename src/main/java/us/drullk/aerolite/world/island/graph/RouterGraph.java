package us.drullk.aerolite.world.island.graph;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A graph with one output per field of the vanilla {@code NoiseRouter}. It maps to the {@code noise_router} object of noise settings.
 * The path of the function node at an output is the router field name.
 * An output without a link holds a constant or the switch dimension default. A link hides that value until the link goes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class RouterGraph extends FunctionNodeGraph {

    /** The router fields in the sequence of {@code NoiseRouter.CODEC}. */
    public static final List<String> FIELDS = List.of("barrier", "fluid_level_floodedness", "fluid_level_spread", "lava", "temperature", "vegetation",
            "continents", "erosion", "depth", "ridges", "initial_density_without_jaggedness", "final_density", "vein_toggle", "vein_ridged", "vein_gap");

    /** The state of one router output. */
    public sealed interface Output {

        /** A function node feeds the output. */
        record Linked(int functionNode) implements Output {
        }
    }

    /** The value of an output without a link. */
    public sealed interface Unlinked extends Output {
    }

    /** A number. Its JSON is a bare number. */
    public record Constant(double value) implements Unlinked {
    }

    /** The router field of the shipped noise settings of the dimension, saved as the shipped router has it. */
    public record DimensionDefault() implements Unlinked {
    }

    private final Map<String, Unlinked> unlinked = new HashMap<>();

    /** A router graph whose outputs all hold the given value. */
    public RouterGraph(Unlinked initial) {
        super(FIELDS);
        for (String field : FIELDS) {
            this.unlinked.put(field, initial);
        }
    }

    /** A copy with the same function node ids and the same unlinked values. */
    public RouterGraph copy() {
        RouterGraph copy = new RouterGraph(new DimensionDefault());
        copy.copyFrom(this);
        copy.unlinked.putAll(this.unlinked);
        return copy;
    }

    public boolean link(int source, String field) {
        return this.linkOutput(source, field);
    }

    public Output output(String field) {
        Integer source = this.outputSource(field);
        return source != null ? new Output.Linked(source) : this.unlinkedValue(field);
    }

    /** The value of an output that shows while no link feeds it. */
    public Unlinked unlinkedValue(String field) {
        Unlinked value = this.unlinked.get(field);
        if (value == null) {
            throw new IllegalArgumentException("No router field " + field);
        }
        return value;
    }

    /** Gives the output a value and removes its link. */
    public void set(String field, Unlinked value) {
        this.unlinkedValue(field);
        this.unlinked.put(field, value);
        Integer source = this.outputSource(field);
        if (source != null) {
            this.unlink(source);
        }
    }

    @Nullable
    public Integer linked(String field) {
        return this.outputSource(field);
    }

}
