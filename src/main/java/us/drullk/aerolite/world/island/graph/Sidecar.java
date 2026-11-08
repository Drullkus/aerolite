package us.drullk.aerolite.world.island.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The canvas positions of the function nodes of one graph. The file sits next to the density function JSON, which stays pure vanilla.
 * Each key is the path of a function node. A function node without a position gets none from the sidecar.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public record Sidecar(Map<String, Position> positions) {

    public static final Codec<Sidecar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Position.CODEC).fieldOf("positions").forGetter(Sidecar::positions)
    ).apply(instance, Sidecar::new));

    public Sidecar {
        positions = Collections.unmodifiableMap(new LinkedHashMap<>(positions));
    }

    /** The sidecar of the given positions of function node ids. A function node outside the trees has no path, so it is not kept. */
    public static Sidecar of(FunctionNodeGraph graph, Map<Integer, Position> byFunctionNode) {
        Map<String, Position> positions = new LinkedHashMap<>();
        graph.paths().forEach((path, id) -> {
            Position position = byFunctionNode.get(id);
            if (position != null) {
                positions.put(path, position);
            }
        });
        return new Sidecar(positions);
    }

    /** The position of each function node of the graph whose path has one. */
    public Map<Integer, Position> apply(FunctionNodeGraph graph) {
        Map<Integer, Position> byFunctionNode = new HashMap<>();
        graph.paths().forEach((path, id) -> {
            Position position = this.positions.get(path);
            if (position != null) {
                byFunctionNode.put(id, position);
            }
        });
        return byFunctionNode;
    }

    /** One canvas position, in canvas units. */
    public record Position(float x, float y) {

        public static final Codec<Position> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("x").forGetter(Position::x),
                Codec.FLOAT.fieldOf("y").forGetter(Position::y)
        ).apply(instance, Position::new));
    }

}
