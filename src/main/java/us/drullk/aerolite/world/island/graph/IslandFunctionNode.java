package us.drullk.aerolite.world.island.graph;

import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.LatticeSettings;

import java.util.Optional;

/**
 * Maps the lattice settings to and from the fields of an island function node, the type {@code aerolite:island_variations}.
 * The noise field and the variation list field stay as they are.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandFunctionNode {

    private IslandFunctionNode() {
    }

    public static boolean isIslandFunction(FunctionNode functionNode) {
        return functionNode instanceof FunctionNode.Typed typed && typed.type().id().equals(AeroliteWorldgen.ISLAND_VARIATIONS_TYPE);
    }

    /** The lattice settings of the function node. Empty for another type or a field without a value of its kind. */
    public static Optional<LatticeSettings> read(FunctionNode functionNode) {
        if (!isIslandFunction(functionNode)) {
            return Optional.empty();
        }
        FunctionNode.Typed typed = (FunctionNode.Typed) functionNode;
        if (!(typed.values().get("cell_size") instanceof FieldValue.Int cellSize)
                || !(typed.values().get("cell_height") instanceof FieldValue.Int cellHeight)
                || !(typed.values().get("layer_min_y") instanceof FieldValue.Int layerMinY)
                || !(typed.values().get("jitter") instanceof FieldValue.Number jitter)) {
            return Optional.empty();
        }
        return Optional.of(new LatticeSettings(cellSize.value(), cellHeight.value(), layerMinY.value(), jitter.value()));
    }

    /** The function node with the lattice settings in its fields. */
    public static FunctionNode.Typed write(FunctionNode.Typed functionNode, LatticeSettings settings) {
        if (!isIslandFunction(functionNode)) {
            throw new IllegalArgumentException("The function node is not of the type " + AeroliteWorldgen.ISLAND_VARIATIONS_TYPE);
        }
        return functionNode
                .with("cell_size", new FieldValue.Int(settings.cellSize()))
                .with("cell_height", new FieldValue.Int(settings.cellHeight()))
                .with("layer_min_y", new FieldValue.Int(settings.layerMinY()))
                .with("jitter", new FieldValue.Number(settings.jitter()));
    }

}
